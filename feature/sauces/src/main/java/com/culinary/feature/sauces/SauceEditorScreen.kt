package com.culinary.feature.sauces

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryButtonVariant
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.QuantityText
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.Ingredient

/** 酱汁编辑器路由：新建或编辑草稿版本，可保存草稿或保存并发布。 */
@Composable
fun SauceEditorRoute(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SauceEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showUnitPicker by remember { mutableStateOf(false) }
    var showIngredientPicker by remember { mutableStateOf(false) }
    val saved = state.savedSauceId

    LaunchedEffect(saved) {
        if (saved != null) onSaved()
    }

    SauceEditorScreen(
        state = state,
        onNameChange = viewModel::onNameChange,
        onFamilyChange = viewModel::onFamilyChange,
        onYieldAmountChange = viewModel::onYieldAmountChange,
        onOpenUnitPicker = { showUnitPicker = true },
        onAddIngredient = { showIngredientPicker = true },
        onLineAmountChange = viewModel::onLineAmountChange,
        onToggleOptional = viewModel::toggleOptional,
        onRemoveLine = viewModel::removeLine,
        onSaveDraft = { viewModel.save(publish = false) },
        onSaveAndPublish = { viewModel.save(publish = true) },
        onDismissError = viewModel::clearError,
        modifier = modifier,
    )

    if (showUnitPicker) {
        UnitPickerDialog(
            units = state.units,
            selectedId = state.yieldUnit?.id?.value,
            onSelect = {
                viewModel.onYieldUnitChange(it)
                showUnitPicker = false
            },
            onDismiss = { showUnitPicker = false },
        )
    }
    if (showIngredientPicker) {
        IngredientPickerDialog(
            ingredients = state.ingredients,
            onSelect = {
                viewModel.addIngredient(it)
                showIngredientPicker = false
            },
            onDismiss = { showIngredientPicker = false },
        )
    }
}

@Composable
internal fun SauceEditorScreen(
    state: SauceEditorUiState,
    onNameChange: (String) -> Unit,
    onFamilyChange: (String) -> Unit,
    onYieldAmountChange: (String) -> Unit,
    onOpenUnitPicker: () -> Unit,
    onAddIngredient: () -> Unit,
    onLineAmountChange: (String, String) -> Unit,
    onToggleOptional: (String) -> Unit,
    onRemoveLine: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onSaveAndPublish: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
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
            EditorForm(
                state = state,
                onNameChange = onNameChange,
                onFamilyChange = onFamilyChange,
                onYieldAmountChange = onYieldAmountChange,
                onOpenUnitPicker = onOpenUnitPicker,
                onAddIngredient = onAddIngredient,
                onLineAmountChange = onLineAmountChange,
                onToggleOptional = onToggleOptional,
                onRemoveLine = onRemoveLine,
                onSaveDraft = onSaveDraft,
                onSaveAndPublish = onSaveAndPublish,
            )
        }
    }
}

@Composable
private fun EditorForm(
    state: SauceEditorUiState,
    onNameChange: (String) -> Unit,
    onFamilyChange: (String) -> Unit,
    onYieldAmountChange: (String) -> Unit,
    onOpenUnitPicker: () -> Unit,
    onAddIngredient: () -> Unit,
    onLineAmountChange: (String, String) -> Unit,
    onToggleOptional: (String) -> Unit,
    onRemoveLine: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onSaveAndPublish: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = CulinarySpacing.xl),
    ) {
        EditorIntro(state = state)
        Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
            EditorBasics(
                state = state,
                onNameChange = onNameChange,
                onFamilyChange = onFamilyChange,
            )
            EditorYield(
                state = state,
                onYieldAmountChange = onYieldAmountChange,
                onOpenUnitPicker = onOpenUnitPicker,
            )
            EditorComponents(
                state = state,
                onAddIngredient = onAddIngredient,
                onLineAmountChange = onLineAmountChange,
                onToggleOptional = onToggleOptional,
                onRemoveLine = onRemoveLine,
            )
            EditorFooter(
                isSaving = state.isSaving,
                onSaveDraft = onSaveDraft,
                onSaveAndPublish = onSaveAndPublish,
            )
        }
    }
}

@Composable
private fun EditorIntro(state: SauceEditorUiState) {
    PageHeader(
        eyebrow = "SAUCE",
        title =
            if (state.isNew) {
                stringResource(R.string.sauces_editor_new_title)
            } else {
                stringResource(R.string.sauces_editor_edit_title, state.editingVersionNo ?: 1)
            },
        supporting = if (state.hasPublishedVersion) stringResource(R.string.sauces_editor_published_hint) else null,
    )
}

@Composable
private fun EditorBasics(
    state: SauceEditorUiState,
    onNameChange: (String) -> Unit,
    onFamilyChange: (String) -> Unit,
) {
    CulinaryTextField(
        value = state.name,
        onValueChange = onNameChange,
        label = { Text(text = stringResource(R.string.sauces_name_label)) },
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(CulinarySpacing.field))
    CulinaryTextField(
        value = state.family,
        onValueChange = onFamilyChange,
        label = { Text(text = stringResource(R.string.sauces_family_label)) },
        singleLine = true,
    )
}

@Composable
private fun EditorYield(
    state: SauceEditorUiState,
    onYieldAmountChange: (String) -> Unit,
    onOpenUnitPicker: () -> Unit,
) {
    SectionHeader(title = stringResource(R.string.sauces_yield))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CulinaryTextField(
            value = state.yieldAmount,
            onValueChange = onYieldAmountChange,
            label = { Text(text = stringResource(R.string.sauces_yield_amount)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            fillWidth = false,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(CulinarySpacing.sm))
        CulinaryButton(
            text = state.yieldUnit?.symbol ?: "—",
            onClick = onOpenUnitPicker,
            variant = CulinaryButtonVariant.Secondary,
            icon = CulinaryIcons.ChevronDown,
        )
    }
}

@Composable
private fun EditorComponents(
    state: SauceEditorUiState,
    onAddIngredient: () -> Unit,
    onLineAmountChange: (String, String) -> Unit,
    onToggleOptional: (String) -> Unit,
    onRemoveLine: (String) -> Unit,
) {
    SectionHeader(title = stringResource(R.string.sauces_components, state.lines.size))
    if (state.lines.any { it.isReadOnly }) {
        Text(
            text = stringResource(R.string.sauces_locked_components_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = CulinarySpacing.sm),
        )
    }
    state.lines.forEach { line ->
        SauceLineRowEditor(
            line = line,
            onAmountChange = { value -> onLineAmountChange(line.key, value) },
            onToggleOptional = { onToggleOptional(line.key) },
            onRemove = { onRemoveLine(line.key) },
        )
    }
    if (state.lines.isEmpty()) {
        Text(
            text = stringResource(R.string.sauces_no_components),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    CulinaryButton(
        text = stringResource(R.string.sauces_add_ingredient),
        onClick = onAddIngredient,
        variant = CulinaryButtonVariant.Secondary,
        icon = CulinaryIcons.Add,
        modifier = Modifier.padding(top = CulinarySpacing.xs),
    )
}

@Composable
private fun EditorFooter(
    isSaving: Boolean,
    onSaveDraft: () -> Unit,
    onSaveAndPublish: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.block),
        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm),
    ) {
        CulinaryButton(
            text = stringResource(R.string.sauces_save_draft),
            onClick = onSaveDraft,
            variant = CulinaryButtonVariant.Secondary,
            enabled = !isSaving,
            modifier = Modifier.weight(1f),
        )
        CulinaryButton(
            text = stringResource(R.string.sauces_save_publish),
            onClick = onSaveAndPublish,
            enabled = !isSaving,
            loading = isSaving,
            modifier = Modifier.weight(1f),
        )
    }
}

@Suppress("LongMethod") // 行编辑器的只读/可编辑两态共享同一行布局。
@Composable
private fun SauceLineRowEditor(
    line: SauceLineEditor,
    onAmountChange: (String) -> Unit,
    onToggleOptional: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = line.kindLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (line.isReadOnly) {
                QuantityText(
                    amount = line.amount,
                    unit = line.unit.symbol,
                    modifier = Modifier.padding(end = CulinarySpacing.xs),
                )
                StatusBadge(
                    text = stringResource(R.string.sauces_locked_component),
                    tone = StatusTone.Neutral,
                )
            } else {
                CulinaryTextField(
                    value = line.amount,
                    onValueChange = onAmountChange,
                    label = { Text(text = stringResource(R.string.sauces_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    trailingIcon = { Text(text = line.unit.symbol) },
                    fillWidth = false,
                    modifier = Modifier.width(132.dp),
                )
                CulinaryTextButton(
                    text = stringResource(R.string.sauces_remove_line),
                    onClick = onRemove,
                )
            }
        }
        if (!line.isReadOnly) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(
                    text =
                        stringResource(
                            if (line.isOptional) R.string.sauces_optional_on else R.string.sauces_optional_off,
                        ),
                    tone = if (line.isOptional) StatusTone.Warning else StatusTone.Neutral,
                )
                CulinaryTextButton(
                    text = stringResource(R.string.sauces_optional),
                    onClick = onToggleOptional,
                )
            }
        }
        CulinaryRule()
    }
}

@Composable
private fun UnitPickerDialog(
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
                text = stringResource(R.string.sauces_unit_picker_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
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
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    CulinaryRule()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.sauces_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun IngredientPickerDialog(
    ingredients: List<Ingredient>,
    onSelect: (Ingredient) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.sauces_ingredient_picker_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            if (ingredients.isEmpty()) {
                Text(text = stringResource(R.string.sauces_picker_empty))
            } else {
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    ingredients.forEach { ingredient ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(ingredient) }
                                    .padding(vertical = CulinarySpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = ingredient.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        CulinaryRule()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.sauces_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
