package com.culinary.feature.ingredients

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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
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
            ErrorBanner(message = message, onAction = viewModel::clearError)
        }
        if (state.isLoading) {
            LoadingRows()
        } else {
            IngredientEditorForm(
                state = state,
                onNameChange = viewModel::onNameChange,
                onCategoryChange = viewModel::onCategoryChange,
                onDescriptionChange = viewModel::onDescriptionChange,
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

@Composable
private fun IngredientEditorForm(
    state: IngredientEditorUiState,
    onNameChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onOpenUnitPicker: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(CulinarySpacing.md),
    ) {
        IngredientIdentityFields(
            state = state,
            onNameChange = onNameChange,
            onCategoryChange = onCategoryChange,
        )
        IngredientDetailsFields(
            state = state,
            onDescriptionChange = onDescriptionChange,
            onOpenUnitPicker = onOpenUnitPicker,
            onSave = onSave,
        )
    }
}

@Composable
private fun IngredientIdentityFields(
    state: IngredientEditorUiState,
    onNameChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
) {
    Text(
        text =
            stringResource(
                if (state.isNew) {
                    R.string.ingredients_editor_new_title
                } else {
                    R.string.ingredients_editor_edit_title
                },
            ),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    OutlinedTextField(
        value = state.name,
        onValueChange = onNameChange,
        label = { Text(text = stringResource(R.string.ingredients_name_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md),
    )
    OutlinedTextField(
        value = state.category,
        onValueChange = onCategoryChange,
        label = { Text(text = stringResource(R.string.ingredients_category_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.sm),
    )
}

@Composable
private fun IngredientDetailsFields(
    state: IngredientEditorUiState,
    onDescriptionChange: (String) -> Unit,
    onOpenUnitPicker: () -> Unit,
    onSave: () -> Unit,
) {
    SectionHeader(title = stringResource(R.string.ingredients_default_unit))
    OutlinedButton(onClick = onOpenUnitPicker) {
        Text(
            text =
                state.defaultUnit?.let { "${it.name}（${it.symbol}）" }
                    ?: stringResource(R.string.ingredients_no_unit),
        )
    }
    OutlinedTextField(
        value = state.description,
        onValueChange = onDescriptionChange,
        label = { Text(text = stringResource(R.string.ingredients_description_label)) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.sm),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.lg),
        horizontalArrangement = Arrangement.End,
    ) {
        Button(onClick = onSave, enabled = !state.isSaving) {
            Text(
                text =
                    if (state.isSaving) {
                        stringResource(R.string.ingredients_saving)
                    } else {
                        stringResource(R.string.ingredients_save)
                    },
            )
        }
    }
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
        title = { Text(text = stringResource(R.string.ingredients_unit_picker_title)) },
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
                                modifier = Modifier.weight(1f),
                            )
                            if (unit.id.value == selectedId) {
                                Icon(imageVector = CulinaryIcons.Check, contentDescription = null)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.ingredients_cancel))
            }
        },
    )
}
