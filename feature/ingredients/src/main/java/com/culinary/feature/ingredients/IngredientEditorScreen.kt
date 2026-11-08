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
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing

/** 食材编辑器路由：名称、分类、默认单位、描述。 */
@Composable
fun IngredientEditorRoute(
    onSaved: () -> Unit,
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
                onDescriptionChange = viewModel::onDescriptionChange,
                onEnglishNameChange = viewModel::onEnglishNameChange,
                onExampleProductsChange = viewModel::onExampleProductsChange,
                onStorageNotesChange = viewModel::onStorageNotesChange,
                onAllergenNotesChange = viewModel::onAllergenNotesChange,
                onOpenUnitPicker = { showUnitPicker = true },
                onSave = viewModel::save,
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
    onDescriptionChange: (String) -> Unit,
    onEnglishNameChange: (String) -> Unit,
    onExampleProductsChange: (String) -> Unit,
    onStorageNotesChange: (String) -> Unit,
    onAllergenNotesChange: (String) -> Unit,
    onOpenUnitPicker: () -> Unit,
    onSave: () -> Unit,
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
            CulinaryTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(text = stringResource(R.string.ingredients_name_label)) },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.field))
            CulinaryTextField(
                value = state.category,
                onValueChange = onCategoryChange,
                label = { Text(text = stringResource(R.string.ingredients_category_label)) },
                singleLine = true,
            )
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
                value = state.exampleProducts,
                onValueChange = onExampleProductsChange,
                label = { Text(text = stringResource(R.string.ingredients_example_products)) },
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.field))
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
            CulinaryButton(
                text =
                    stringResource(
                        if (state.isSaving) {
                            R.string.ingredients_saving
                        } else {
                            R.string.ingredients_save
                        },
                    ),
                onClick = onSave,
                enabled = !state.isSaving,
                loading = state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
