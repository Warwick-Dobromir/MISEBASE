package com.culinary.feature.ingredients

import androidx.compose.foundation.clickable
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
import com.culinary.core.designsystem.component.ArchiveEditorImageBox
import com.culinary.core.designsystem.component.ArchiveEditorInlineField
import com.culinary.core.designsystem.component.ArchiveEditorSectionHeader
import com.culinary.core.designsystem.component.ArchiveEditorTopBar
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextField
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
    modifier: Modifier = Modifier,
    viewModel: IngredientEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showUnitPicker by remember { mutableStateOf(false) }
    val saved = state.saved

    LaunchedEffect(saved) {
        if (saved) onSaved()
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
                onExampleProductsChange = viewModel::onExampleProductsChange,
                onStorageNotesChange = viewModel::onStorageNotesChange,
                onAllergenNotesChange = viewModel::onAllergenNotesChange,
                onOpenUnitPicker = { showUnitPicker = true },
                onSave = viewModel::save,
                onImagePicked = viewModel::onImagePicked,
                onImageRemoved = viewModel::onImageRemoved,
                onCancelled = onCancelled,
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

@Suppress("LongMethod") // 单个表单页共享同一份编辑状态，拆分会重复回调参数。
@Composable
internal fun IngredientEditorForm(
    state: IngredientEditorUiState,
    onNameChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onCategorySelect: (Int, String?) -> Unit = { _, _ -> },
    onDescriptionChange: (String) -> Unit,
    onEnglishNameChange: (String) -> Unit,
    onExampleProductsChange: (String) -> Unit,
    onStorageNotesChange: (String) -> Unit,
    onAllergenNotesChange: (String) -> Unit,
    onOpenUnitPicker: () -> Unit,
    onSave: () -> Unit,
    onImagePicked: (PendingIngredientImage) -> Unit,
    onImageRemoved: () -> Unit,
    onCancelled: () -> Unit,
) {
    val pickImage = rememberIngredientImagePicker(onImagePicked)
    Column(modifier = Modifier.fillMaxSize()) {
        ArchiveEditorTopBar(
            title = if (state.isNew) "新建食材" else "编辑食材",
            onBack = onCancelled,
            onCancel = onCancelled,
            onSave = onSave,
            isSaving = state.isSaving,
            canSave = !state.isLoading && !state.isSaving,
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = CulinarySpacing.pageHorizontal)
                .padding(bottom = CulinarySpacing.xl),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.lg)) {
                Text(
                    text = state.category.ifBlank { "INGREDIENT · 食材档案" },
                    style = CulinaryTextStyles.eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                ArchiveEditorInlineField(
                    value = state.englishName,
                    onValueChange = onEnglishNameChange,
                    placeholder = "Ingredient name",
                    textStyle = CulinaryTextStyles.pageTitle,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
                ArchiveEditorInlineField(
                    value = state.name,
                    onValueChange = onNameChange,
                    placeholder = "食材名称",
                    textStyle = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                ArchiveEditorInlineField(
                    value = state.description,
                    onValueChange = onDescriptionChange,
                    placeholder = "描述食材特征与用途……",
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.lg))
                IngredientClassificationSelector(
                    state = state,
                    onCategorySelect = onCategorySelect,
                    onLegacyCategoryChange = onCategoryChange,
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.xl)) {
                Text(
                    text = "HERO MEDIA",
                    style = CulinaryTextStyles.eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.xs))
                ArchiveEditorImageBox(
                    imageBytes = state.pendingImage?.bytes ?: state.imageBytes,
                    onPick = pickImage,
                    onClear = if (state.pendingImage != null || state.imageId != null) onImageRemoved else null,
                    emptyLabel = "添加食材图片",
                    replaceLabel = "替换",
                    deleteLabel = "删除",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ArchiveEditorSectionHeader(index = "01", english = "DEFAULT UNIT", title = "默认单位")
            UnitSelectionRow(
                value = state.defaultUnit?.let { "${it.name}（${it.symbol}）" }
                    ?: stringResource(R.string.ingredients_no_unit),
                onClick = onOpenUnitPicker,
            )
            ArchiveEditorSectionHeader(index = "02", english = "PRODUCTS / SPECS", title = "示例商品 / 规格")
            ArchiveEditorInlineField(
                value = state.exampleProducts,
                onValueChange = onExampleProductsChange,
                placeholder = "例如：全脂牛奶 1L；全脂牛奶 250ml",
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            ArchiveEditorSectionHeader(index = "03", english = "STORAGE", title = "储存条件")
            ArchiveEditorInlineField(
                value = state.storageNotes,
                onValueChange = onStorageNotesChange,
                placeholder = "例如：冷藏 0–4°C",
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            ArchiveEditorSectionHeader(index = "04", english = "ALLERGENS", title = "过敏原提示")
            ArchiveEditorInlineField(
                value = state.allergenNotes,
                onValueChange = onAllergenNotesChange,
                placeholder = "填写过敏原信息",
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            CulinaryRule()
            Spacer(modifier = Modifier.height(CulinarySpacing.md))
            Text(
                text = if (state.isNew) "新建食材档案" else "编辑食材档案",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.xl))
        }
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
            CulinaryTextField(
                value = state.category,
                onValueChange = onLegacyCategoryChange,
                label = { Text(text = stringResource(R.string.ingredients_category_label)) },
                singleLine = true,
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
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
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
