package com.culinary.feature.inventory

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.designsystem.component.ConfirmDialog
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinaryColorScheme
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.ArchiveCategory
import com.culinary.core.domain.model.ArchiveLibrary
import com.culinary.core.domain.model.ArchiveListFilter
import com.culinary.core.domain.usecase.archive.ListArchiveCategoriesUseCase
import com.culinary.core.domain.usecase.inventory.InventoryCategoryUseCases
import com.culinary.core.domain.usecase.inventory.ListInventoryArchivesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 分类管理的一行：缩进层级、子分类数与直属档案数决定可删除性与排序位置。 */
data class CategoryManageRow(
    val category: ArchiveCategory,
    val depth: Int,
    val childCount: Int,
    val archiveCount: Int,
    val siblingIndex: Int,
    val siblingCount: Int,
) {
    val isEmptyCategory: Boolean get() = childCount == 0 && archiveCount == 0
}

data class CategoryManageUiState(
    val rows: List<CategoryManageRow> = emptyList(),
    val total: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
)

/** 分类管理 ViewModel：读写全部走 [InventoryCategoryUseCases]，失败原因原样回显。 */
@HiltViewModel
class CategoryManageViewModel
    @Inject
    constructor(
        private val listCategories: ListArchiveCategoriesUseCase,
        private val listArchives: ListInventoryArchivesUseCase,
        private val categoryUseCases: InventoryCategoryUseCases,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(CategoryManageUiState())
        val uiState: StateFlow<CategoryManageUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = it.rows.isEmpty(), error = null) }
                when (val result = read()) {
                    is Outcome.Success ->
                        _uiState.update {
                            it.copy(
                                rows = result.value,
                                total = result.value.size,
                                isLoading = false,
                            )
                        }

                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                        }
                }
            }
        }

        private suspend fun read(): Outcome<List<CategoryManageRow>> {
            val categories = listCategories(ArchiveLibrary.INVENTORY).first()
            val archives =
                when (val result = listArchives(ArchiveListFilter.ALL)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> result.value.rows
                }
            val counts = archives.groupingBy { it.archive.categoryId }.eachCount()
            return Outcome.success(flatten(categories, counts))
        }

        private fun flatten(
            categories: List<ArchiveCategory>,
            counts: Map<String?, Int>,
        ): List<CategoryManageRow> {
            val byParent = categories.groupBy { it.parentId }
            val rows = mutableListOf<CategoryManageRow>()

            fun walk(
                parentId: String?,
                depth: Int,
            ) {
                val siblings = byParent[parentId].orEmpty().sortedWith(compareBy({ it.sortOrder }, { it.name }))
                siblings.forEachIndexed { index, category ->
                    rows +=
                        CategoryManageRow(
                            category = category,
                            depth = depth,
                            childCount = byParent[category.id].orEmpty().size,
                            archiveCount = counts[category.id] ?: 0,
                            siblingIndex = index,
                            siblingCount = siblings.size,
                        )
                    walk(category.id, depth + 1)
                }
            }

            walk(null, 1)
            return rows
        }

        fun create(
            parentId: String?,
            name: String,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(error = null, message = null) }
                when (val result = categoryUseCases.create(parentId, name)) {
                    is Outcome.Success -> {
                        _uiState.update { it.copy(message = "已新建分类「${result.value.name}」") }
                        load()
                    }

                    is Outcome.Failure -> _uiState.update { it.copy(error = categoryErrorText(result.error)) }
                }
            }
        }

        fun rename(
            id: String,
            name: String,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(error = null, message = null) }
                when (val result = categoryUseCases.rename(id, name)) {
                    is Outcome.Success -> {
                        _uiState.update { it.copy(message = "已重命名为「${result.value.name}」") }
                        load()
                    }

                    is Outcome.Failure -> _uiState.update { it.copy(error = categoryErrorText(result.error)) }
                }
            }
        }

        /** 同级内上移/下移：按当前显示顺序重排后调用 reorder。 */
        fun move(
            id: String,
            delta: Int,
        ) {
            val rows = _uiState.value.rows
            val current = rows.firstOrNull { it.category.id == id } ?: return
            val siblings = rows.filter { it.category.parentId == current.category.parentId }
            val index = siblings.indexOfFirst { it.category.id == id }
            val target = index + delta
            if (index < 0 || target !in siblings.indices) return
            val ordered = siblings.map { it.category.id }.toMutableList()
            val moved = ordered.removeAt(index)
            ordered.add(target, moved)
            viewModelScope.launch {
                _uiState.update { it.copy(error = null, message = null) }
                when (val result = categoryUseCases.reorder(ordered)) {
                    is Outcome.Success -> {
                        _uiState.update { it.copy(message = "已调整排序") }
                        load()
                    }

                    is Outcome.Failure -> _uiState.update { it.copy(error = categoryErrorText(result.error)) }
                }
            }
        }

        fun delete(id: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(error = null, message = null) }
                when (val result = categoryUseCases.delete(id)) {
                    is Outcome.Success -> {
                        _uiState.update { it.copy(message = "已删除分类") }
                        load()
                    }

                    is Outcome.Failure -> _uiState.update { it.copy(error = categoryErrorText(result.error)) }
                }
            }
        }
    }

/** 分类写入失败原因：删不掉时必须说明"为什么"。 */
private fun categoryErrorText(error: CulinaryError): String =
    when (error) {
        is CulinaryError.Validation ->
            when {
                error.rule.contains("blank") -> "分类名称不能为空。"
                error.rule.contains("20 characters") -> "分类名称最多 20 个字。"
                error.rule.contains("child categories") -> "该分类下还有子分类，无法删除。请先删除或移动子分类。"
                error.rule.contains("inventory archives") -> "该分类下还有库存档案，无法删除。请先把档案移到其他分类。"
                error.rule.contains("parent category") -> "目标父分类不属于库存分类。"
                else -> "分类操作未通过校验，请检查输入。"
            }

        is CulinaryError.NotFound -> "分类不存在，可能已被删除。"
        else -> InventoryErrorMessages.describe(error)
    }

@Composable
fun CategoryManageRoute(
    onBack: () -> Unit,
    viewModel: CategoryManageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    CategoryManageScreen(
        state = state,
        onBack = onBack,
        onCreate = viewModel::create,
        onRename = viewModel::rename,
        onMove = viewModel::move,
        onDelete = viewModel::delete,
        onRetry = viewModel::load,
    )
}

/** 管理页的返回条：返回按钮与标题同行，下面一条细线分隔。 */
@Composable
private fun ManageBackBar(
    title: String,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(CulinarySpacing.xxl)
                    .padding(horizontal = CulinarySpacing.pageHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .clickable(role = Role.Button, onClick = onBack)
                        .defaultMinSize(minHeight = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = CulinaryIcons.Back,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CulinaryRule()
    }
}

@Composable
private fun CategoryManageScreen(
    state: CategoryManageUiState,
    onBack: () -> Unit,
    onCreate: (String?, String) -> Unit,
    onRename: (String, String) -> Unit,
    onMove: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    onRetry: () -> Unit,
) {
    var dialog by remember { mutableStateOf<ManageDialog?>(null) }
    Column(modifier = Modifier.fillMaxSize()) {
        ManageBackBar(title = "INVENTORY CATEGORIES", onBack = onBack)
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
        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier =
                    Modifier.padding(
                        start = CulinarySpacing.pageHorizontal,
                        end = CulinarySpacing.pageHorizontal,
                        bottom = CulinarySpacing.xs,
                    ),
            )
        }
        PageHeader(
            eyebrow = "INVENTORY",
            title = "分类管理",
            supporting = "共 ${state.total} 个分类 · 仅空分类可以删除",
            actions = {
                CulinaryTextButton(text = "＋ 大分类", onClick = { dialog = ManageDialog.Create(parent = null) })
            },
        )
        when {
            state.isLoading -> LoadingRows()
            state.rows.isEmpty() ->
                EmptyState(
                    icon = CulinaryIcons.Inventory,
                    title = "还没有库存分类",
                    description = "先建立一个大分类（例如「肉类」「干货」），再按需要添加子分类。",
                    actionLabel = "新建大分类",
                    onAction = { dialog = ManageDialog.Create(parent = null) },
                )

            else -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = CulinarySpacing.xl),
                ) {
                    state.rows.forEach { row ->
                        CategoryManageRowItem(
                            row = row,
                            onOpenActions = { dialog = ManageDialog.Actions(row) },
                        )
                    }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(
                                    role = Role.Button,
                                    onClick = { dialog = ManageDialog.Create(parent = null) },
                                ).defaultMinSize(minHeight = 48.dp)
                                .padding(
                                    horizontal = CulinarySpacing.pageHorizontal,
                                    vertical = CulinarySpacing.md,
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "＋ 添加大分类",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
    }

    when (val current = dialog) {
        null -> Unit

        is ManageDialog.Create ->
            CategoryNameDialog(
                title = if (current.parent == null) "新建大分类" else "在「${current.parent.name}」下新建子分类",
                initial = "",
                confirmLabel = "新建",
                onDismiss = { dialog = null },
                onConfirm = { name ->
                    onCreate(current.parent?.id, name)
                    dialog = null
                },
            )

        is ManageDialog.Rename ->
            CategoryNameDialog(
                title = "重命名分类",
                initial = current.row.category.name,
                confirmLabel = "保存",
                onDismiss = { dialog = null },
                onConfirm = { name ->
                    onRename(current.row.category.id, name)
                    dialog = null
                },
            )

        is ManageDialog.Actions ->
            CategoryActionsDialog(
                row = current.row,
                onDismiss = { dialog = null },
                onAddChild = {
                    dialog = ManageDialog.Create(parent = current.row.category)
                },
                onRename = { dialog = ManageDialog.Rename(current.row) },
                onMoveUp = {
                    onMove(current.row.category.id, -1)
                    dialog = null
                },
                onMoveDown = {
                    onMove(current.row.category.id, 1)
                    dialog = null
                },
                onDelete = { dialog = ManageDialog.Delete(current.row) },
            )

        is ManageDialog.Delete ->
            ConfirmDialog(
                title = "删除分类",
                message =
                    if (current.row.isEmptyCategory) {
                        "「${current.row.category.name}」下没有子分类与档案，删除后不可恢复。"
                    } else {
                        "「${current.row.category.name}」下还有 ${current.row.childCount} 个子分类与 " +
                            "${current.row.archiveCount} 个档案，删除会失败；需要先清空该分类。"
                    },
                confirmLabel = "删除",
                destructive = true,
                onConfirm = {
                    onDelete(current.row.category.id)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
    }
}

private sealed interface ManageDialog {
    data class Create(
        val parent: ArchiveCategory?,
    ) : ManageDialog

    data class Rename(
        val row: CategoryManageRow,
    ) : ManageDialog

    data class Actions(
        val row: CategoryManageRow,
    ) : ManageDialog

    data class Delete(
        val row: CategoryManageRow,
    ) : ManageDialog
}

@Composable
private fun CategoryManageRowItem(
    row: CategoryManageRow,
    onOpenActions: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpenActions)
                .defaultMinSize(minHeight = 56.dp)
                .padding(
                    start = CulinarySpacing.pageHorizontal + ((row.depth - 1) * 16).dp,
                    end = CulinarySpacing.pageHorizontal,
                    top = CulinarySpacing.sm,
                    bottom = CulinarySpacing.sm,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.category.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = buildString {
                    append(row.archiveCount).append(" 项档案")
                    if (row.childCount > 0) append(" · ").append(row.childCount).append(" 个子分类")
                    if (row.siblingCount > 1) {
                        append(" · 第 ").append(row.siblingIndex + 1).append("/").append(row.siblingCount).append(" 位")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = CulinaryIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
    CulinaryRule(modifier = Modifier.padding(start = CulinarySpacing.pageHorizontal))
}

/** 新建/重命名共用的名称输入弹窗；空名称时确认按钮不可用。 */
@Composable
private fun CategoryNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraSmall,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            CulinaryTextField(
                value = name,
                onValueChange = { name = it },
                label = "分类名称",
                singleLine = true,
                supportingText = "最多 20 个字",
            )
        },
        confirmButton = {
            CulinaryButton(
                text = confirmLabel,
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/** 行内动作弹窗：把新建子分类、重命名、排序与删除收在一处，避免每行堆满按钮。 */
@Composable
private fun CategoryActionsDialog(
    row: CategoryManageRow,
    onDismiss: () -> Unit,
    onAddChild: () -> Unit,
    onRename: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraSmall,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = row.category.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = row.category.pathLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.xs))
                DialogActionRow(text = "新建子分类", onClick = onAddChild)
                DialogActionRow(text = "重命名", onClick = onRename)
                if (row.siblingIndex > 0) {
                    DialogActionRow(text = "上移", onClick = onMoveUp)
                }
                if (row.siblingIndex < row.siblingCount - 1) {
                    DialogActionRow(text = "下移", onClick = onMoveDown)
                }
                DialogActionRow(
                    text = if (row.isEmptyCategory) "删除" else "删除（仅空分类可删）",
                    onClick = onDelete,
                    danger = true,
                )
            }
        },
        confirmButton = {
            CulinaryTextButton(text = "关闭", onClick = onDismiss)
        },
    )
}

@Composable
private fun DialogActionRow(
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .defaultMinSize(minHeight = CulinarySpacing.xxl)
                .padding(vertical = CulinarySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (danger) {
                    CulinaryColorScheme.current.danger
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}
