package com.culinary.feature.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.designsystem.component.ArchiveListItem
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinaryColorScheme
import com.culinary.core.designsystem.theme.CulinarySizes
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles
import com.culinary.core.domain.model.ArchiveListFilter
import com.culinary.core.domain.model.InventoryCategoryNode
import com.culinary.core.domain.model.InventoryHomeSummary
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.usecase.inventory.InventoryLedgerUseCases
import com.culinary.core.domain.usecase.inventory.ListInventoryArchivesUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryCategoryTreeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 库存首页：概况、三大操作、分类目录、最近记录与全部档案入口。 */
data class InventoryHomeUiState(
    val summary: InventoryHomeSummary? = null,
    val categories: List<InventoryCategoryNode> = emptyList(),
    val recent: List<InventoryLedgerEntry> = emptyList(),
    val archiveNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * 首页 ViewModel：只读聚合，数量与状态全部来自用例，页面不持有固定数字。
 */
@HiltViewModel
class InventoryHomeViewModel
    @Inject
    constructor(
        private val listArchives: ListInventoryArchivesUseCase,
        private val listCategoryTree: ListInventoryCategoryTreeUseCase,
        private val ledger: InventoryLedgerUseCases,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(InventoryHomeUiState())
        val uiState: StateFlow<InventoryHomeUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = it.summary == null, error = null) }
                when (val result = read()) {
                    is Outcome.Success -> _uiState.value = result.value
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = InventoryErrorMessages.describe(result.error),
                            )
                        }
                }
            }
        }

        private suspend fun read(): Outcome<InventoryHomeUiState> {
            val archives =
                when (val result = listArchives(ArchiveListFilter.ALL)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> result.value
                }
            val categories =
                when (val result = listCategoryTree(null)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> result.value
                }
            val recent =
                when (val result = ledger.recent(RECENT_LIMIT)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> result.value
                }
            return Outcome.success(
                InventoryHomeUiState(
                    summary = archives.summary,
                    categories = categories,
                    recent = recent,
                    archiveNames = archives.rows.associate { it.archive.id to it.archive.name },
                    isLoading = false,
                ),
            )
        }

        private companion object {
            const val RECENT_LIMIT = 8
        }
    }

@Composable
fun InventoryHomeRoute(
    onOpenArchive: (String) -> Unit,
    onOpenCategory: (String?) -> Unit,
    onAddCategory: () -> Unit,
    onOpenReceipt: () -> Unit,
    onOpenIssue: () -> Unit,
    onOpenStocktake: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenManagement: () -> Unit,
    onOpenArchives: () -> Unit,
    viewModel: InventoryHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    InventoryHomeScreen(
        state = state,
        onOpenArchive = onOpenArchive,
        onOpenCategory = onOpenCategory,
        onAddCategory = onAddCategory,
        onOpenReceipt = onOpenReceipt,
        onOpenIssue = onOpenIssue,
        onOpenStocktake = onOpenStocktake,
        onOpenLedger = onOpenLedger,
        onOpenManagement = onOpenManagement,
        onOpenArchives = onOpenArchives,
        onRetry = viewModel::load,
    )
}

@Suppress("LongMethod", "LongParameterList") // 首页是唯一聚合入口，区块顺序即信息层级。
@Composable
private fun InventoryHomeScreen(
    state: InventoryHomeUiState,
    onOpenArchive: (String) -> Unit,
    onOpenCategory: (String?) -> Unit,
    onAddCategory: () -> Unit,
    onOpenReceipt: () -> Unit,
    onOpenIssue: () -> Unit,
    onOpenStocktake: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenManagement: () -> Unit,
    onOpenArchives: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        state.error?.let { message ->
            ErrorBanner(
                message = message,
                actionLabel = "重试",
                onAction = onRetry,
                modifier =
                    Modifier.padding(
                        start = CulinarySpacing.pageHorizontal,
                        end = CulinarySpacing.pageHorizontal,
                        bottom = CulinarySpacing.xs,
                    ),
            )
        }
        PageHeader(
            eyebrow = "MISEBASE",
            title = "库存",
            supporting = "INVENTORY",
            actions = {
                CulinaryTextButton(text = "···", onClick = onOpenManagement)
            },
        )
        if (state.isLoading) {
            LoadingRows()
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = CulinarySpacing.xl),
            ) {
                HomeSummarySection(summary = state.summary)
                HomeActionsSection(
                    onOpenReceipt = onOpenReceipt,
                    onOpenIssue = onOpenIssue,
                    onOpenStocktake = onOpenStocktake,
                )
                HomeCategorySection(
                    categories = state.categories,
                    onOpenCategory = onOpenCategory,
                    onAddCategory = onAddCategory,
                )
                HomeRecentSection(
                    recent = state.recent,
                    archiveNames = state.archiveNames,
                    onOpenArchive = onOpenArchive,
                    onOpenLedger = onOpenLedger,
                )
                SectionHeader(
                    title = "全部档案",
                    modifier =
                        Modifier
                            .padding(horizontal = CulinarySpacing.pageHorizontal)
                            .padding(top = CulinarySpacing.block),
                )
                ArchiveListItem(
                    title = "全部库存物品",
                    subtitle = "查看所有档案、库存状态与批次数",
                    onClick = onOpenArchives,
                    trailing = {
                        Icon(
                            imageVector = CulinaryIcons.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeSummarySection(summary: InventoryHomeSummary?) {
    val value = summary ?: InventoryHomeSummary(total = 0, normal = 0, low = 0, out = 0)
    SectionHeader(
        title = "库存概况",
        subtitle = "按全部库存档案统计当前状态",
        modifier =
            Modifier
                .padding(horizontal = CulinarySpacing.pageHorizontal)
                .padding(top = CulinarySpacing.sm),
    )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm),
    ) {
        HomeSummaryCell(value = value.total, label = "物品总数", modifier = Modifier.weight(1f))
        HomeSummaryCell(value = value.normal, label = "正常", modifier = Modifier.weight(1f))
        HomeSummaryCell(
            value = value.low,
            label = "低库存",
            modifier = Modifier.weight(1f),
            valueColor = if (value.low > 0) CulinaryColorScheme.current.warning else null,
        )
        HomeSummaryCell(
            value = value.out,
            label = "不足",
            modifier = Modifier.weight(1f),
            valueColor = if (value.out > 0) CulinaryColorScheme.current.danger else null,
        )
    }
}

@Composable
private fun HomeSummaryCell(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = value.toString(),
            style = CulinaryTextStyles.numeralM,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun HomeActionsSection(
    onOpenReceipt: () -> Unit,
    onOpenIssue: () -> Unit,
    onOpenStocktake: () -> Unit,
) {
    SectionHeader(
        title = "库存操作",
        modifier =
            Modifier
                .padding(horizontal = CulinarySpacing.pageHorizontal)
                .padding(top = CulinarySpacing.block),
    )
    Row(
        modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs),
    ) {
        CulinaryTextButton(
            text = "入库",
            onClick = onOpenReceipt,
            modifier = Modifier.defaultMinSize(minHeight = CulinarySizes.touchTarget),
        )
        CulinaryTextButton(
            text = "出库",
            onClick = onOpenIssue,
            modifier = Modifier.defaultMinSize(minHeight = CulinarySizes.touchTarget),
        )
        CulinaryTextButton(
            text = "库存盘点",
            onClick = onOpenStocktake,
            modifier = Modifier.defaultMinSize(minHeight = CulinarySizes.touchTarget),
        )
    }
}

@Composable
private fun HomeCategorySection(
    categories: List<InventoryCategoryNode>,
    onOpenCategory: (String?) -> Unit,
    onAddCategory: () -> Unit,
) {
    SectionHeader(
        title = "分类目录",
        subtitle = if (categories.isEmpty()) null else "共 ${categories.size} 个大分类",
        modifier =
            Modifier
                .padding(horizontal = CulinarySpacing.pageHorizontal)
                .padding(top = CulinarySpacing.block),
    )
    if (categories.isEmpty()) {
        Text(
            text = "还没有库存分类。先添加一个大分类，再把物品归档进去。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.padding(
                    start = CulinarySpacing.pageHorizontal,
                    end = CulinarySpacing.pageHorizontal,
                    top = CulinarySpacing.xs,
                    bottom = CulinarySpacing.sm,
                ),
        )
    } else {
        categories.forEach { node ->
            ArchiveListItem(
                title = node.category.name,
                meta = "${node.totalArchiveCount} 项",
                onClick = { onOpenCategory(node.category.id) },
            )
        }
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onAddCategory)
                .defaultMinSize(minHeight = CulinarySizes.touchTarget)
                .padding(
                    start = CulinarySpacing.pageHorizontal,
                    end = CulinarySpacing.pageHorizontal,
                    top = CulinarySpacing.md,
                    bottom = CulinarySpacing.xs,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "＋ 添加大分类",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
    CulinaryRule(
        modifier = Modifier.padding(top = CulinarySpacing.xxs),
    )
}

@Composable
private fun HomeRecentSection(
    recent: List<InventoryLedgerEntry>,
    archiveNames: Map<String, String>,
    onOpenArchive: (String) -> Unit,
    onOpenLedger: () -> Unit,
) {
    SectionHeader(
        title = "最近库存记录",
        modifier =
            Modifier
                .padding(horizontal = CulinarySpacing.pageHorizontal)
                .padding(top = CulinarySpacing.block),
        action = {
            CulinaryTextButton(text = "查看全部", onClick = onOpenLedger)
        },
    )
    if (recent.isEmpty()) {
        Text(
            text = "暂无库存记录。入库、出库或盘点后会在这里留下流水。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.padding(
                    start = CulinarySpacing.pageHorizontal,
                    end = CulinarySpacing.pageHorizontal,
                    top = CulinarySpacing.xs,
                    bottom = CulinarySpacing.sm,
                ),
        )
        return
    }
    recent.forEach { entry ->
        val archiveId = entry.archiveId
        val archiveName =
            archiveId?.let(archiveNames::get)
                ?: entry.note?.takeIf { it.isNotBlank() }
                ?: "未关联档案"
        ArchiveListItem(
            title = archiveName,
            subtitle = "${formatInstant(entry.occurredAt)} · ${InventoryText.ledgerType(entry.type)}",
            onClick = { if (archiveId != null) onOpenArchive(archiveId) else onOpenLedger() },
            trailing = {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = homeDeltaText(entry),
                        style = CulinaryTextStyles.numeralS,
                        color = homeDeltaColor(entry),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                    Text(
                        text = entry.stockUnit.symbol,
                        style = CulinaryTextStyles.unit,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            },
        )
    }
}

private fun homeDeltaText(entry: InventoryLedgerEntry): String =
    when {
        entry.onHandDelta > 0.0 -> "+${formatAmount(entry.onHandDelta)}"
        entry.onHandDelta < 0.0 -> formatAmount(entry.onHandDelta)
        else -> "±0"
    }

@Composable
private fun homeDeltaColor(entry: InventoryLedgerEntry): Color =
    when {
        entry.onHandDelta > 0.0 -> CulinaryColorScheme.current.success
        entry.onHandDelta < 0.0 -> CulinaryColorScheme.current.danger
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
