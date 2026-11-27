package com.culinary.plans

import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.database.dao.ProductionPlanDao
import com.culinary.core.database.dao.ProductionPlanItemDao
import com.culinary.core.database.dao.RecipeDao
import com.culinary.core.database.dao.RecipeVersionDao
import com.culinary.core.database.dao.RecipeComponentDao
import com.culinary.core.database.dao.IngredientDao
import com.culinary.core.database.dao.SauceDao
import com.culinary.core.database.dao.UnitDao
import com.culinary.core.database.dao.WorkPlanDao
import com.culinary.core.database.entity.ProductionPlanEntity
import com.culinary.core.database.entity.ProductionPlanItemEntity
import com.culinary.core.database.entity.WorkPlanEntity
import com.culinary.core.database.model.PlanStatusDb
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PlanKind { PRODUCTION, WORK }

fun planDetailRoute(plan: PlanUiItem): String = "home/plans/detail/${plan.id}"

fun planEditRoute(plan: PlanUiItem): String =
    "home/plans/edit/${plan.id}/${if (plan.kind == PlanKind.WORK) "work" else "production"}"

/**
 * 当前数据库已经分别保存 production_plan / work_plan。
 * 类型专属字段以版本化 metadata 编码在各自记录的 notes/description 中，
 * 不混表，也不需要破坏现有用户数据库的迁移。
 */
data class PlanMetadata(
    val target: String = "",
    val quantity: String = "",
    val unit: String = "",
    val category: String = "",
    val time: String = "",
    val priority: String = "普通",
    val instructions: String = "",
    val completionCriteria: String = "",
    val linkedArchive: String = "",
    val source: String = "手动创建",
    val note: String = "",
    val recipeId: String = "",
    val recipeVersionId: String = "",
    val unitId: String = "",
)

private const val PLAN_META_PREFIX = "MISEBASE_PLAN_V1:"
private fun encodeMetadata(meta: PlanMetadata): String {
    val raw = listOf(
        meta.target, meta.quantity, meta.unit, meta.category, meta.time, meta.priority,
        meta.instructions, meta.completionCriteria, meta.linkedArchive, meta.source, meta.note, meta.recipeId, meta.recipeVersionId, meta.unitId,
    ).joinToString("\u001f")
    return PLAN_META_PREFIX + Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
}
private fun decodeMetadata(value: String?): PlanMetadata {
    if (value.isNullOrBlank()) return PlanMetadata(note = value.orEmpty())
    if (!value.startsWith(PLAN_META_PREFIX)) return PlanMetadata(note = value)
    return runCatching {
        val raw = String(Base64.decode(value.removePrefix(PLAN_META_PREFIX), Base64.DEFAULT), Charsets.UTF_8)
        val p = raw.split("\u001f")
        PlanMetadata(
            target = p.getOrElse(0) { "" }, quantity = p.getOrElse(1) { "" },
            unit = p.getOrElse(2) { "" }, category = p.getOrElse(3) { "" },
            time = p.getOrElse(4) { "" }, priority = p.getOrElse(5) { "普通" },
            instructions = p.getOrElse(6) { "" }, completionCriteria = p.getOrElse(7) { "" },
            linkedArchive = p.getOrElse(8) { "" }, source = p.getOrElse(9) { "手动创建" },
            note = p.getOrElse(10) { "" },
            recipeId = p.getOrElse(11) { "" }, recipeVersionId = p.getOrElse(12) { "" },
            unitId = p.getOrElse(13) { "" },
        )
    }.getOrDefault(PlanMetadata(note = value))
}

data class PlanRecipeOption(
    val id: String,
    val versionId: String,
    val unitId: String,
    val name: String,
    val unitName: String,
    val unitSymbol: String,
    val defaultQuantity: Double,
)

data class PlanRequirement(val name: String, val quantity: Double, val unit: String)

data class PlanUiItem(
    val id: String,
    val kind: PlanKind,
    val title: String,
    val plannedDate: String?,
    val description: String?,
    val status: String,
    val completedAt: Long?,
    val metadata: PlanMetadata = PlanMetadata(),
) {
    val completed: Boolean get() = status == "COMPLETED"
    val inProgress: Boolean get() = status == "IN_PROGRESS"
}

@HiltViewModel
class PlanManagementViewModel @Inject constructor(
    private val productionDao: ProductionPlanDao,
    private val workDao: WorkPlanDao,
    private val productionItemDao: ProductionPlanItemDao,
    private val recipeDao: RecipeDao,
    private val recipeVersionDao: RecipeVersionDao,
    private val recipeComponentDao: RecipeComponentDao,
    private val ingredientDao: IngredientDao,
    private val sauceDao: SauceDao,
    private val unitDao: UnitDao,
) : ViewModel() {
    private val _recipeOptions = MutableStateFlow<List<PlanRecipeOption>>(emptyList())
    val recipeOptions = _recipeOptions
    private val _requirements = MutableStateFlow<List<PlanRequirement>>(emptyList())
    val requirements = _requirements

    init {
        viewModelScope.launch {
            _recipeOptions.value = recipeDao.listActive().mapNotNull { recipe ->
                val version = recipeVersionDao.findCurrent(recipe.id) ?: return@mapNotNull null
                val unit = unitDao.findById(version.yieldUnitId)
                PlanRecipeOption(
                    id = recipe.id, versionId = version.id, unitId = version.yieldUnitId,
                    name = recipe.name, unitName = unit?.name.orEmpty(), unitSymbol = unit?.symbol.orEmpty(),
                    defaultQuantity = version.yieldQty,
                )
            }
        }
    }

    fun loadRequirements(plan: PlanUiItem) {
        if (plan.kind != PlanKind.PRODUCTION) {
            _requirements.value = emptyList()
            return
        }
        viewModelScope.launch {
            val item = productionItemDao.listByPlan(plan.id).firstOrNull()
            val version = item?.let { recipeVersionDao.findById(it.recipeVersionId) }
            if (item == null || version == null) {
                _requirements.value = emptyList()
                return@launch
            }
            val scale = if (version.yieldQty > 0.0) item.quantity / version.yieldQty else 1.0
            _requirements.value = recipeComponentDao.listByVersion(version.id).map { component ->
                val name = when {
                    component.ingredientId != null -> ingredientDao.findById(component.ingredientId)?.name
                    component.referencedSauceId != null -> sauceDao.findById(component.referencedSauceId)?.name
                    component.referencedRecipeId != null -> recipeDao.findById(component.referencedRecipeId)?.name
                    else -> null
                } ?: "未命名组成项"
                val unit = unitDao.findById(component.unitId)?.symbol.orEmpty()
                PlanRequirement(name, component.quantity * scale, unit)
            }
        }
    }
    val plans = combine(productionDao.observeAll(), workDao.observeAll()) { production, work ->
        (production.map { it.toUi() } + work.map { it.toUi() })
            .sortedWith(compareBy<PlanUiItem> { it.plannedDate ?: "9999-99-99" }.thenByDescending { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message

    fun save(kind: PlanKind, id: String?, title: String, date: String?, metadata: PlanMetadata) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            _message.value = "请填写计划名称"
            return
        }
        if (date.isNullOrBlank()) {
            _message.value = "请选择计划日期"
            return
        }
        if (kind == PlanKind.PRODUCTION && (metadata.recipeId.isBlank() || metadata.recipeVersionId.isBlank())) {
            _message.value = "请选择有效的制作档案"
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val cleanDate = date.trim()
            val encoded = encodeMetadata(metadata)
            val planId = id ?: UUID.randomUUID().toString()
            when (kind) {
                PlanKind.PRODUCTION -> {
                    if (id == null) {
                        productionDao.insert(ProductionPlanEntity(id = planId, title = cleanTitle, plannedDate = cleanDate, notes = encoded, createdAt = now, updatedAt = now))
                    } else {
                        productionDao.updateOpen(planId, cleanTitle, cleanDate, encoded, now)
                    }
                    productionItemDao.deleteByPlan(planId)
                    val version = metadata.recipeVersionId.takeIf { it.isNotBlank() }?.let { recipeVersionDao.findById(it) }
                    if (version != null && metadata.recipeId.isNotBlank()) {
                        productionItemDao.insert(
                            ProductionPlanItemEntity(
                                id = UUID.randomUUID().toString(), planId = planId, recipeId = metadata.recipeId,
                                recipeVersionId = version.id, quantity = metadata.quantity.toDoubleOrNull() ?: version.yieldQty,
                                unitId = metadata.unitId.ifBlank { version.yieldUnitId }, createdAt = now, updatedAt = now,
                            ),
                        )
                    }
                }
                PlanKind.WORK -> if (id == null) {
                    workDao.insert(WorkPlanEntity(id = planId, title = cleanTitle, description = encoded, plannedDate = cleanDate, createdAt = now, updatedAt = now))
                } else {
                    workDao.updateOpen(planId, cleanTitle, encoded, cleanDate, now)
                }
            }
            _message.value = null
        }
    }

    fun start(plan: PlanUiItem) {
        if (plan.completed || plan.inProgress) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            when (plan.kind) {
                PlanKind.PRODUCTION -> productionDao.updateStatus(plan.id, PlanStatusDb.IN_PROGRESS, null, now)
                PlanKind.WORK -> workDao.start(plan.id, now)
            }
        }
    }

    fun complete(plan: PlanUiItem) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            when (plan.kind) {
                PlanKind.PRODUCTION -> productionDao.complete(plan.id, now)
                PlanKind.WORK -> workDao.complete(plan.id, now)
            }
        }
    }

    fun deleteUnfinished(plan: PlanUiItem) {
        if (plan.completed) {
            _message.value = "已完成的计划请在计划回收站中处理"
            return
        }
        viewModelScope.launch {
            when (plan.kind) {
                PlanKind.PRODUCTION -> productionDao.deleteOpen(plan.id)
                PlanKind.WORK -> workDao.deleteOpen(plan.id)
            }
        }
    }

    fun restore(plan: PlanUiItem) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            when (plan.kind) {
                PlanKind.PRODUCTION -> productionDao.restoreCompleted(plan.id, now)
                PlanKind.WORK -> workDao.restore(plan.id, now)
            }
        }
    }

    fun permanentlyDeleteAfterPassword(plan: PlanUiItem) {
        if (!plan.completed) return
        viewModelScope.launch {
            when (plan.kind) {
                PlanKind.PRODUCTION -> productionDao.permanentlyDeleteCompleted(plan.id)
                PlanKind.WORK -> workDao.permanentlyDeleteCompleted(plan.id)
            }
        }
    }

    private fun ProductionPlanEntity.toUi(): PlanUiItem {
        val meta = decodeMetadata(notes)
        return PlanUiItem(id, PlanKind.PRODUCTION, title, plannedDate, meta.note, status.name, completedAt, meta)
    }
    private fun WorkPlanEntity.toUi(): PlanUiItem {
        val meta = decodeMetadata(description)
        return PlanUiItem(id, PlanKind.WORK, title, plannedDate, meta.instructions, status, completedAt, meta)
    }
}

@Composable
fun AllPlansRoute(
    onAdd: () -> Unit,
    onOpen: (PlanUiItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanManagementViewModel = hiltViewModel(),
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val dateKey = selectedDate.toString()
    val dayPlans = plans.filter { it.plannedDate == dateKey || (it.plannedDate.isNullOrBlank() && selectedDate == LocalDate.now()) }
    val completed = dayPlans.count { it.completed }
    val production = dayPlans.filter { it.kind == PlanKind.PRODUCTION }
    val work = dayPlans.filter { it.kind == PlanKind.WORK }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("TODAY'S PLAN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(18.dp))
        Text("今日计划", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 · E", java.util.Locale.CHINA)), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                Text("‹", modifier = Modifier.clickable { selectedDate = selectedDate.minusDays(1) }.padding(horizontal = 10.dp), style = MaterialTheme.typography.titleLarge)
                Text("›", modifier = Modifier.clickable { selectedDate = selectedDate.plusDays(1) }.padding(horizontal = 10.dp), style = MaterialTheme.typography.titleLarge)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text("完成进度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("%02d / %02d".format(completed, dayPlans.size), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            Text(if (dayPlans.isEmpty()) "0%" else "${completed * 100 / dayPlans.size}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        PlanProgress(completed, dayPlans.size)
        PlanListGroup("生产计划", production, onOpen)
        PlanListGroup("工作计划", work, onOpen)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("+ 添加计划", modifier = Modifier.clickable(onClick = onAdd).padding(vertical = 8.dp), color = MaterialTheme.colorScheme.secondary)
            Text("计划管理", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanProgress(done: Int, total: Int) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().height(3.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (total > 0 && done > 0) androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth(done.toFloat() / total).height(3.dp).background(Color(0xFF8A6A2B)),
        )
    }
}

@Composable
private fun PlanListGroup(title: String, items: List<PlanUiItem>, onOpen: (PlanUiItem) -> Unit) {
    Column {
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${items.size} 项", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (items.isEmpty()) {
            Text("暂无计划", Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else items.forEach { plan ->
            Column(
                Modifier.fillMaxWidth().clickable { onOpen(plan) }.padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(plan.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(plan.quantityLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(plan.statusLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
fun AddPlanRoute(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    planId: String? = null,
    kindArg: String? = null,
    viewModel: PlanManagementViewModel = hiltViewModel(),
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val existing = plans.firstOrNull { it.id == planId }
    var kind by remember(planId, kindArg) { mutableStateOf(existing?.kind ?: if (kindArg == "work") PlanKind.WORK else PlanKind.PRODUCTION) }
    var step by remember(planId) { mutableStateOf(if (planId == null) 0 else 1) }
    var title by remember(planId) { mutableStateOf(existing?.title.orEmpty()) }
    var date by remember(planId) { mutableStateOf(existing?.plannedDate ?: LocalDate.now().toString()) }
    val recipeOptions by viewModel.recipeOptions.collectAsStateWithLifecycle()
    var recipeId by remember(planId) { mutableStateOf(existing?.metadata?.recipeId.orEmpty()) }
    var recipeVersionId by remember(planId) { mutableStateOf(existing?.metadata?.recipeVersionId.orEmpty()) }
    var unitId by remember(planId) { mutableStateOf(existing?.metadata?.unitId.orEmpty()) }
    var target by remember(planId) { mutableStateOf(existing?.metadata?.target.orEmpty()) }
    var quantity by remember(planId) { mutableStateOf(existing?.metadata?.quantity.orEmpty()) }
    var unit by remember(planId) { mutableStateOf(existing?.metadata?.unit.orEmpty()) }
    var category by remember(planId) { mutableStateOf(existing?.metadata?.category ?: "其他") }
    var time by remember(planId) { mutableStateOf(existing?.metadata?.time ?: "不指定") }
    var priority by remember(planId) { mutableStateOf(existing?.metadata?.priority ?: "普通") }
    var instructions by remember(planId) { mutableStateOf(existing?.metadata?.instructions ?: existing?.description.orEmpty()) }
    var criteria by remember(planId) { mutableStateOf(existing?.metadata?.completionCriteria.orEmpty()) }
    var linkedArchive by remember(planId) { mutableStateOf(existing?.metadata?.linkedArchive.orEmpty()) }
    var note by remember(planId) { mutableStateOf(existing?.metadata?.note.orEmpty()) }

    LaunchedEffect(existing?.id) {
        if (existing != null) {
            title = existing.title; date = existing.plannedDate ?: LocalDate.now().toString()
            target = existing.metadata.target; quantity = existing.metadata.quantity; unit = existing.metadata.unit
            recipeId = existing.metadata.recipeId; recipeVersionId = existing.metadata.recipeVersionId; unitId = existing.metadata.unitId
            category = existing.metadata.category.ifBlank { "其他" }; time = existing.metadata.time.ifBlank { "不指定" }
            priority = existing.metadata.priority; instructions = existing.metadata.instructions
            criteria = existing.metadata.completionCriteria; linkedArchive = existing.metadata.linkedArchive; note = existing.metadata.note
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(if (step == 0) "NEW PLAN" else if (kind == PlanKind.WORK) "WORK PLAN" else "PRODUCTION PLAN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(18.dp))
        Text(if (planId == null) "添加计划" else "编辑计划", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        if (step == 0) {
            Spacer(Modifier.height(10.dp))
            Text("选择计划类型", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            PlanTypeChoice("生产计划", "制作菜品、酱汁、汤底或预制件", "关联制作档案，按数量计算原料需求", kind == PlanKind.PRODUCTION) { kind = PlanKind.PRODUCTION }
            Spacer(Modifier.height(12.dp))
            PlanTypeChoice("工作计划", "盘点、清洁、检查、整理等日常任务", "不产生原料采购需求", kind == PlanKind.WORK) { kind = PlanKind.WORK }
            HorizontalDivider(Modifier.padding(vertical = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
            PlanSection("计划日期", "执行日期") {
                PlanField("执行日期（YYYY-MM-DD）", date) { date = it }
            }
            Text("计划来源", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Text("手动创建 / 系统建议", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(22.dp))
            PlanPrimaryAction("继续") { step = 1 }
        } else {
            Spacer(Modifier.height(8.dp))
            if (planId == null) Text("‹ 修改计划类型", Modifier.clickable { step = 0 }.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(if (kind == PlanKind.PRODUCTION) "PRODUCTION TASK · 生产任务" else "WORK TASK · 日常工作任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(vertical = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
            if (kind == PlanKind.PRODUCTION) {
                PlanSection("01  制作计划内容", "选择制作对象") {
                    PlanField("计划名称", title) { title = it }
                    Text("选择制作档案", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    if (recipeOptions.isEmpty()) {
                        Text("当前没有可用的菜谱档案或已发布版本。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    } else recipeOptions.forEach { option ->
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                recipeId = option.id
                                recipeVersionId = option.versionId
                                unitId = option.unitId
                                target = option.name
                                quantity = option.defaultQuantity.toString()
                                unit = option.unitSymbol
                            }.padding(vertical = 9.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(option.name, fontWeight = if (recipeId == option.id) FontWeight.SemiBold else FontWeight.Normal)
                                Text(if (recipeId == option.id) "已选择" else "选择 →", color = if (recipeId == option.id) Color(0xFF8A6A2B) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("当前版本产出：${option.defaultQuantity} ${option.unitName}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    if (target.isNotBlank()) Text("已关联：$target", color = Color(0xFF8A6A2B), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) { PlanField("制作数量", quantity) { quantity = it } }
                        Column(Modifier.weight(1f)) { PlanField("计量单位", unit) { unit = it } }
                    }
                }
                PlanSection("02  执行安排", "执行日期与时间") {
                    PlanField("执行日期（YYYY-MM-DD）", date) { date = it }
                    PlanField("执行时间（如 09:00）", time) { time = it }
                    PlanField("备注（可选）", note) { note = it }
                }
                PlanSection("03  原料需求预览", "根据关联制作档案和数量计算") {
                    Text(if (target.isBlank()) "选择制作对象后显示配方原料需求" else "已记录制作对象：$target。原料需求需从对应档案的配方版本读取。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                PlanSection("01  任务信息", "工作内容") {
                    PlanField("任务名称", title) { title = it }
                    Text("任务分类", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    ChoiceRow(listOf("库存检查", "设备检查", "清洁整理", "食材处理", "其他"), category) { category = it }
                }
                PlanSection("02  执行安排", "日期、时间与优先级") {
                    PlanField("执行日期（YYYY-MM-DD）", date) { date = it }
                    Text("执行时间", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    ChoiceRow(listOf("不指定", "09:00", "10:00", "11:00", "14:00", "15:00", "16:00"), time) { time = it }
                    Text("优先级", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    ChoiceRow(listOf("低", "普通", "高", "紧急"), priority) { priority = it }
                }
                PlanSection("03  执行要求", "任务说明与完成标准") {
                    PlanField("任务说明", instructions, minLines = 3) { instructions = it }
                    PlanField("完成标准", criteria, minLines = 2) { criteria = it }
                }
                PlanSection("04  其他信息", "可选关联") {
                    PlanField("关联库存档案", linkedArchive) { linkedArchive = it }
                    PlanField("备注", note) { note = it }
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(20.dp))
            PlanPrimaryAction(if (kind == PlanKind.PRODUCTION) "保存制作计划" else "保存工作计划") {
                val meta = PlanMetadata(
                    target = target, quantity = quantity, unit = unit, category = category, time = time,
                    priority = priority, instructions = instructions, completionCriteria = criteria,
                    linkedArchive = linkedArchive, note = note, recipeId = recipeId, recipeVersionId = recipeVersionId, unitId = unitId,
                )
                viewModel.save(kind, planId, title, date, meta)
                if (title.isNotBlank() && date.isNotBlank() && (kind != PlanKind.PRODUCTION || recipeId.isNotBlank())) onSaved()
            }
        }
    }
}

@Composable
private fun PlanTypeChoice(title: String, subtitle: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .then(if (selected) Modifier else Modifier),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().border(
                BorderStroke(1.dp, if (selected) Color(0xFF8A6A2B) else MaterialTheme.colorScheme.outlineVariant),
            ).padding(15.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PlanSection(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun PlanField(label: String, value: String, minLines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) }, minLines = minLines, singleLine = minLines == 1,
    )
}

@Composable
private fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            Text(
                option,
                Modifier.clickable { onSelect(option) }.padding(horizontal = 7.dp, vertical = 9.dp),
                color = if (option == selected) Color(0xFF8A6A2B) else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun PlanPrimaryAction(text: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().border(BorderStroke(1.dp, Color(0xFF8A6A2B)))
            .clickable(onClick = onClick).padding(vertical = 16.dp),
    ) {
        Text(text, Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PlanDetailRoute(
    planId: String,
    onBack: () -> Unit,
    onEdit: (PlanUiItem) -> Unit,
    onDeleted: () -> Unit,
    viewModel: PlanManagementViewModel = hiltViewModel(),
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val plan = plans.firstOrNull { it.id == planId }
    val requirements by viewModel.requirements.collectAsStateWithLifecycle()
    LaunchedEffect(plan?.id) { plan?.let(viewModel::loadRequirements) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("计划详情", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(22.dp))
        if (plan == null) {
            Text("计划不存在或已删除")
        } else {
            Text(plan.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Text(if (plan.kind == PlanKind.PRODUCTION) "PRODUCTION TASK · 生产任务" else "WORK TASK · 日常工作任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(vertical = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
            PlanInfoRow("计划日期", plan.plannedDate ?: "未指定")
            PlanInfoRow("计划状态", plan.statusLabel())
            if (plan.kind == PlanKind.PRODUCTION) {
                PlanInfoRow("制作数量", plan.quantityLabel().ifBlank { "未填写" })
                PlanSection("制作内容", "关联制作对象") {
                    PlanInfoRow("制作对象", plan.metadata.target.ifBlank { "未关联" })
                    PlanInfoRow("配方版本", if (plan.metadata.recipeVersionId.isNotBlank()) "已固定版本" else "未关联")
                }
                PlanSection("原料需求", "按关联配方版本及制作数量自动折算") {
                    if (requirements.isEmpty()) {
                        Text("该制作档案当前版本没有组成明细。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    } else requirements.forEach { requirement ->
                        PlanInfoRow(requirement.name, "${"%.2f".format(requirement.quantity)} ${requirement.unit}")
                    }
                }
            } else {
                PlanSection("任务信息", "工作分类") {
                    PlanInfoRow("任务分类", plan.metadata.category.ifBlank { "其他" })
                    PlanInfoRow("优先级", plan.metadata.priority)
                }
                PlanSection("执行要求", "任务说明与完成标准") {
                    PlanInfoRow("任务说明", plan.metadata.instructions.ifBlank { "暂无" })
                    PlanInfoRow("完成标准", plan.metadata.completionCriteria.ifBlank { "未设置" })
                }
                if (plan.metadata.linkedArchive.isNotBlank()) PlanInfoRow("关联库存档案", plan.metadata.linkedArchive)
            }
            PlanSection("执行记录", "当前执行状态") {
                PlanInfoRow("执行进度", when { plan.completed -> "已完成"; plan.inProgress -> "进行中"; else -> "未开始" })
                if (plan.metadata.time.isNotBlank()) PlanInfoRow("计划时间", plan.metadata.time)
                PlanInfoRow("备注", plan.metadata.note.ifBlank { "暂无" })
            }
            HorizontalDivider(Modifier.padding(vertical = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
            if (!plan.completed) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("编辑计划", Modifier.clickable { onEdit(plan) }.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("删除计划", Modifier.clickable { viewModel.deleteUnfinished(plan); onDeleted() }.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                PlanPrimaryAction(if (plan.inProgress) "完成计划" else "开始执行") {
                    if (plan.inProgress) viewModel.complete(plan) else viewModel.start(plan)
                }
            } else {
                Text("已完成的计划已永久保留在计划回收站中。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PlanInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 14.dp))
    }
}

private fun PlanUiItem.quantityLabel(): String = listOf(metadata.quantity, metadata.unit).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "" }
private fun PlanUiItem.statusLabel(): String = when (status) {
    "COMPLETED" -> "已完成"
    "IN_PROGRESS" -> "进行中"
    "CONFIRMED" -> "已确认"
    "CANCELLED" -> "已取消"
    else -> "待执行"
}
