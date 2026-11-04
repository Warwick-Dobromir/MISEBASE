package com.culinary.feature.recipes

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.RecipeCategory
import com.culinary.core.domain.model.RecipeComponentKind
import com.culinary.core.domain.usecase.recipe.RecipeOption
import com.culinary.core.domain.usecase.sauce.SauceOption
import com.culinary.core.domain.model.Unit as UnitModel

/** 菜谱编辑器路由：档案信息、基准份数、组成行（食材/子菜谱），保存草稿或发布。 */
@Composable
fun RecipeEditorRoute(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf(EditorPicker.NONE) }
    val saved = state.savedRecipeId

    LaunchedEffect(saved) {
        if (saved != null) onSaved()
    }

    RecipeEditorScreen(
        state = state,
        onNameChange = viewModel::onNameChange,
        onOpenCategoryPicker = { picker = EditorPicker.CATEGORY },
        onYieldAmountChange = viewModel::onYieldAmountChange,
        onOpenUnitPicker = { picker = EditorPicker.UNIT },
        onServingsChange = viewModel::onServingsChange,
        onAddIngredient = { picker = EditorPicker.INGREDIENT },
        onAddRecipe = { picker = EditorPicker.RECIPE },
        onAddSauce = { picker = EditorPicker.SAUCE },
        onLineAmountChange = viewModel::onLineAmountChange,
        onToggleOptional = viewModel::toggleOptional,
        onRemoveLine = viewModel::removeLine,
        onSaveDraft = { viewModel.save(publish = false) },
        onSaveAndPublish = { viewModel.save(publish = true) },
        onDismissError = viewModel::clearError,
        modifier = modifier,
    )

    RecipeEditorDialogs(
        state = state,
        active = picker,
        onSelectUnit = {
            viewModel.onYieldUnitChange(it)
            picker = EditorPicker.NONE
        },
        onSelectCategory = {
            viewModel.onCategoryChange(it)
            picker = EditorPicker.NONE
        },
        onSelectIngredient = {
            viewModel.addIngredient(it)
            picker = EditorPicker.NONE
        },
        onSelectOption = {
            viewModel.addRecipeOption(it)
            picker = EditorPicker.NONE
        },
        onSelectSauce = {
            viewModel.addSauceOption(it)
            picker = EditorPicker.NONE
        },
        onDismiss = { picker = EditorPicker.NONE },
    )
}

private enum class EditorPicker {
    NONE,
    UNIT,
    CATEGORY,
    INGREDIENT,
    RECIPE,
    SAUCE,
}

@Composable
private fun RecipeEditorDialogs(
    state: RecipeEditorUiState,
    active: EditorPicker,
    onSelectUnit: (UnitModel) -> Unit,
    onSelectCategory: (RecipeCategory) -> Unit,
    onSelectIngredient: (Ingredient) -> Unit,
    onSelectOption: (RecipeOption) -> Unit,
    onSelectSauce: (SauceOption) -> Unit,
    onDismiss: () -> Unit,
) {
    when (active) {
        EditorPicker.NONE -> Unit
        EditorPicker.UNIT ->
            UnitPickerDialog(
                units = state.units,
                selectedId = state.yieldUnit?.id?.value,
                onSelect = onSelectUnit,
                onDismiss = onDismiss,
            )

        EditorPicker.CATEGORY ->
            CategoryPickerDialog(
                selected = state.category,
                onSelect = onSelectCategory,
                onDismiss = onDismiss,
            )

        EditorPicker.INGREDIENT ->
            IngredientPickerDialog(
                ingredients = state.ingredients,
                onSelect = onSelectIngredient,
                onDismiss = onDismiss,
            )

        EditorPicker.RECIPE ->
            RecipePickerDialog(
                options = state.recipeOptions,
                onSelect = onSelectOption,
                onDismiss = onDismiss,
            )

        EditorPicker.SAUCE ->
            SaucePickerDialog(
                options = state.sauceOptions,
                onSelect = onSelectSauce,
                onDismiss = onDismiss,
            )
    }
}

@Composable
private fun RecipeEditorScreen(
    state: RecipeEditorUiState,
    onNameChange: (String) -> Unit,
    onOpenCategoryPicker: () -> Unit,
    onYieldAmountChange: (String) -> Unit,
    onOpenUnitPicker: () -> Unit,
    onServingsChange: (String) -> Unit,
    onAddIngredient: () -> Unit,
    onAddRecipe: () -> Unit,
    onAddSauce: () -> Unit,
    onLineAmountChange: (String, String) -> Unit,
    onToggleOptional: (String) -> Unit,
    onRemoveLine: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onSaveAndPublish: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        state.error?.let { message -> ErrorBanner(message = message, onAction = onDismissError) }
        if (state.isLoading) {
            LoadingRows()
        } else {
            RecipeEditorForm(
                state = state,
                onNameChange = onNameChange,
                onOpenCategoryPicker = onOpenCategoryPicker,
                onYieldAmountChange = onYieldAmountChange,
                onOpenUnitPicker = onOpenUnitPicker,
                onServingsChange = onServingsChange,
                onAddIngredient = onAddIngredient,
                onAddRecipe = onAddRecipe,
                onAddSauce = onAddSauce,
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
private fun RecipeEditorForm(
    state: RecipeEditorUiState,
    onNameChange: (String) -> Unit,
    onOpenCategoryPicker: () -> Unit,
    onYieldAmountChange: (String) -> Unit,
    onOpenUnitPicker: () -> Unit,
    onServingsChange: (String) -> Unit,
    onAddIngredient: () -> Unit,
    onAddRecipe: () -> Unit,
    onAddSauce: () -> Unit,
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
                .padding(CulinarySpacing.md),
    ) {
        RecipeEditorIntro(state)
        RecipeBasics(
            state = state,
            onNameChange = onNameChange,
            onOpenCategoryPicker = onOpenCategoryPicker,
        )
        RecipeYieldSection(
            state = state,
            onYieldAmountChange = onYieldAmountChange,
            onOpenUnitPicker = onOpenUnitPicker,
            onServingsChange = onServingsChange,
        )
        RecipeComponentsEditor(
            state = state,
            onAddIngredient = onAddIngredient,
            onAddRecipe = onAddRecipe,
            onAddSauce = onAddSauce,
            onLineAmountChange = onLineAmountChange,
            onToggleOptional = onToggleOptional,
            onRemoveLine = onRemoveLine,
        )
        RecipeEditorFooter(
            isSaving = state.isSaving,
            onSaveDraft = onSaveDraft,
            onSaveAndPublish = onSaveAndPublish,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xl))
    }
}

@Composable
private fun RecipeEditorIntro(state: RecipeEditorUiState) {
    Text(
        text =
            if (state.isNew) {
                stringResource(R.string.recipes_editor_new_title)
            } else {
                stringResource(R.string.recipes_editor_edit_title, state.editingVersionNo ?: 1)
            },
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (state.hasPublishedVersion) {
        Text(
            text = stringResource(R.string.recipes_editor_published_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = CulinarySpacing.xs),
        )
    }
}

@Composable
private fun RecipeBasics(
    state: RecipeEditorUiState,
    onNameChange: (String) -> Unit,
    onOpenCategoryPicker: () -> Unit,
) {
    OutlinedTextField(
        value = state.name,
        onValueChange = onNameChange,
        label = { Text(text = stringResource(R.string.recipes_name_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md),
    )
    OutlinedButton(
        onClick = onOpenCategoryPicker,
        modifier = Modifier.padding(top = CulinarySpacing.sm),
    ) {
        Text(
            text =
                stringResource(R.string.recipes_category_label) +
                    "：" + recipeCategoryLabel(state.category),
        )
    }
}

@Composable
private fun RecipeYieldSection(
    state: RecipeEditorUiState,
    onYieldAmountChange: (String) -> Unit,
    onOpenUnitPicker: () -> Unit,
    onServingsChange: (String) -> Unit,
) {
    SectionHeader(title = stringResource(R.string.recipes_yield))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.yieldAmount,
            onValueChange = onYieldAmountChange,
            label = { Text(text = stringResource(R.string.recipes_yield_amount)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(CulinarySpacing.xs))
        OutlinedButton(onClick = onOpenUnitPicker) {
            Text(text = state.yieldUnit?.symbol ?: "—")
        }
    }
    OutlinedTextField(
        value = state.servings,
        onValueChange = onServingsChange,
        label = { Text(text = stringResource(R.string.recipes_servings_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(160.dp).padding(top = CulinarySpacing.sm),
    )
}

@Composable
private fun RecipeComponentsEditor(
    state: RecipeEditorUiState,
    onAddIngredient: () -> Unit,
    onAddRecipe: () -> Unit,
    onAddSauce: () -> Unit,
    onLineAmountChange: (String, String) -> Unit,
    onToggleOptional: (String) -> Unit,
    onRemoveLine: (String) -> Unit,
) {
    SectionHeader(title = stringResource(R.string.recipes_components, state.lines.size))
    state.lines.forEach { line ->
        RecipeLineRowEditor(
            line = line,
            onAmountChange = { onLineAmountChange(line.key, it) },
            onToggleOptional = { onToggleOptional(line.key) },
            onRemove = { onRemoveLine(line.key) },
        )
    }
    if (state.lines.isEmpty()) {
        Text(
            text = stringResource(R.string.recipes_no_components),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(
        modifier = Modifier.padding(top = CulinarySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs),
    ) {
        OutlinedButton(onClick = onAddIngredient) {
            Text(text = stringResource(R.string.recipes_add_ingredient))
        }
        OutlinedButton(onClick = onAddRecipe) {
            Text(text = stringResource(R.string.recipes_add_recipe))
        }
        OutlinedButton(onClick = onAddSauce) {
            Text(text = stringResource(R.string.recipes_add_sauce))
        }
    }
}

@Composable
private fun RecipeLineRowEditor(
    line: RecipeLineEditor,
    onAmountChange: (String) -> Unit,
    onToggleOptional: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = recipeLineKindLabel(line),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = line.amount,
                onValueChange = onAmountChange,
                label = { Text(text = stringResource(R.string.recipes_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = { Text(text = line.unit.symbol) },
                modifier = Modifier.width(132.dp),
            )
            TextButton(onClick = onRemove) {
                Text(text = stringResource(R.string.recipes_remove_line))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(
                text =
                    stringResource(
                        if (line.isOptional) R.string.recipes_optional_on else R.string.recipes_optional_off,
                    ),
                tone = if (line.isOptional) StatusTone.Warning else StatusTone.Neutral,
            )
            TextButton(onClick = onToggleOptional) {
                Text(text = stringResource(R.string.recipes_optional))
            }
        }
    }
}

@Composable
private fun recipeLineKindLabel(line: RecipeLineEditor): String =
    when (line.kind) {
        RecipeComponentKind.INGREDIENT -> stringResource(R.string.recipes_kind_ingredient)
        RecipeComponentKind.RECIPE ->
            stringResource(R.string.recipes_kind_recipe, line.recipeVersionNo ?: 0)
        RecipeComponentKind.SAUCE ->
            stringResource(R.string.recipes_kind_sauce, line.sauceVersionNo ?: 0)
    }

@Composable
private fun RecipeEditorFooter(
    isSaving: Boolean,
    onSaveDraft: () -> Unit,
    onSaveAndPublish: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm),
    ) {
        OutlinedButton(onClick = onSaveDraft, enabled = !isSaving, modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.recipes_save_draft))
        }
        Button(onClick = onSaveAndPublish, enabled = !isSaving, modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.recipes_save_publish))
        }
    }
}

@Composable
private fun UnitPickerDialog(
    units: List<UnitModel>,
    selectedId: String?,
    onSelect: (UnitModel) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.recipes_unit_picker_title)) },
        text = {
            PickerColumn(items = units.map { "${it.name}（${it.symbol}）" to (it.id.value == selectedId) }) {
                onSelect(units[it])
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.recipes_cancel)) }
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
        title = { Text(text = stringResource(R.string.recipes_ingredient_picker_title)) },
        text = {
            PickerColumn(items = ingredients.map { it.name to false }) { onSelect(ingredients[it]) }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.recipes_cancel)) }
        },
    )
}

@Composable
private fun RecipePickerDialog(
    options: List<RecipeOption>,
    onSelect: (RecipeOption) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.recipes_recipe_picker_title)) },
        text = {
            PickerColumn(
                items =
                    options.map { option ->
                        val version = option.currentVersion
                        val label =
                            if (version == null) {
                                option.recipe.name + stringResource(R.string.recipes_recipe_no_version)
                            } else {
                                option.recipe.name + " · v" + version.versionNo
                            }
                        label to false
                    },
            ) { onSelect(options[it]) }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.recipes_cancel)) }
        },
    )
}

@Composable
private fun SaucePickerDialog(
    options: List<SauceOption>,
    onSelect: (SauceOption) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.recipes_sauce_picker_title)) },
        text = {
            PickerColumn(
                items =
                    options.map { option ->
                        val version = option.currentVersion
                        val label =
                            if (version == null) {
                                option.sauce.name + stringResource(R.string.recipes_recipe_no_version)
                            } else {
                                option.sauce.name + " · v" + version.versionNo
                            }
                        label to false
                    },
            ) { onSelect(options[it]) }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.recipes_cancel)) }
        },
    )
}

@Composable
private fun CategoryPickerDialog(
    selected: RecipeCategory,
    onSelect: (RecipeCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.recipes_category_picker_title)) },
        text = {
            val categories = RecipeCategory.entries
            PickerColumn(items = categories.map { recipeCategoryLabel(it) to (it == selected) }) {
                onSelect(categories[it])
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.recipes_cancel)) }
        },
    )
}

/** 简单选择列：空列表给出明确文案，条目点击回调索引。 */
@Composable
private fun PickerColumn(
    items: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
) {
    if (items.isEmpty()) {
        Text(text = stringResource(R.string.recipes_picker_empty))
        return
    }
    Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
        items.forEachIndexed { index, (label, selected) ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) }
                        .padding(vertical = CulinarySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(imageVector = CulinaryIcons.Check, contentDescription = null)
                }
            }
        }
    }
}
