package com.culinary.feature.inventory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.database.dao.TemporaryBarcodeDao
import com.culinary.core.database.entity.TemporaryBarcodeEntity
import com.culinary.core.database.entity.TemporaryBarcodeHistoryEntity
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.theme.CulinarySpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BarcodePoolUiState(
    val rows: List<TemporaryBarcodeEntity> = emptyList(),
    val generations: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val freeCount get() = rows.count { it.status == "FREE" }
    val allocatedCount get() = rows.count { it.status == "ALLOCATED" }
    val pendingCleanCount get() = rows.count { it.status == "PENDING_CLEAN" }
}

@HiltViewModel
class BarcodePoolViewModel @Inject constructor(
    private val dao: TemporaryBarcodeDao,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BarcodePoolUiState())
    val uiState: StateFlow<BarcodePoolUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                dao.releaseTerminalBatchAllocations(System.currentTimeMillis())
                val rows = dao.listAll()
                val generations = rows.associate { it.id to dao.allocationGeneration(it.id) }
                _uiState.update { it.copy(rows = rows, generations = generations, isLoading = false) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message ?: "读取临时条码池失败。") }
            }
        }
    }

    fun clean(item: TemporaryBarcodeEntity) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null, message = null) }
            val now = System.currentTimeMillis()
            val batchId = dao.latestAllocatedBatchId(item.id) ?: "released"
            runCatching {
                dao.cleanAndLog(
                    item.id,
                    now,
                    TemporaryBarcodeHistoryEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        barcodeId = item.id,
                        barcode = item.barcode,
                        batchId = batchId,
                        action = "CLEANED",
                        occurredAt = now,
                    ),
                )
            }.onSuccess { changed ->
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        message = if (changed == 1) "条码已清洁并归还空闲池。" else null,
                        error = if (changed == 0) "条码状态已变化，请刷新后重试。" else null,
                    )
                }
                load()
            }.onFailure { error ->
                _uiState.update { it.copy(isWorking = false, error = error.message ?: "清洁操作失败。") }
            }
        }
    }
}

@Composable
fun BarcodePoolRoute(
    onBack: () -> Unit,
    viewModel: BarcodePoolViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BarcodePoolScreen(
        state = state,
        onBack = onBack,
        onReload = viewModel::load,
        onClean = viewModel::clean,
    )
}

@Composable
private fun BarcodePoolScreen(
    state: BarcodePoolUiState,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onClean: (TemporaryBarcodeEntity) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        state.error?.let { ErrorBanner(message = it, onAction = onReload, modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) }
        if (state.isLoading) {
            LoadingRows()
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = CulinarySpacing.xl),
            ) {
                PageHeader(
                    eyebrow = "BARCODE POOL",
                    title = "临时条码池",
                    supporting = "统一管理批次临时条码；清洁后复用资源位，每次重新分配都会生成新代次，旧标签永久失效。",
                    actions = { CulinaryTextButton(text = "返回", onClick = onBack) },
                )
                Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
                    Text(
                        text = "空闲 ${state.freeCount}  ·  已分配 ${state.allocatedCount}  ·  待清洁 ${state.pendingCleanCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = CulinarySpacing.md),
                    )
                    SectionHeader(title = "条码资源", subtitle = "共 ${state.rows.size} 个编号")
                    if (state.rows.isEmpty()) {
                        EmptyState(
                            title = "条码池为空",
                            description = "首次为无原生条码的批次分配临时条码时，系统会自动建立资源。",
                            actionLabel = "重新加载",
                            onAction = onReload,
                        )
                    } else {
                        state.rows.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.barcode, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                    val generation = state.generations[item.id] ?: 0
                                    Text(
                                        text = (if (generation == 0) "尚未分配" else "第 ${generation} 次分配") + " · " + when (item.status) {
                                            "FREE" -> "空闲"
                                            "ALLOCATED" -> "已分配 · 批次 ${item.batchId?.take(8) ?: "—"}"
                                            "PENDING_CLEAN" -> "待清洁"
                                            else -> "状态异常：${item.status}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                when (item.status) {
                                    "FREE" -> StatusBadge(text = "空闲", tone = StatusTone.Success)
                                    "ALLOCATED" -> StatusBadge(text = "已分配", tone = StatusTone.Warning)
                                    "PENDING_CLEAN" -> CulinaryTextButton(
                                        text = "确认清洁",
                                        onClick = { onClean(item) },
                                        enabled = !state.isWorking,
                                    )
                                    else -> StatusBadge(text = "异常", tone = StatusTone.Danger)
                                }
                            }
                            CulinaryRule()
                        }
                    }
                }
            }
        }
    }
}
