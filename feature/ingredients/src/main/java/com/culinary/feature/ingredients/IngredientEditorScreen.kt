package com.culinary.feature.ingredients

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import com.culinary.core.designsystem.component.ArchiveEditorNotesBox
import com.culinary.core.designsystem.component.ArchiveEditorSectionHeader
import com.culinary.core.designsystem.component.ArchiveEditorTopBar
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles
import com.culinary.core.domain.model.ArchiveCategory

/** 食材编辑路由：新建与编辑共用同一表单，编辑时完整回填。 */
@Composable
fun IngredientEditorRoute(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IngredientEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showUnitPicker by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val leave: () -> Unit = { dispatcher?.onBackPressed() }
    val saved = state.saved

    LaunchedEffect(saved) {
        if (saved) onSaved()
    }

    BackHandler(enabled = state.isDirty && !state.isSaving) {
        showDiscard = true
    }

    Column(modifier = modifier.fillMaxSize()) {
        ArchiveEditorTopBar(
            title = if (state.isNew) "新建食材档案" else "编辑食材档案",
            onBack = { if (state.isDirty) showDiscard = true else leave() },
            onCancel = { if (state.isDirty) showDiscard = true else leave() },
            onSave = viewModel::save,
            isSaving = state.isSaving,
            canSave = !state.isLoading && !state.isSaving && (state.isNew || state.isDirty),
        )
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
                onOpenUnitPicker = { showUnitPicker = true },
                onSave = viewModel::save,
                onScientificNameChange = viewModel::onScientificNameChange,
                onExampleProductsChange = viewModel::onExampleProductsChange,
                onFlavorProfileChange = viewModel::onFlavorProfileChange,
                onSeasonalityChange = viewModel::onSeasonalityChange,
                onOriginChange = viewModel::onOriginChange,
                onSelectionNotesChange = viewModel::onSelectionNotesChange,
                onPrepNotesChange = viewModel::onPrepNotesChange,
                onStorageNotesChange = viewModel::onStorageNotesChange,
                onAllergenNotesChange = viewModel::onAllergenNotesChange,
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

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("放弃本次修改？") },
            text = { Text("尚未保存的食材档案内容将不会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscard = false
                        leave()
                    },
                ) { Text("放弃修改") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text("继续编辑") }
            },
        )
    }
}

@Suppress("LongMethod", "LongParameterList")
@Composable
internal fun IngredientEditorForm(
    state: IngredientEditorUiState,
    onNameChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onCategorySelect: (Int, String?) -> Unit = { _, _ -> },
    onDescriptionChange: (String) -> Unit,
    onEnglishNameChange: (String) -> Unit,
    onOpenUnitPicker: () -> Unit,
    onSave: () -> Unit,
    onScientificNameChange: (String) -> Unit = {},
    onExampleProductsChange: (String) -> Unit = {},
    onFlavorProfileChange: (String) -> Unit = {},
    onSeasonalityChange: (String) -> Unit = {},
    onOriginChange: (String) -> Unit = {},
    onSelectionNotesChange: (String) -> Unit = {},
    onPrepNotesChange: (String) -> Unit = {},
    onStorageNotesChange: (String) -> Unit = {},
    onAllergenNotesChange: (String) -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CulinarySpacing.pageHorizontal)
                .padding(bottom = CulinarySpacing.xl),
    ) {
        ArchiveEditorSectionHeader(index = "01", english = "IDENTITY", title = "基本信息", showRule = false)
        EditorTextField("食材名称", state.name, onNameChange)
        IngredientClassificationSelector(
            state = state,
            onCategorySelect = onCategorySelect,
            onLegacyCategoryChange = onCategoryChange,
        )
        EditorTextField("英文名称", state.englishName, onEnglishNameChange)
        EditorTextField("学名 / 拉丁名称", state.scientificName, onScientificNameChange)
        EditorNotesField("档案描述", state.description, onDescriptionChange)
        EditorTextField("示例商品 / 常见规格", state.exampleProducts, onExampleProductsChange)
        Text(
            text = "DEFAULT UNIT / 默认计量单位",
            style = CulinaryTextStyles.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = CulinarySpacing.md),
        )
        UnitSelectionRow(
            value =
                state.defaultUnit?.let { "${it.name}（${it.symbol}）" }
                    ?: stringResource(R.string.ingredients_no_unit),
            onClick = onOpenUnitPicker,
        )

        ArchiveEditorSectionHeader(index = "02", english = "FLAVOR", title = "风味特性")
        EditorNotesField("风味描述", state.flavorProfile, onFlavorProfileChange)

        ArchiveEditorSectionHeader(index = "03", english = "CULINARY USE", title = "烹饪用途与预处理")
        EditorNotesField("烹饪用途 / 预处理说明", state.prepNotes, onPrepNotesChange)

        ArchiveEditorSectionHeader(index = "04", english = "PROFESSIONAL NOTES", title = "专业信息")
        EditorTextField("时令", state.seasonality, onSeasonalityChange)
        EditorTextField("产地", state.origin, onOriginChange)
        EditorNotesField("挑选标准", state.selectionNotes, onSelectionNotesChange)
        EditorNotesField("储存建议", state.storageNotes, onStorageNotesChange)
        EditorNotesField("过敏原提示", state.allergenNotes, onAllergenNotesChange)
    }
}

@Composable
private fun EditorTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        ArchiveEditorInlineField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "填写$label",
            modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs),
        )
        CulinaryRule()
    }
}

@Composable
private fun EditorNotesField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        ArchiveEditorNotesBox(
            value = value,
            onValueChange = onValueChange,
            placeholder = "填写$label",
        )
    }
}

@Composable
private fun IngredientClassificationSelector(
    state: IngredientEditorUiState,
    onCategorySelect: (Int, String?) -> Unit,
    onLegacyCategoryChange: (String) -> Unit,
) {
    val formal = state.categories.filter { !it.isTest }
    Column(modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md)) {
        Text(
            text = "CLASSIFICATION / 分类归属",
            style = CulinaryTextStyles.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        if (formal.isEmpty()) {
            EditorTextField(
                label = "分类（可选）",
                value = state.category,
                onValueChange = onLegacyCategoryChange,
            )
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
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = true }
                    .padding(vertical = CulinarySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected?.name ?: "选择分类",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
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
                    imageVector = com.culinary.core.designsystem.icon.CulinaryIcons.ChevronDown,
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

@Composable
private fun UnitSelectionRow(
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
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
            imageVector = com.culinary.core.designsystem.icon.CulinaryIcons.ChevronDown,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
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
        title = { Text(stringResource(R.string.ingredients_unit_picker_title)) },
        text = {
            if (units.isEmpty()) {
                Text(stringResource(R.string.ingredients_picker_empty))
            } else {
                Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    units.forEach { unit ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth().clickable { onSelect(unit) }
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
                                    imageVector = com.culinary.core.designsystem.icon.CulinaryIcons.Check,
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ingredients_cancel)) }
        },
    )
}
