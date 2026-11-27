package com.culinary.plans

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.database.dao.ProductionPlanDao
import com.culinary.core.database.dao.WorkPlanDao
import com.culinary.core.database.entity.ProductionPlanEntity
import com.culinary.core.database.entity.WorkPlanEntity
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class PlanUiItem(
    val id: String,
    val kind: PlanKind,
    val title: String,
    val plannedDate: String?,
    val description: String?,
    val status: String,
    val completedAt: Long?,
) {
    val completed: Boolean get() = status == "COMPLETED"
}

@HiltViewModel
class PlanManagementViewModel @Inject constructor(
    private val productionDao: ProductionPlanDao,
    private val workDao: WorkPlanDao,
) : ViewModel() {
    val plans = combine(productionDao.observeAll(), workDao.observeAll()) { production, work ->
        (production.map { it.toUi() } + work.map { it.toUi() })
            .sortedWith(compareBy<PlanUiItem> { it.plannedDate ?: "9999-99-99" }.thenByDescending { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message

    fun save(kind: PlanKind, id: String?, title: String, date: String?, description: String?) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            _message.value = "请填写计划名称"
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val cleanDate = date?.trim()?.takeIf(String::isNotEmpty)
            val cleanDescription = description?.trim()?.takeIf(String::isNotEmpty)
            if (id == null) {
                val newId = UUID.randomUUID().toString()
                when (kind) {
                    PlanKind.PRODUCTION -> productionDao.insert(
                        ProductionPlanEntity(id = newId, title = cleanTitle, plannedDate = cleanDate, notes = cleanDescription, createdAt = now, updatedAt = now),
                    )
                    PlanKind.WORK -> workDao.insert(
                        WorkPlanEntity(id = newId, title = cleanTitle, description = cleanDescription, plannedDate = cleanDate, createdAt = now, updatedAt = now),
                    )
                }
            } else {
                when (kind) {
                    PlanKind.PRODUCTION -> productionDao.updateOpen(id, cleanTitle, cleanDate, cleanDescription, now)
                    PlanKind.WORK -> workDao.updateOpen(id, cleanTitle, cleanDescription, cleanDate, now)
                }
            }
            _message.value = null
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

    private fun ProductionPlanEntity.toUi() = PlanUiItem(id, PlanKind.PRODUCTION, title, plannedDate, notes, status.name, completedAt)
    private fun WorkPlanEntity.toUi() = PlanUiItem(id, PlanKind.WORK, title, plannedDate, description, status, completedAt)
}

@Composable
fun AllPlansRoute(
    onAdd: () -> Unit,
    onOpen: (PlanUiItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanManagementViewModel = hiltViewModel(),
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("全部计划", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text("制作计划与工作计划分别保存", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(22.dp))
        TextButton(onClick = onAdd) { Text("＋ 添加计划") }
        PlanGroup("制作计划", plans.filter { it.kind == PlanKind.PRODUCTION && !it.completed }, onOpen)
        PlanGroup("工作计划", plans.filter { it.kind == PlanKind.WORK && !it.completed }, onOpen)
    }
}

@Composable
private fun PlanGroup(title: String, items: List<PlanUiItem>, onOpen: (PlanUiItem) -> Unit) {
    Spacer(Modifier.height(18.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    if (items.isEmpty()) Text("暂无计划", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    else items.forEach { plan ->
        Column(Modifier.fillMaxWidth().clickable { onOpen(plan) }.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(plan.title, style = MaterialTheme.typography.bodyLarge)
            Text(listOfNotNull(plan.plannedDate, plan.statusLabel()).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    val existing = plans.firstOrNull { it.id == planId }
    var kind by remember(planId, kindArg) { mutableStateOf(existing?.kind ?: if (kindArg == "work") PlanKind.WORK else PlanKind.PRODUCTION) }
    var title by remember(planId) { mutableStateOf(existing?.title.orEmpty()) }
    var date by remember(planId) { mutableStateOf(existing?.plannedDate.orEmpty()) }
    var description by remember(planId) { mutableStateOf(existing?.description.orEmpty()) }
    LaunchedEffect(existing?.id) {
        if (existing != null) {
            title = existing.title
            date = existing.plannedDate.orEmpty()
            description = existing.description.orEmpty()
            kind = existing.kind
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(if (planId == null) "添加计划" else "编辑计划", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))
        Text("计划类型", style = MaterialTheme.typography.labelMedium)
        Row {
            TextButton(onClick = { kind = PlanKind.PRODUCTION }, enabled = planId == null || kind == PlanKind.PRODUCTION) { Text(if (kind == PlanKind.PRODUCTION) "✓ 制作计划" else "制作计划") }
            TextButton(onClick = { kind = PlanKind.WORK }, enabled = planId == null || kind == PlanKind.WORK) { Text(if (kind == PlanKind.WORK) "✓ 工作计划" else "工作计划") }
        }
        OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("计划名称") }, singleLine = true)
        OutlinedTextField(value = date, onValueChange = { date = it }, modifier = Modifier.fillMaxWidth(), label = { Text("计划日期（YYYY-MM-DD，可留空）") }, singleLine = true)
        OutlinedTextField(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (kind == PlanKind.PRODUCTION) "制作说明" else "工作内容") }, minLines = 3)
        Spacer(Modifier.height(20.dp))
        Row {
            TextButton(onClick = onBack) { Text("返回") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { viewModel.save(kind, planId, title, date, description); onSaved() }) { Text("保存") }
        }
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("计划详情", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        if (plan == null) Text("计划不存在或已删除") else {
            Text(if (plan.kind == PlanKind.PRODUCTION) "制作计划" else "工作计划", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(plan.title, style = MaterialTheme.typography.titleLarge)
            plan.plannedDate?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            plan.description?.takeIf(String::isNotBlank)?.let { Spacer(Modifier.height(16.dp)); Text(it, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(24.dp))
            Text("状态：${plan.statusLabel()}", style = MaterialTheme.typography.bodySmall)
            Row {
                if (!plan.completed) {
                    TextButton(onClick = { viewModel.complete(plan) }) { Text("标记完成") }
                    TextButton(onClick = { viewModel.deleteUnfinished(plan); onDeleted() }) { Text("删除") }
                    TextButton(onClick = { onEdit(plan) }) { Text("编辑") }
                } else Text("已完成的计划已转入计划回收站", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onBack) { Text("返回") }
    }
}

private fun PlanUiItem.statusLabel(): String = when (status) {
    "COMPLETED" -> "已完成"
    "IN_PROGRESS" -> "进行中"
    "CONFIRMED" -> "已确认"
    "CANCELLED" -> "已取消"
    else -> "未完成"
}
