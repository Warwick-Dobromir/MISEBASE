package com.culinary.feature.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryButtonVariant
import com.culinary.core.designsystem.component.CulinaryFilterPill
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.StorageLocation
import com.culinary.core.domain.usecase.inventory.ListInventoryArchivesUseCase
import com.culinary.core.domain.usecase.inventory.ListStorageLocationsUseCase
import com.culinary.core.domain.usecase.inventory.SaveStorageLocationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// 路由
// ---------------------------------------------------------------------------

/** 库位管理：树形结构、新建、重命名；有库存的库位不可删除。 */
@Composable
fun LocationRoute(
    onBack: () -> Unit,
    viewModel: LocationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LocationScreen(
        state = state,
        onBack = onBack,
        onCreate = viewModel::openCreate,
        onRename = viewModel::openRename,
        onNameChange = viewModel::onNameChange,
        onParentSelected = viewModel::onParentSelected,
        onKindSelected = viewModel::onKindSelected,
        onSave = viewModel::save,
        onDismissDialog = viewModel::closeDialog,
        onRequestDelete = viewModel::requestDelete,
        onCancelDelete = viewModel::cancelDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onReload = viewModel::load,
        onDismissError = viewModel::clearError,
    )
}

// ---------------------------------------------------------------------------
// 状态
// ---------------------------------------------------------------------------

/** 库位树行：缩进深度由父子关系计算，不做嵌套卡片。 */
data class LocationRow(
    val location: StorageLocation,
    val depth: Int,
    val childCount: Int,
    val hasStock: Boolean,
)

/** 库位编辑弹窗状态。 */
data class LocationUiState(
    val rows: List<LocationRow> = emptyList(),
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val dialogOpen: Boolean = false,
    val editingId: String? = null,
    val name: String = "",
    val parentId: String? = null,
    val kind: String = LocationKinds.last(),
    val deleteTargetId: String? = null,
    val deleteTargetName: String? = null,
    val deleteBlocked: Boolean = false,
)

/** 库位类型；与 `storage_location.kind` 的取值一致。 */
internal val LocationKinds = listOf("PANTRY", "FRIDGE", "FREEZER", "DRY", "OTHER")

internal fun locationKindLabel(kind: String): String =
    when (kind) {
        "PANTRY" -> "储藏室"
        "FRIDGE" -> "冷藏"
        "FREEZER" -> "冷冻"
        "DRY" -> "干货"
        else -> "其他"
    }

/** 库位 ViewModel：库位结构可编辑，库存数量不在本页修改。 */
@HiltViewModel
class LocationViewModel
    @Inject
    constructor(
        private val listLocations: ListStorageLocationsUseCase,
        private val listArchives: ListInventoryArchivesUseCase,
        private val saveLocation: SaveStorageLocationUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(LocationUiState())
        val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val locations =
                    when (val result = listLocations()) {
                        is Outcome.Failure -> {
                            _uiState.update {
                                it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                            }
                            return@launch
                        }

                        is Outcome.Success -> result.value
                    }
                val stockedLocationNames =
                    when (val result = listArchives()) {
                        is Outcome.Failure -> emptySet()
                        is Outcome.Success -> result.value.rows.flatMap { it.locationNames }.toSet()
                    }
                _uiState.update {
                    it.copy(
                        rows = buildTree(locations, stockedLocationNames),
                        isLoading = false,
                    )
                }
            }
        }

        /** 按父子关系展开为带缩进的扁平行；父节点缺失时按根节点处理。 */
        private fun buildTree(
            locations: List<StorageLocation>,
            stockedNames: Set<String>,
        ): List<LocationRow> {
            val byParent = locations.groupBy { it.parentId?.value }
            val ids = locations.map { it.id.value }.toSet()
            val roots = locations.filter { it.parentId?.value?.let(ids::contains) != true }
            val result = mutableListOf<LocationRow>()
            val visited = mutableSetOf<String>()
            fun visit(
                location: StorageLocation,
                depth: Int,
            ) {
                if (!visited.add(location.id.value)) return
                val children = byParent[location.id.value].orEmpty().sortedBy { it.name }
                result +=
                    LocationRow(
                        location = location,
                        depth = depth,
                        childCount = children.size,
                        hasStock = location.name in stockedNames,
                    )
                children.forEach { visit(it, depth + 1) }
            }
            roots.sortedBy { it.name }.forEach { visit(it, 0) }
            locations.filterNot { it.id.value in visited }.sortedBy { it.name }.forEach { visit(it, 0) }
            return result
        }

        fun openCreate() {
            _uiState.update {
                it.copy(
                    dialogOpen = true,
                    editingId = null,
                    name = "",
                    parentId = null,
                    kind = "OTHER",
                    error = null,
                )
            }
        }

        fun openRename(locationId: String) {
            val row = _uiState.value.rows.firstOrNull { it.location.id.value == locationId } ?: return
            _uiState.update {
                it.copy(
                    dialogOpen = true,
                    editingId = locationId,
                    name = row.location.name,
                    parentId = row.location.parentId?.value,
                    error = null,
                )
            }
        }

        fun onNameChange(value: String) {
            _uiState.update { it.copy(name = value, error = null) }
        }

        fun onParentSelected(parentId: String?) {
            _uiState.update { it.copy(parentId = parentId) }
        }

        fun onKindSelected(kind: String) {
            _uiState.update { it.copy(kind = kind) }
        }

        fun closeDialog() {
            _uiState.update { it.copy(dialogOpen = false, editingId = null, name = "", parentId = null) }
        }

        fun save() {
            val state = _uiState.value
            val name = state.name.trim()
            if (name.isEmpty()) {
                _uiState.update { it.copy(error = "库位名称不能为空。") }
                return
            }
            val editingId = state.editingId
            // 重命名时不能把自己设为自己的父库位，避免形成环。
            val parentId = state.parentId?.takeIf { it != editingId }
            viewModelScope.launch {
                _uiState.update { it.copy(isWorking = true, error = null) }
                val result = saveLocation(editingId, name, parentId?.let(::LocationId), state.kind, 0)
                when (result) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isWorking = false, error = InventoryErrorMessages.describe(result.error))
                        }

                    is Outcome.Success -> {
                        _uiState.update {
                            it.copy(
                                isWorking = false,
                                dialogOpen = false,
                                editingId = null,
                                name = "",
                                parentId = null,
                                message = if (editingId == null) "库位已创建。" else "库位已重命名。",
                            )
                        }
                        load()
                    }
                }
            }
        }

        fun requestDelete(locationId: String) {
            val row = _uiState.value.rows.firstOrNull { it.location.id.value == locationId } ?: return
            _uiState.update {
                it.copy(
                    deleteTargetId = locationId,
                    deleteTargetName = row.location.name,
                    deleteBlocked = row.hasStock || row.childCount > 0,
                )
            }
        }

        fun cancelDelete() {
            _uiState.update { it.copy(deleteTargetId = null, deleteTargetName = null, deleteBlocked = false) }
        }

        /**
         * 删除库位：当前领域端口只提供保存（软删除未开放），
         * 因此页面只做前置校验并给出明确说明，绝不静默吞掉点击。
         */
        fun confirmDelete() {
            val state = _uiState.value
            val name = state.deleteTargetName ?: return
            if (state.deleteBlocked) {
                _uiState.update {
                    it.copy(
                        message = "「$name」下仍有库存或子库位，请先迁移库存后再删除。",
                        deleteTargetId = null,
                        deleteTargetName = null,
                        deleteBlocked = false,
                    )
                }
                return
            }
            _uiState.update {
                it.copy(
                    message = "当前版本不开放删除库位（批次需保留库位归属），可先改名停用。",
                    deleteTargetId = null,
                    deleteTargetName = null,
                    deleteBlocked = false,
                )
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }
    }

// ---------------------------------------------------------------------------
// 页面
// ---------------------------------------------------------------------------

@Composable
internal fun LocationScreen(
    state: LocationUiState,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onRename: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onParentSelected: (String?) -> Unit,
    onKindSelected: (String) -> Unit,
    onSave: () -> Unit,
    onDismissDialog: () -> Unit,
    onRequestDelete: (String) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onReload: () -> Unit,
    onDismissError: () -> Unit,
) {
    var parentPickerOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        state.error?.let { message ->
            ErrorBanner(
                message = message,
                onAction = onDismissError,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            )
        }
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
                PageHeader(
                    eyebrow = "LOCATION",
                    title = "库位",
                    supporting = "库位支持上下级；有库存的库位不能删除，请先迁移库存。",
                    actions = { CulinaryTextButton(text = "返回", onClick = onBack) },
                )
                Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
                    state.message?.let { text ->
                        StatusBadge(
                            text = text,
                            tone = StatusTone.Neutral,
                            modifier = Modifier.padding(bottom = CulinarySpacing.sm),
                        )
                    }
                    SectionHeader(
                        title = "库位树",
                        subtitle = "共 ${state.rows.size} 个库位",
                        action = {
                            CulinaryTextButton(text = "新建库位", onClick = onCreate)
                        },
                    )
                    if (state.rows.isEmpty()) {
                        EmptyState(
                            title = "还没有库位",
                            description = "先新建一个库位，入库时才能选择存放位置。",
                            actionLabel = "新建库位",
                            onAction = onCreate,
                        )
                    } else {
                        state.rows.forEach { row ->
                            LocationTreeRow(
                                row = row,
                                enabled = !state.isWorking,
                                onRename = { onRename(row.location.id.value) },
                                onDelete = { onRequestDelete(row.location.id.value) },
                            )
                            CulinaryRule()
                        }
                        Text(
                            text = "删除限制：库位下存在在库批次或子库位时不可删除，请先迁移库存。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = CulinarySpacing.sm),
                        )
                        CulinaryButton(
                            text = "重新加载库位",
                            onClick = onReload,
                            variant = CulinaryButtonVariant.Ghost,
                            enabled = !state.isWorking,
                            modifier = Modifier.padding(top = CulinarySpacing.xs),
                        )
                    }
                }
            }
        }
    }

    if (state.dialogOpen) {
        AlertDialog(
            onDismissRequest = onDismissDialog,
            shape = MaterialTheme.shapes.extraSmall,
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = if (state.editingId == null) "新建库位" else "重命名库位",
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                Column {
                    CulinaryTextField(
                        value = state.name,
                        onValueChange = onNameChange,
                        label = "库位名称 *",
                        singleLine = true,
                    )
                    CulinaryButton(
                        text =
                            state.parentId
                                ?.let { id -> state.rows.firstOrNull { it.location.id.value == id }?.location?.name }
                                ?: "父库位（可空）",
                        onClick = { parentPickerOpen = true },
                        variant = CulinaryButtonVariant.Secondary,
                        icon = CulinaryIcons.ChevronDown,
                        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md),
                    )
                    Text(
                        text = "库位类型",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = CulinarySpacing.md, bottom = CulinarySpacing.xxs),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xxs)) {
                        LocationKinds.forEach { kind ->
                            CulinaryFilterPill(
                                text = locationKindLabel(kind),
                                selected = state.kind == kind,
                                onClick = { onKindSelected(kind) },
                            )
                        }
                    }
                    if (state.name.isBlank()) {
                        Text(
                            text = "名称不能为空，最多 30 个字符。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = CulinarySpacing.xs),
                        )
                    }
                }
            },
            confirmButton = {
                CulinaryButton(
                    text = if (state.isWorking) "正在保存" else "保存",
                    onClick = onSave,
                    enabled = !state.isWorking && state.name.isNotBlank(),
                    loading = state.isWorking,
                )
            },
            dismissButton = {
                TextButton(onClick = onDismissDialog, enabled = !state.isWorking) {
                    Text(text = "取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    if (state.deleteTargetId != null) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            shape = MaterialTheme.shapes.extraSmall,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(text = "删除库位", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    text =
                        if (state.deleteBlocked) {
                            "「${state.deleteTargetName.orEmpty()}」下仍存在库存或子库位，请先迁移库存后再删除。"
                        } else {
                            "确认删除「${state.deleteTargetName.orEmpty()}」？库位结构删除后不可恢复。"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            confirmButton = {
                CulinaryButton(
                    text = if (state.deleteBlocked) "先迁移库存" else "确认删除",
                    onClick = onConfirmDelete,
                    variant = if (state.deleteBlocked) CulinaryButtonVariant.Secondary else CulinaryButtonVariant.Danger,
                )
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) {
                    Text(text = "取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    if (parentPickerOpen) {
        val options =
            listOf("" to "不设置父库位") +
                state.rows
                    .filter { it.location.id.value != state.editingId }
                    .map { it.location.id.value to "  ".repeat(it.depth) + it.location.name }
        AlertDialog(
            onDismissRequest = { parentPickerOpen = false },
            shape = MaterialTheme.shapes.extraSmall,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(text = "父库位", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    options.forEach { (value, label) ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onParentSelected(value.takeIf { it.isNotEmpty() })
                                        parentPickerOpen = false
                                    }.padding(vertical = CulinarySpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        CulinaryRule()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { parentPickerOpen = false }) {
                    Text(text = "取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

@Composable
private fun LocationTreeRow(
    row: LocationRow,
    enabled: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = (row.depth * 16).dp)
                .padding(vertical = CulinarySpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.location.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (row.hasStock) {
                StatusBadge(text = "有库存", tone = StatusTone.Accent)
            } else if (row.childCount > 0) {
                StatusBadge(text = "${row.childCount} 个子库位", tone = StatusTone.Neutral)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val hint =
                when {
                    row.hasStock -> "有库存，先迁移库存后才能删除"
                    row.childCount > 0 -> "含子库位，先迁移或删除子库位"
                    else -> "无库存"
                }
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            CulinaryTextButton(text = "重命名", onClick = onRename, enabled = enabled)
            CulinaryTextButton(
                text = "删除",
                onClick = onDelete,
                enabled = enabled && !row.hasStock,
                danger = true,
            )
        }
        if (row.hasStock) {
            Text(
                text = "先迁移库存",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
