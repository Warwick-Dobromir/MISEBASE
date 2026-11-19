package com.culinary.feature.ingredients

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.ArchiveEditorInlineField
import com.culinary.core.designsystem.component.ArchiveEditorSectionHeader
import com.culinary.core.designsystem.component.ArchiveEditorTopBar
import com.culinary.core.designsystem.component.ConfirmDialog
import com.culinary.core.designsystem.component.CulinaryLinkText
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles
import com.culinary.core.domain.model.ArchiveCategory

/** 食材编辑器路由：名称、分类、默认单位、描述。 */
@Composable
fun IngredientEditorRoute(
    onSaved: () -> Unit,
    onCancelled: () -> Unit = {},
    onArchived: () -> Unit = onCancelled,
    modifier: Modifier = Modifier,
    viewModel: IngredientEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showUnitPicker by remember { mutableStateOf(false) }
    val saved = state.saved
    val archived = state.archived

    LaunchedEffect(saved) {
        if (saved) onSaved()
    }
    LaunchedEffect(archived) {
        if (archived) onArchived()
    }

    Column(modifier = modifier.fillMaxSize()) {
        state.error?.let { message ->
            ErrorBanner(
                message = message,
                onAction = viewModel::clearError,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            )
        }
        if (state.isLoading) {
            LoadingRows()
        } else {
            IngredientEditorForm(
                state = state,
                onNameChange = viewModel::onNameChange,
                onCategoryChange = viewModel::onCategoryChange,
                onCategorySelect = viewModel::onCategorySelect,
                onDescriptionChange = viewModel::onDescriptionChange,
                onEnglishNameChange = viewModel::onEnglishNameChange,
                onScientificNameChange = viewModel::onScientificNameChange,
                onFlavorProfileChange = viewModel::onFlavorProfileChange,
                onSeasonalityChange = viewModel::onSeasonalityChange,
                onOriginChange = viewModel::onOriginChange,
                onSelectionNotesChange = viewModel::onSelectionNotesChange,
                onPrepNotesChange = viewModel::onPrepNotesChange,
                onExampleProductsChange = viewModel::onExampleProductsChange,
                onStorageNotesChange = viewModel::onStorageNotesChange,
                onAllergenNotesChange = viewModel::onAllergenNotesChange,
                onOpenUnitPicker = { showUnitPicker = true },
                onSave = viewModel::save,
                onCancel = onCancelled,
                onArchive = viewModel::archive,
            )
        }
    }

    if (showUnitPicker) {
        IngredientUnitPickerDialog(
            units = state.units,
            selectedId = state.defaultUnit?.id?.value,
            onSelect = {
                viewModel.onUnitChange(it)
                showUnitPicker = false
            },
            onDismiss = { showUnitPicker = false },
        )
    }
}

@Suppress("LongMethod") // 食材新建与编辑共用同一档案表单。
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IngredientEditorForm(
    state: IngredientEditorUiState,
    onNameChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onCategorySelect: (Int, String?) -> Unit = { _, _ -> },
    onDescriptionChange: (String) -> Unit,
    onEnglishNameChange: (String) -> Unit,
    onScientificNameChange: (String) -> Unit = {},
    onFlavorProfileChange: (String) -> Unit = {},
    onSeasonalityChange: (String) -> Unit = {},
    onOriginChange: (String) -> Unit = {},
    onSelectionNotesChange: (String) -> Unit = {},
    onPrepNotesChange: (String) -> Unit = {},
    onExampleProductsChange: (String) -> Unit,
    onStorageNotesChange: (String) -> Unit,
    onAllergenNotesChange: (String) -> Unit,
    onOpenUnitPicker: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit = {},
    onArchive: () -> Unit = {},
) {
    var showDelete by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    val baseline = remember(state.ingredientId) { mutableStateOf<IngredientEditorSnapshot?>(null) }
    LaunchedEffect(state.isLoading, state.ingredientId) {
        if (!state.isLoading && baseline.value == null) {
            baseline.value = state.toSnapshot()
        }
    }
    val isDirty = baseline.value?.let { it != state.toSnapshot() } ?: false
    val imeVisible = WindowInsets.isImeVisible
    val editorTitle =
        stringResource(
            if (state.isNew) R.string.ingredients_editor_new_title else R.string.ingredients_editor_edit_title,
        )
    val canSave = !state.isLoading && !state.isSaving && state.name.isNotBlank() && (state.isNew || isDirty)
    val defaultUnitLabel =
        state.defaultUnit?.let { "${it.name}（${it.symbol}）" }
            ?: stringResource(R.string.ingredients_no_unit)
    BackHandler(enabled = isDirty && !imeVisible) { showDiscard = true }
    BackHandler(enabled = !isDirty && !imeVisible) { onCancel() }
    Column(modifier = Modifier.fillMaxSize()) {
        ArchiveEditorTopBar(
            title = editorTitle,
            onBack = { if (isDirty) showDiscard = true else onCancel() },
            onCancel = { if (isDirty) showDiscard = true else onCancel() },
            onSave = onSave,
            isSaving = state.isSaving,
            canSave = canSave,
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CulinarySpacing.pageHorizontal)
                    .padding(bottom = CulinarySpacing.xl),
        ) {
            ArchiveEditorSectionHeader(index = "01", english = "IDENTITY", title = "基础信息")
            IngredientEditorField(
                label = "食材名称",
                value = state.name,
                onValueChange = onNameChange,
                placeholder = "填写食材名称",
            )
            IngredientEditorField(
                label = "英文名称",
                value = state.englishName,
                onValueChange = onEnglishNameChange,
                placeholder = "填写英文名称",
            )
            IngredientEditorField(
                label = "档案描述",
                value = state.description,
                onValueChange = onDescriptionChange,
                placeholder = "简要描述食材特点与用途",
                singleLine = false,
                minLines = 2,
            )
            IngredientEditorField(
                label = "学名",
                value = state.scientificName,
                onValueChange = onScientificNameChange,
                placeholder = "填写学名（选填）",
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.sm))
            IngredientClassificationSelector(
                state = state,
                onCategorySelect = onCategorySelect,
                onLegacyCategoryChange = onCategoryChange,
            )
            Text(
                text = stringResource(R.string.ingredients_default_unit),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            UnitSelectionRow(
                value = defaultUnitLabel,
                onClick = onOpenUnitPicker,
            )

            ArchiveEditorSectionHeader(index = "02", english = "CHARACTERISTICS", title = "风味与特性")
            IngredientEditorField(
                label = "风味特征",
                value = state.flavorProfile,
                onValueChange = onFlavorProfileChange,
                placeholder = "描述主要风味、香气与口感",
                singleLine = false,
                minLines = 2,
            )
            IngredientEditorField(
                label = "时令",
                value = state.seasonality,
                onValueChange = onSeasonalityChange,
                placeholder = "填写主要产季或供应季节",
            )
            IngredientEditorField(
                label = "产地",
                value = state.origin,
                onValueChange = onOriginChange,
                placeholder = "填写主要产地或产区",
            )
            IngredientEditorField(
                label = "挑选标准",
                value = state.selectionNotes,
                onValueChange = onSelectionNotesChange,
                placeholder = "填写品种、规格、新鲜度与品质判断标准",
                singleLine = false,
                minLines = 2,
            )
            IngredientEditorField(
                label = "预处理",
                value = state.prepNotes,
                onValueChange = onPrepNotesChange,
                placeholder = "填写清洗、去皮、切配及预处理要求",
                singleLine = false,
                minLines = 2,
            )

            ArchiveEditorSectionHeader(index = "03", english = "CULINARY APPLICATION", title = "烹饪应用")
            IngredientEditorField(
                label = "常见用途",
                value = state.exampleProducts,
                onValueChange = onExampleProductsChange,
                placeholder = "填写适用菜式、酱汁或常见用途",
                singleLine = false,
                minLines = 2,
            )

            ArchiveEditorSectionHeader(index = "04", english = "STORAGE & SAFETY", title = "储存与安全")
            IngredientEditorField(
                label = "储存说明",
                value = state.storageNotes,
                onValueChange = onStorageNotesChange,
                placeholder = "填写温度、湿度、包装及保存要求",
                singleLine = false,
                minLines = 2,
            )
            IngredientEditorField(
                label = "过敏原提示",
                value = state.allergenNotes,
                onValueChange = onAllergenNotesChange,
                placeholder = "填写过敏原及交叉接触风险（选填）",
                singleLine = false,
                minLines = 2,
            )

            ArchiveEditorSectionHeader(
                index = "05",
                english = "UNIT CONVERSIONS",
                title = "单位换算",
                subtitle = "用于库存与配方计算；单位关系按系统已有设置执行",
            )
            if (!state.isNew) {
                Spacer(modifier = Modifier.height(CulinarySpacing.xl))
                CulinaryRule()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.md),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CulinaryLinkText(
                        text = "删除此食材档案",
                        onClick = { showDelete = true },
                    )
                }
            }
        }
    }
    if (showDiscard) {
        ConfirmDialog(
            title = "放弃未保存修改？",
            message = "当前食材档案尚有未保存的修改，离开后这些内容会丢失。",
            confirmLabel = "放弃修改",
            dismissLabel = "继续编辑",
            onConfirm = {
                showDiscard = false
                onCancel()
            },
            onDismiss = { showDiscard = false },
            destructive = true,
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.ingredients_archive_title),
            message = stringResource(R.string.ingredients_archive_message),
            confirmLabel = stringResource(R.string.ingredients_archive),
            dismissLabel = stringResource(R.string.ingredients_cancel),
            onConfirm = {
                showDelete = false
                onArchive()
            },
            onDismiss = { showDelete = false },
            destructive = true,
        )
    }
}

private data class IngredientEditorSnapshot(
    val name: String,
    val category: String,
    val description: String,
    val englishName: String,
    val scientificName: String,
    val flavorProfile: String,
    val seasonality: String,
    val origin: String,
    val selectionNotes: String,
    val prepNotes: String,
    val exampleProducts: String,
    val storageNotes: String,
    val allergenNotes: String,
    val categoryPathIds: List<String>,
    val defaultUnitId: String?,
)

private fun IngredientEditorUiState.toSnapshot(): IngredientEditorSnapshot =
    IngredientEditorSnapshot(
        name = name,
        category = category,
        description = description,
        englishName = englishName,
        scientificName = scientificName,
        flavorProfile = flavorProfile,
        seasonality = seasonality,
        origin = origin,
        selectionNotes = selectionNotes,
        prepNotes = prepNotes,
        exampleProducts = exampleProducts,
        storageNotes = storageNotes,
        allergenNotes = allergenNotes,
        categoryPathIds = categoryPathIds,
        defaultUnitId = defaultUnit?.id?.value,
    )

@Composable
private fun IngredientEditorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = CulinarySpacing.md)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
        ArchiveEditorInlineField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            singleLine = singleLine,
            minLines = minLines,
            modifier = Modifier.fillMaxWidth(),
        )
        CulinaryRule()
    }
}

@Composable
private fun IngredientClassificationSelector(
    state: IngredientEditorUiState,
    onCategorySelect: (Int, String?) -> Unit,
    onLegacyCategoryChange: (String) -> Unit,
) {
    val formal = state.categories.filter { !it.isTest }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "CLASSIFICATION / 分类归属",
            style = CulinaryTextStyles.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        if (formal.isEmpty()) {
            Text(
                text = stringResource(R.string.ingredients_category_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ArchiveEditorInlineField(
                value = state.category,
                onValueChange = onLegacyCategoryChange,
                placeholder = "填写分类",
                modifier = Modifier.fillMaxWidth(),
            )
            CulinaryRule()
            return
        }
        var level = 0
        while (level <= state.categoryPathIds.size) {
            val parentId = if (level == 0) null else state.categoryPathIds.getOrNull(level - 1)
            val options = formal.filter { it.parentId == parentId }
            if (options.isEmpty()) break
            IngredientCategoryLevelField(
                level = level,
                selectedId = state.categoryPathIds.getOrNull(level),
                options = options,
                onSelect = { id -> onCategorySelect(level, id) },
            )
            level++
            if (level > 32) break
        }
    }
}

@Composable
private fun IngredientCategoryLevelField(
    level: Int,
    selectedId: String?,
    options: List<ArchiveCategory>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }
    Column(modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.xs)) {
        Text(
            text = "LEVEL ${(level + 1).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true }
                        .padding(vertical = CulinarySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected?.name ?: "选择分类",
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (selected == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    modifier = Modifier.weight(1f),
                )
                if (selected != null) {
                    Text(
                        text = "清除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onSelect(null) },
                    )
                }
                Icon(
                    imageVector = CulinaryIcons.ChevronDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = CulinarySpacing.xs).size(14.dp),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            expanded = false
                            onSelect(category.id)
                        },
                    )
                }
            }
        }
        CulinaryRule()
    }
}

/** 选择型字段：与输入框同一高度，右侧 chevron 表示可展开选择。 */
@Composable
private fun UnitSelectionRow(
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .padding(vertical = CulinarySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = CulinaryIcons.ChevronDown,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    CulinaryRule()
}

@Composable
private fun IngredientUnitPickerDialog(
    units: List<com.culinary.core.domain.model.Unit>,
    selectedId: String?,
    onSelect: (com.culinary.core.domain.model.Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.ingredients_unit_picker_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            if (units.isEmpty()) {
                Text(text = stringResource(R.string.ingredients_picker_empty))
            } else {
                Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    units.forEach { unit ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(unit) }
                                    .padding(vertical = CulinarySpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${unit.name}（${unit.symbol}）",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (unit.id.value == selectedId) {
                                Icon(
                                    imageVector = CulinaryIcons.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                        CulinaryRule()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.ingredients_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
