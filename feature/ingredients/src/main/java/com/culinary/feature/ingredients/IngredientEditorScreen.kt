package com.culinary.feature.ingredients

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles
import com.culinary.core.domain.model.ArchiveCategory

/** 食材编辑器路由：名称、分类、默认单位、描述。 */
@Composable
fun IngredientEditorRoute(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IngredientEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showUnitPicker by remember { mutableStateOf(false) }
    val pickPrimaryImage = rememberIngredientImagePicker(viewModel::onPrimaryImagePicked)
    val pickReferenceImage = rememberIngredientImagePicker(viewModel::onReferenceImagePicked)
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
                onPickPrimaryImage = pickPrimaryImage,
                onRemovePrimaryImage = viewModel::onPrimaryImageRemoved,
                onPickReferenceImage = pickReferenceImage,
                onRemoveReferenceImage = viewModel::onReferenceImageRemoved,
                onSave = viewModel::save,
                onCancel = onSaved,
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
    onScientificNameChange: (String) -> Unit,
    onFlavorProfileChange: (String) -> Unit,
    onSeasonalityChange: (String) -> Unit,
    onOriginChange: (String) -> Unit,
    onSelectionNotesChange: (String) -> Unit,
    onPrepNotesChange: (String) -> Unit,
    onExampleProductsChange: (String) -> Unit,
    onStorageNotesChange: (String) -> Unit,
    onAllergenNotesChange: (String) -> Unit,
    onOpenUnitPicker: () -> Unit,
    onPickPrimaryImage: () -> Unit,
    onRemovePrimaryImage: () -> Unit,
    onPickReferenceImage: () -> Unit,
    onRemoveReferenceImage: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = CulinarySpacing.xl),
    ) {
        PageHeader(
            eyebrow = "INGREDIENT",
            title =
                stringResource(
                    if (state.isNew) {
                        R.string.ingredients_editor_new_title
                    } else {
                        R.string.ingredients_editor_edit_title
                    },
                ),
        )
        Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
            SectionHeader(title = "01  基本信息")
            CulinaryTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(text = stringResource(R.string.ingredients_name_label)) },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.field))
            IngredientClassificationSelector(
                state = state,
                onCategorySelect = onCategorySelect,
                onLegacyCategoryChange = onCategoryChange,
            )
            SectionHeader(title = "02  图片资料")
            state.primaryImageBytes?.let { bytes ->
                val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                bitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = "食材主图",
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.lg)) {
                CulinaryTextButton(
                    text = if (state.primaryImageBytes == null) "添加主图" else "更换主图",
                    onClick = onPickPrimaryImage,
                )
                if (state.primaryImageBytes != null) {
                    CulinaryTextButton(text = "删除主图", onClick = onRemovePrimaryImage, danger = true)
                }
            }
            SectionHeader(title = "补充图片")
            state.referenceImages.forEachIndexed { index, image ->
                val bitmap = remember(image.bytes) { BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)?.asImageBitmap() }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm),
                ) {
                    bitmap?.let {
                        Image(
                            bitmap = it,
                            contentDescription = "食材补充图片",
                            modifier = Modifier.size(72.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    CulinaryTextButton(text = "删除", onClick = { onRemoveReferenceImage(index) }, danger = true)
                }
            }
            CulinaryTextButton(text = "添加补充图片", onClick = onPickReferenceImage)
            SectionHeader(title = "03  计量信息")
            SectionHeader(title = stringResource(R.string.ingredients_default_unit))
            UnitSelectionRow(
                value =
                    state.defaultUnit?.let { "${it.name}（${it.symbol}）" }
                        ?: stringResource(R.string.ingredients_no_unit),
                onClick = onOpenUnitPicker,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.field))
            CulinaryTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = { Text(text = stringResource(R.string.ingredients_description_label)) },
                minLines = 3,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.field))
            CulinaryTextField(
                value = state.englishName,
                onValueChange = onEnglishNameChange,
                label = { Text(text = stringResource(R.string.ingredients_english_name)) },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.field))
            CulinaryTextField(
                value = state.scientificName,
                onValueChange = onScientificNameChange,
                label = { Text("学名") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.field))
            SectionHeader(title = "04  风味与特性")
            CulinaryTextField(
                value = state.flavorProfile,
                onValueChange = onFlavorProfileChange,
                label = { Text("风味与特性") },
                minLines = 3,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.field))
            CulinaryTextField(
                value = state.seasonality,
                onValueChange = onSeasonalityChange,
                label = { Text("季节性 / 时令") },
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.field))
            CulinaryTextField(
                value = state.origin,
                onValueChange = onOriginChange,
                label = { Text("产地") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.field))
            CulinaryTextField(
                value = state.selectionNotes,
                onValueChange = onSelectionNotesChange,
                label = { Text("挑选建议") },
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.field))
            CulinaryTextField(
                value = state.prepNotes,
                onValueChange = onPrepNotesChange,
                label = { Text("预处理方法") },
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.field))
            SectionHeader(title = "05  烹饪用途")
            CulinaryTextField(
                value = state.exampleProducts,
                onValueChange = onExampleProductsChange,
                label = { Text(text = stringResource(R.string.ingredients_example_products)) },
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.field))
            SectionHeader(title = "06  其他信息")
            CulinaryTextField(
                value = state.storageNotes,
                onValueChange = onStorageNotesChange,
                label = { Text(text = stringResource(R.string.ingredients_storage)) },
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.field))
            CulinaryTextField(
                value = state.allergenNotes,
                onValueChange = onAllergenNotesChange,
                label = { Text(text = stringResource(R.string.ingredients_allergen)) },
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CulinaryTextButton(
                    text = stringResource(R.string.ingredients_cancel),
                    onClick = onCancel,
                )
                CulinaryTextButton(
                    text = stringResource(if (state.isSaving) R.string.ingredients_saving else R.string.ingredients_save),
                    onClick = onSave,
                )
            }
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
