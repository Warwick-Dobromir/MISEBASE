package com.culinary.plans

import android.app.DatePickerDialog
import android.util.Base64
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.database.dao.IngredientDao
import com.culinary.core.database.dao.ProductionPlanDao
import com.culinary.core.database.dao.ProductionPlanItemDao
import com.culinary.core.database.dao.RecipeComponentDao
import com.culinary.core.database.dao.RecipeDao
import com.culinary.core.database.dao.RecipeVersionDao
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
            if (item == null) {
                _requirements.value = emptyList()
            } else {
                calculateRequirements(item.recipeVersionId, item.quantity)
            }
        }
    }

    fun previewRequirements(versionId: String, quantity: Double?) {
        viewModelScope.launch {
            val version = recipeVersionDao.findById(versionId)
            if (version == null) {
                _requirements.value = emptyList()
            } else {
                calculateRequirements(versionId, quantity ?: version.yieldQty)
            }
        }
    }

    private suspend fun calculateRequirements(versionId: String, quantity: Double) {
        val version = recipeVersionDao.findById(versionId)
        if (version == null) {
            _requirements.value = emptyList()
            return
        }
        val scale = if (version.yieldQty > 0.0) quantity / version.yieldQty else 1.0
        _requirements.value = recipeComponentDao.listByVersion(version.id).map { component ->
            val ingredientId = component.ingredientId
            val referencedSauceId = component.referencedSauceId
            val referencedRecipeId = component.referencedRecipeId
            val name = when {
                ingredientId != null -> ingredientDao.findById(ingredientId)?.name
                referencedSauceId != null -> sauceDao.findById(referencedSauceId)?.name
                referencedRecipeId != null -> recipeDao.findById(referencedRecipeId)?.name
                else -> null
            } ?: "未命名组成项"
            val unit = unitDao.findById(component.unitId)?.symbol.orEmpty()
            PlanRequirement(name, component.quantity * scale, unit)
        }
    }
    val plans = combine(productionDao.observeAll(), workDao.observeAll()) { production, work ->
        (production.map { it.toUi() } + work.map { it.toUi() })
            .sortedWith(compareBy<PlanUiItem> { it.plannedDate ?: "9999-99-99" }.thenByDescending { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message

    fun save(kind: PlanKind, id: String?, title: String, date: String?, metadata: PlanMetadata, onSaved: () -> Unit = {}) {
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
            onSaved()
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
fun AllPlansRoute(onAdd: () -> Unit, onOpen: (PlanUiItem) -> Unit, modifier: Modifier = Modifier, viewModel: PlanManagementViewModel = hiltViewModel()) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val dayPlans = plans.filter { it.plannedDate == selectedDate.toString() || (it.plannedDate.isNullOrBlank() && selectedDate == LocalDate.now()) }
    val production = dayPlans.filter { it.kind == PlanKind.PRODUCTION }
    val work = dayPlans.filter { it.kind == PlanKind.WORK }
    val completed = dayPlans.count { it.completed }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp)) {
        PlanEyebrow("PLAN MANAGEMENT")
        Spacer(Modifier.height(12.dp))
        Text("全部计划", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 · E", java.util.Locale.CHINA)), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text("‹", Modifier.clickable { selectedDate = selectedDate.minusDays(1) }.padding(horizontal = 12.dp), style = MaterialTheme.typography.headlineSmall)
            Text("›", Modifier.clickable { selectedDate = selectedDate.plusDays(1) }.padding(start = 12.dp), style = MaterialTheme.typography.headlineSmall)
        }
        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("当日完成进度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                    Text("%02d".format(completed), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Medium)
                    Text(" / %02d 项".format(dayPlans.size), Modifier.padding(start = 7.dp, bottom = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(if (dayPlans.isEmpty()) "—" else (completed * 100 / dayPlans.size).toString() + "%", color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(10.dp))
        PlanProgress(completed, dayPlans.size)
        PlanListGroup("制作计划", "PRODUCTION", production, onOpen)
        PlanListGroup("工作计划", "WORK", work, onOpen)
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("＋ 添加计划", Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(vertical = 16.dp), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PlanProgress(done: Int, total: Int) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (total > 0 && done > 0) androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth((done.toFloat() / total).coerceIn(0f, 1f)).height(3.dp).background(Color(0xFF8A6A2B)))
    }
}

@Composable
private fun PlanListGroup(title: String, eyebrow: String, items: List<PlanUiItem>, onOpen: (PlanUiItem) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(items.size.toString() + " 项", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (items.isEmpty()) Text("当天没有" + title, Modifier.padding(vertical = 15.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        else items.forEach { plan ->
            Column(Modifier.fillMaxWidth().clickable { onOpen(plan) }.padding(vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(plan.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(plan.statusLabel(), style = MaterialTheme.typography.labelSmall, color = if (plan.completed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val info = if (plan.kind == PlanKind.PRODUCTION) listOf(plan.quantityLabel(), plan.metadata.time).filter { it.isNotBlank() }.joinToString(" · ") else listOf(plan.metadata.category, plan.metadata.time).filter { it.isNotBlank() && it != "不指定" }.joinToString(" · ")
                if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
fun AddPlanRoute(onSaved: () -> Unit, onBack: () -> Unit, planId: String? = null, kindArg: String? = null, viewModel: PlanManagementViewModel = hiltViewModel()) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val existing = plans.firstOrNull { it.id == planId }
    var kind by remember(planId, kindArg) { mutableStateOf(existing?.kind ?: if (kindArg == "work") PlanKind.WORK else PlanKind.PRODUCTION) }
    var step by remember(planId) { mutableStateOf(if (planId == null) 0 else 1) }
    var title by remember(planId) { mutableStateOf(existing?.title.orEmpty()) }
    var date by remember(planId) { mutableStateOf(existing?.plannedDate ?: LocalDate.now().toString()) }
    val recipes by viewModel.recipeOptions.collectAsStateWithLifecycle()
    val requirements by viewModel.requirements.collectAsStateWithLifecycle()
    var recipeId by remember(planId) { mutableStateOf(existing?.metadata?.recipeId.orEmpty()) }
    var versionId by remember(planId) { mutableStateOf(existing?.metadata?.recipeVersionId.orEmpty()) }
    var unitId by remember(planId) { mutableStateOf(existing?.metadata?.unitId.orEmpty()) }
    var target by remember(planId) { mutableStateOf(existing?.metadata?.target.orEmpty()) }
    var quantity by remember(planId) { mutableStateOf(existing?.metadata?.quantity.orEmpty()) }
    var unit by remember(planId) { mutableStateOf(existing?.metadata?.unit.orEmpty()) }
    var category by remember(planId) { mutableStateOf(existing?.metadata?.category ?: "其他") }
    var time by remember(planId) { mutableStateOf(existing?.metadata?.time ?: "不指定") }
    var priority by remember(planId) { mutableStateOf(existing?.metadata?.priority ?: "普通") }
    var instructions by remember(planId) { mutableStateOf(existing?.metadata?.instructions.orEmpty()) }
    var criteria by remember(planId) { mutableStateOf(existing?.metadata?.completionCriteria.orEmpty()) }
    var linkedArchive by remember(planId) { mutableStateOf(existing?.metadata?.linkedArchive.orEmpty()) }
    var note by remember(planId) { mutableStateOf(existing?.metadata?.note.orEmpty()) }
    LaunchedEffect(versionId, quantity) { if (versionId.isNotBlank()) viewModel.previewRequirements(versionId, quantity.toDoubleOrNull()) }
    LaunchedEffect(existing?.id) {
        if (existing != null) {
            title = existing.title
            date = existing.plannedDate ?: LocalDate.now().toString()
            recipeId = existing.metadata.recipeId
            versionId = existing.metadata.recipeVersionId
            unitId = existing.metadata.unitId
            target = existing.metadata.target
            quantity = existing.metadata.quantity
            unit = existing.metadata.unit
            category = existing.metadata.category.ifBlank { "其他" }
            time = existing.metadata.time.ifBlank { "不指定" }
            priority = existing.metadata.priority
            instructions = existing.metadata.instructions
            criteria = existing.metadata.completionCriteria
            linkedArchive = existing.metadata.linkedArchive
            note = existing.metadata.note
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp)) {
        PlanEyebrow(if (planId == null) "CREATE PLAN" else "EDIT PLAN")
        Spacer(Modifier.height(12.dp))
        Text(if (planId == null) "添加计划" else "编辑计划", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        if (step == 0) {
            Spacer(Modifier.height(10.dp))
            Text("选择计划类型", color = MaterialTheme.colorScheme.onSurfaceVariant)
            PlanTypeChoice("制作计划", "PRODUCTION PLAN", "安排菜品、酱汁、汤底及预制件制作，关联制作档案并计算原料需求。", kind == PlanKind.PRODUCTION) { kind = PlanKind.PRODUCTION }
            PlanTypeChoice("工作计划", "WORK PLAN", "安排检查、清洁、整理、盘点等日常工作，不产生制作原料需求。", kind == PlanKind.WORK) { kind = PlanKind.WORK }
            PlanSection("计划日期", "选择任务执行日期") { PlanDateField(date) { date = it } }
            PlanSection("计划来源", "建立方式") { Text("手动创建", style = MaterialTheme.typography.bodyMedium) }
            PlanTextAction("继续", true) { step = 1 }
        } else {
            if (planId == null) Text("‹ 修改计划类型", Modifier.clickable { step = 0 }.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
            Text(if (kind == PlanKind.PRODUCTION) "PRODUCTION · 制作安排" else "WORK · 工作安排", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(vertical = 15.dp), color = MaterialTheme.colorScheme.outlineVariant)
            if (kind == PlanKind.PRODUCTION) {
                PlanSection("01  制作内容", "关联现有制作档案") {
                    PlanField("计划名称", title) { title = it }
                    Text("选择制作档案", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (recipes.isEmpty()) Text("当前没有可用的菜谱档案或已发布版本。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    recipes.forEach { option ->
                        Column(Modifier.fillMaxWidth().clickable {
                            recipeId = option.id; versionId = option.versionId; unitId = option.unitId
                            target = option.name; quantity = option.defaultQuantity.toString(); unit = option.unitSymbol
                        }.padding(vertical = 11.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(option.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (recipeId == option.id) FontWeight.SemiBold else FontWeight.Normal)
                                    Text("当前版本产出：" + option.defaultQuantity + " " + option.unitName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(if (recipeId == option.id) "已选择 ✓" else "选择", color = if (recipeId == option.id) Color(0xFF8A6A2B) else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (target.isNotBlank()) Text("关联档案：" + target, color = Color(0xFF8A6A2B), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f)) { PlanField("制作数量", quantity, KeyboardType.Decimal) { quantity = it } }
                        Column(Modifier.weight(1f)) { PlanField("计量单位", unit) { unit = it } }
                    }
                }
                PlanSection("02  执行安排", "日期、时间与补充说明") {
                    PlanDateField(date) { date = it }
                    PlanField("执行时间", time) { time = it }
                    PlanField("备注", note) { note = it }
                }
                PlanSection("03  原料需求", "根据固定配方版本及制作数量自动折算") {
                    if (recipeId.isBlank()) Text("选择制作档案后显示原料需求。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    else if (requirements.isEmpty()) Text("当前配方版本没有组成明细。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    else requirements.forEach { PlanInfoRow(it.name, "%.2f".format(it.quantity) + " " + it.unit) }
                }
            } else {
                PlanSection("01  工作内容", "定义需要完成的任务") {
                    PlanField("任务名称", title) { title = it }
                    Text("工作分类", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChoiceRow(listOf("库存检查", "设备检查", "清洁整理", "食材处理", "采购准备", "其他"), category) { category = it }
                }
                PlanSection("02  执行安排", "日期、时间与优先级") {
                    PlanDateField(date) { date = it }
                    Text("执行时间", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChoiceRow(listOf("不指定", "09:00", "10:00", "11:00", "14:00", "15:00", "16:00", "17:00"), time) { time = it }
                    Text("优先级", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChoiceRow(listOf("低", "普通", "高", "紧急"), priority) { priority = it }
                }
                PlanSection("03  执行要求", "工作说明与完成标准") {
                    PlanField("工作说明", instructions, minLines = 3) { instructions = it }
                    PlanField("完成标准", criteria, minLines = 2) { criteria = it }
                }
                PlanSection("04  关联信息", "可选") {
                    PlanField("关联库存档案", linkedArchive) { linkedArchive = it }
                    PlanField("备注", note) { note = it }
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            PlanTextAction(if (kind == PlanKind.PRODUCTION) "保存制作计划" else "保存工作计划", title.isNotBlank() && date.isNotBlank() && (kind != PlanKind.PRODUCTION || recipeId.isNotBlank())) {
                val metadata = PlanMetadata(target, quantity, unit, category, time, priority, instructions, criteria, linkedArchive, note = note, recipeId = recipeId, recipeVersionId = versionId, unitId = unitId)
                viewModel.save(kind, planId, title, date, metadata, onSaved)
            }
        }
    }
}

@Composable
private fun PlanTypeChoice(title: String, eyebrow: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (selected) "●" else "○", color = if (selected) Color(0xFF8A6A2B) else MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.height(7.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(top = 13.dp), color = if (selected) Color(0xFF8A6A2B) else MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun PlanSection(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 21.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun PlanField(label: String, value: String, keyboardType: KeyboardType = KeyboardType.Text, minLines: Int = 1, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(7.dp))
        BasicTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), minLines = minLines, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), decorationBox = { inner ->
            if (value.isEmpty()) Text("请输入", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
            inner()
        })
        HorizontalDivider(Modifier.padding(top = 9.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun PlanDateField(value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val date = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
    Column(Modifier.fillMaxWidth().clickable {
        DatePickerDialog(context, { _, year, month, day -> onChange(LocalDate.of(year, month + 1, day).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }.padding(vertical = 5.dp)) {
        Text("计划日期", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 · E", java.util.Locale.CHINA)) }.getOrDefault("选择日期"), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("选择日期  ›", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        options.forEach { option ->
            Column(Modifier.clickable { onSelect(option) }.padding(vertical = 8.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(option, color = if (option == selected) Color(0xFF8A6A2B) else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal)
                if (option == selected) HorizontalDivider(Modifier.padding(top = 4.dp), color = Color(0xFF8A6A2B), thickness = 2.dp)
            }
        }
    }
}

@Composable
private fun PlanTextAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 13.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        HorizontalDivider(color = if (enabled) Color(0xFF8A6A2B) else MaterialTheme.colorScheme.outlineVariant)
        Text(text, Modifier.padding(top = 12.dp), color = if (enabled) Color(0xFF8A6A2B) else MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PlanEyebrow(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
}

@Composable
fun PlanDetailRoute(planId: String, onBack: () -> Unit, onEdit: (PlanUiItem) -> Unit, onDeleted: () -> Unit, viewModel: PlanManagementViewModel = hiltViewModel()) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val plan = plans.firstOrNull { it.id == planId }
    val requirements by viewModel.requirements.collectAsStateWithLifecycle()
    LaunchedEffect(plan?.id) { plan?.let(viewModel::loadRequirements) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp)) {
        PlanEyebrow(if (plan?.kind == PlanKind.PRODUCTION) "PRODUCTION DETAIL" else "WORK DETAIL")
        Spacer(Modifier.height(12.dp))
        if (plan == null) Text("计划不存在或已删除", style = MaterialTheme.typography.bodyLarge)
        else {
            Text(plan.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(plan.plannedDate ?: "未指定日期", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(plan.statusLabel(), color = if (plan.completed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
            }
            HorizontalDivider(Modifier.padding(vertical = 17.dp), color = MaterialTheme.colorScheme.outlineVariant)
            if (plan.kind == PlanKind.PRODUCTION) {
                PlanSection("制作内容", "PRODUCTION ITEM") {
                    PlanInfoRow("制作对象", plan.metadata.target.ifBlank { "未关联" })
                    PlanInfoRow("制作数量", plan.quantityLabel().ifBlank { "未填写" })
                    PlanInfoRow("配方版本", if (plan.metadata.recipeVersionId.isNotBlank()) "已固定版本" else "未关联")
                }
                PlanSection("原料需求", "按制作数量折算的需求明细") {
                    if (requirements.isEmpty()) Text("该制作档案当前版本没有组成明细。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    else requirements.forEach { PlanInfoRow(it.name, "%.2f".format(it.quantity) + " " + it.unit) }
                }
                PlanSection("执行安排", "日期、时间与备注") {
                    PlanInfoRow("计划日期", plan.plannedDate ?: "未指定")
                    PlanInfoRow("计划时间", plan.metadata.time.ifBlank { "未指定" })
                    PlanInfoRow("计划来源", plan.metadata.source)
                    PlanInfoRow("备注", plan.metadata.note.ifBlank { "暂无" })
                }
            } else {
                PlanSection("工作内容", "WORK ITEM") {
                    PlanInfoRow("工作分类", plan.metadata.category.ifBlank { "其他" })
                    PlanInfoRow("优先级", plan.metadata.priority)
                    PlanInfoRow("工作说明", plan.metadata.instructions.ifBlank { "暂无" })
                    PlanInfoRow("完成标准", plan.metadata.completionCriteria.ifBlank { "未设置" })
                    if (plan.metadata.linkedArchive.isNotBlank()) PlanInfoRow("关联库存档案", plan.metadata.linkedArchive)
                }
                PlanSection("执行安排", "日期、时间与备注") {
                    PlanInfoRow("计划日期", plan.plannedDate ?: "未指定")
                    PlanInfoRow("计划时间", plan.metadata.time.ifBlank { "不指定" })
                    PlanInfoRow("计划来源", plan.metadata.source)
                    PlanInfoRow("备注", plan.metadata.note.ifBlank { "暂无" })
                }
            }
            PlanSection("执行状态", "TASK STATUS") {
                PlanInfoRow("当前状态", plan.statusLabel())
                PlanInfoRow("完成记录", plan.completedAt?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: if (plan.completed) "已完成" else "尚未完成")
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            if (!plan.completed) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("编辑计划", Modifier.clickable { onEdit(plan) }.padding(vertical = 13.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("删除计划", Modifier.clickable { viewModel.deleteUnfinished(plan); onDeleted() }.padding(vertical = 13.dp), color = MaterialTheme.colorScheme.error)
                }
                PlanTextAction(if (plan.inProgress) "标记为已完成" else "开始执行", true) { if (plan.inProgress) viewModel.complete(plan) else viewModel.start(plan) }
            } else Text("此计划已完成，记录已转入计划回收站。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanInfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
