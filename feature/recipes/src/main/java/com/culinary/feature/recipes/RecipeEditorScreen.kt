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
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.component.formatArchiveTimestamp
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
        onEnglishNameChange = viewModel::onEnglishNameChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onOpenCapacityPicker = { picker = EditorPicker.CAPACITY },
        onCuisineChange = viewModel::onCuisineChange,
        onPrepMinutesChange = viewModel::onPrepMinutesChange,
        onCookMinutesChange = viewModel::onCookMinutesChange,
        onAllergenNotesChange = viewModel::onAllergenNotesChange,
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
        onAddStep = viewModel::addStep,
        onStepTitleChange = viewModel::onStepTitleChange,
        onStepBodyChange = viewModel::onStepBodyChange,
        onStepTimeChange = viewModel::onStepTimeChange,
        onRemoveStep = viewModel::removeStep,
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
        onSelectCapacity = {
            viewModel.onMinimumCapacityChange(it)
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
    CAPACITY,
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
    onSelectCapacity: (String) -> Unit,
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

        EditorPicker.CAPACITY ->
            CapacityPickerDialog(
                selected = state.minimumCapacity,
                onSelect = onSelectCapacity,
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
    onEnglishNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onOpenCapacityPicker: () -> Unit,
    onCuisineChange: (String) -> Unit,
    onPrepMinutesChange: (String) -> Unit,
    onCookMinutesChange: (String) -> Unit,
    onAllergenNotesChange: (String) -> Unit,
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
    onAddStep: () -> Unit,
    onStepTitleChange: (String, String) -> Unit,
    onStepBodyChange: (String, String) -> Unit,
    onStepTimeChange: (String, Int) -> Unit,
    onRemoveStep: (String) -> Unit,
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
            RecipeEditorForm(
                state = state,
                onNameChange = onNameChange,
                onEnglishNameChange = onEnglishNameChange,
                onDescriptionChange = onDescriptionChange,
                onOpenCapacityPicker = onOpenCapacityPicker,
                onCuisineChange = onCuisineChange,
                onPrepMinutesChange = onPrepMinutesChange,
                onCookMinutesChange = onCookMinutesChange,
                onAllergenNotesChange = onAllergenNotesChange,
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
                onAddStep = onAddStep,
                onStepTitleChange = onStepTitleChange,
                onStepBodyChange = onStepBodyChange,
                onStepTimeChange = onStepTimeChange,
                onRemoveStep = onRemoveStep,
                onSaveDraft = onSaveDraft,
                onSaveAndPublish = onSaveAndPublish,
            )
        }
    }
}

@Composable
internal fun RecipeEditorForm(
    state: RecipeEditorUiState,
    onNameChange: (String) -> Unit,
    onOpenCategoryPicker: () -> Unit,
    onEnglishNameChange: (String) -> Unit = {},
    onDescriptionChange: (String) -> Unit = {},
    onOpenCapacityPicker: () -> Unit = {},
    onCuisineChange: (String) -> Unit = {},
    onPrepMinutesChange: (String) -> Unit = {},
    onCookMinutesChange: (String) -> Unit = {},
    onAllergenNotesChange: (String) -> Unit = {},
    onYieldAmountChange: (String) -> Unit,
    onOpenUnitPicker: () -> Unit,
    onServingsChange: (String) -> Unit,
    onAddIngredient: () -> Unit,
    onAddRecipe: () -> Unit,
    onAddSauce: () -> Unit,
    onLineAmountChange: (String, String) -> Unit,
    onToggleOptional: (String) -> Unit,
    onRemoveLine: (String) -> Unit,
    onAddStep: () -> Unit = {},
    onStepTitleChange: (String, String) -> Unit = { _, _ -> },
    onStepBodyChange: (String, String) -> Unit = { _, _ -> },
    onStepTimeChange: (String, Int) -> Unit = { _, _ -> },
    onRemoveStep: (String) -> Unit = {},
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
        RecipeEditorIntro(state)
        Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
            RecipeBasics(
                state = state,
                onNameChange = onNameChange,
                onEnglishNameChange = onEnglishNameChange,
                onDescriptionChange = onDescriptionChange,
                onOpenCapacityPicker = onOpenCapacityPicker,
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
            RecipeMethodEditor(
                state = state,
                onAddStep = onAddStep,
                onStepTitleChange = onStepTitleChange,
                onStepBodyChange = onStepBodyChange,
                onStepTimeChange = onStepTimeChange,
                onRemoveStep = onRemoveStep,
            )
            RecipeApplicationSection(
                state = state,
                onCuisineChange = onCuisineChange,
                onPrepMinutesChange = onPrepMinutesChange,
                onCookMinutesChange = onCookMinutesChange,
                onAllergenNotesChange = onAllergenNotesChange,
            )
            RecipeNotesSection(state)
            RecipeEditorFooter(
                state = state,
                onSaveDraft = onSaveDraft,
                onSaveAndPublish = onSaveAndPublish,
            )
        }
    }
}

@Composable
private fun RecipeEditorIntro(state: RecipeEditorUiState) {
    PageHeader(
        eyebrow = "RECIPE",
        title =
            if (state.isNew) {
                stringResource(R.string.recipes_editor_new_title)
            } else {
                stringResource(R.string.recipes_editor_edit_title, state.editingVersionNo ?: 1)
            },
        supporting =
            when {
                state.hasPublishedVersion -> stringResource(R.string.recipes_editor_published_hint)
                state.isNew -> stringResource(R.string.recipes_editor_new_hint)
                else -> null
            },
    )
}

@Composable
private fun RecipeBasics(
    state: RecipeEditorUiState,
    onNameChange: (String) -> Unit,
    onEnglishNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onOpenCapacityPicker: () -> Unit,
    onOpenCategoryPicker: () -> Unit,
) {
    CulinaryTextField(
        value = state.name,
        onValueChange = onNameChange,
        label = { Text(text = stringResource(R.string.recipes_name_label)) },
        singleLine = true,
    )
    CulinaryTextField(
        value = state.englishName,
        onValueChange = onEnglishNameChange,
        label = { Text(text = stringResource(R.string.recipes_english_name)) },
        singleLine = true,
        modifier = Modifier.padding(top = CulinarySpacing.sm),
    )
    CulinaryTextField(
        value = state.description,
        onValueChange = onDescriptionChange,
        label = { Text(text = stringResource(R.string.recipes_profile)) },
        singleLine = false,
        modifier = Modifier.padding(top = CulinarySpacing.sm),
    )
    Row(modifier = Modifier.padding(top = CulinarySpacing.sm)) {
        CulinaryButton(
            text =
                stringResource(R.string.recipes_category_label) +
                    "：" + recipeCategoryLabel(state.category),
            onClick = onOpenCategoryPicker,
            variant = CulinaryButtonVariant.Secondary,
            icon = CulinaryIcons.ChevronDown,
        )
        Spacer(modifier = Modifier.width(CulinarySpacing.sm))
        CulinaryButton(
            text =
                stringResource(R.string.recipes_minimum_capacity) +
                    "：" + recipeMinimumCapacityLabel(state.minimumCapacity),
            onClick = onOpenCapacityPicker,
            variant = CulinaryButtonVariant.Secondary,
            icon = CulinaryIcons.ChevronDown,
            modifier = Modifier.weight(1f),
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
        CulinaryTextField(
            value = state.yieldAmount,
            onValueChange = onYieldAmountChange,
            label = { Text(text = stringResource(R.string.recipes_yield_amount)) },
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
    Row(modifier = Modifier.padding(top = CulinarySpacing.md)) {
        CulinaryTextField(
            value = state.servings,
            onValueChange = onServingsChange,
            label = { Text(text = stringResource(R.string.recipes_servings_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            fillWidth = false,
            modifier = Modifier.width(160.dp),
        )
    }
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
    SectionHeader(
        title = stringResource(R.string.recipes_ingredients_title),
        eyebrow = "01 / INGREDIENTS",
        subtitle = stringResource(R.string.recipes_components, state.lines.size),
    )
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
        CulinaryButton(
            text = stringResource(R.string.recipes_add_ingredient),
            onClick = onAddIngredient,
            variant = CulinaryButtonVariant.Secondary,
            icon = CulinaryIcons.Add,
        )
        CulinaryButton(
            text = stringResource(R.string.recipes_add_recipe),
            onClick = onAddRecipe,
            variant = CulinaryButtonVariant.Secondary,
            icon = CulinaryIcons.Add,
        )
    }
    CulinaryButton(
        text = stringResource(R.string.recipes_add_sauce),
        onClick = onAddSauce,
        variant = CulinaryButtonVariant.Secondary,
        icon = CulinaryIcons.Add,
        modifier = Modifier.padding(top = CulinarySpacing.xs),
    )
}

@Composable
private fun RecipeLineRowEditor(
    line: RecipeLineEditor,
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
                    text = recipeLineKindLabel(line),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CulinaryTextField(
                value = line.amount,
                onValueChange = onAmountChange,
                label = { Text(text = stringResource(R.string.recipes_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = { Text(text = line.unit.symbol) },
                fillWidth = false,
                modifier = Modifier.width(132.dp),
            )
            CulinaryTextButton(
                text = stringResource(R.string.recipes_remove_line),
                onClick = onRemove,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(
                text =
                    stringResource(
                        if (line.isOptional) R.string.recipes_optional_on else R.string.recipes_optional_off,
                    ),
                tone = if (line.isOptional) StatusTone.Warning else StatusTone.Neutral,
            )
            CulinaryTextButton(
                text = stringResource(R.string.recipes_optional),
                onClick = onToggleOptional,
            )
        }
        CulinaryRule()
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
private fun RecipeApplicationSection(
    state: RecipeEditorUiState,
    onCuisineChange: (String) -> Unit,
    onPrepMinutesChange: (String) -> Unit,
    onCookMinutesChange: (String) -> Unit,
    onAllergenNotesChange: (String) -> Unit,
) {
    SectionHeader(
        title = stringResource(R.string.recipes_application),
        eyebrow = "03 / APPLICATION",
    )
    CulinaryTextField(
        value = state.cuisine,
        onValueChange = onCuisineChange,
        label = { Text(text = stringResource(R.string.recipes_cuisine)) },
        singleLine = true,
    )
    Row(modifier = Modifier.padding(top = CulinarySpacing.sm)) {
        CulinaryTextField(
            value = state.prepMinutes,
            onValueChange = onPrepMinutesChange,
            label = { Text(text = stringResource(R.string.recipes_prep_minutes)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            fillWidth = false,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(CulinarySpacing.sm))
        CulinaryTextField(
            value = state.cookMinutes,
            onValueChange = onCookMinutesChange,
            label = { Text(text = stringResource(R.string.recipes_cook_minutes)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            fillWidth = false,
            modifier = Modifier.weight(1f),
        )
    }
    CulinaryTextField(
        value = state.allergenNotes,
        onValueChange = onAllergenNotesChange,
        label = { Text(text = stringResource(R.string.recipes_allergen)) },
        singleLine = false,
        modifier = Modifier.padding(top = CulinarySpacing.sm),
    )
}

@Composable
private fun RecipeNotesSection(state: RecipeEditorUiState) {
    if (state.seedNote.isBlank()) return
    SectionHeader(
        title = stringResource(R.string.recipes_notes),
        eyebrow = "04 / NOTES",
    )
    Text(
        text = state.seedNote,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
}

@Composable
private fun RecipeEditorFooter(
    state: RecipeEditorUiState,
    onSaveDraft: () -> Unit,
    onSaveAndPublish: () -> Unit,
) {
    val isSaving = state.isSaving
    formatArchiveTimestamp(state.updatedAtEpochMillis)?.let { updated ->
        Text(
            text = stringResource(R.string.recipes_last_updated, updated),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = CulinarySpacing.md),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.block),
        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm),
    ) {
        CulinaryButton(
            text = stringResource(R.string.recipes_save_draft),
            onClick = onSaveDraft,
            variant = CulinaryButtonVariant.Secondary,
            enabled = !isSaving,
            modifier = Modifier.weight(1f),
        )
        CulinaryButton(
            text = stringResource(R.string.recipes_save_publish),
            onClick = onSaveAndPublish,
            enabled = !isSaving,
            loading = isSaving,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun UnitPickerDialog(
    units: List<UnitModel>,
    selectedId: String?,
    onSelect: (UnitModel) -> Unit,
    onDismiss: () -> Unit,
) {
    PickerDialog(
        title = stringResource(R.string.recipes_unit_picker_title),
        items = units.map { "${it.name}（${it.symbol}）" to (it.id.value == selectedId) },
        onSelect = { index -> onSelect(units[index]) },
        onDismiss = onDismiss,
        cancelLabel = stringResource(R.string.recipes_cancel),
    )
}

@Composable
private fun IngredientPickerDialog(
    ingredients: List<Ingredient>,
    onSelect: (Ingredient) -> Unit,
    onDismiss: () -> Unit,
) {
    PickerDialog(
        title = stringResource(R.string.recipes_ingredient_picker_title),
        items = ingredients.map { it.name to false },
        onSelect = { index -> onSelect(ingredients[index]) },
        onDismiss = onDismiss,
        cancelLabel = stringResource(R.string.recipes_cancel),
    )
}

@Composable
private fun RecipePickerDialog(
    options: List<RecipeOption>,
    onSelect: (RecipeOption) -> Unit,
    onDismiss: () -> Unit,
) {
    PickerDialog(
        title = stringResource(R.string.recipes_recipe_picker_title),
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
        onSelect = { index -> onSelect(options[index]) },
        onDismiss = onDismiss,
        cancelLabel = stringResource(R.string.recipes_cancel),
    )
}

@Composable
private fun SaucePickerDialog(
    options: List<SauceOption>,
    onSelect: (SauceOption) -> Unit,
    onDismiss: () -> Unit,
) {
    PickerDialog(
        title = stringResource(R.string.recipes_sauce_picker_title),
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
        onSelect = { index -> onSelect(options[index]) },
        onDismiss = onDismiss,
        cancelLabel = stringResource(R.string.recipes_cancel),
    )
}

@Composable
private fun CapacityPickerDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options =
        listOf(
            "EASY" to "简单",
            "MEDIUM" to "中等",
            "HARD" to "困难",
        )
    val current = selected.trim().uppercase()
    PickerDialog(
        title = stringResource(R.string.recipes_minimum_capacity),
        items = options.map { (value, label) -> label to (value == current) },
        onSelect = { index -> onSelect(options[index].first) },
        onDismiss = onDismiss,
        cancelLabel = stringResource(R.string.recipes_cancel),
    )
}

@Composable
private fun CategoryPickerDialog(
    selected: RecipeCategory,
    onSelect: (RecipeCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    val categories = RecipeCategory.entries
    PickerDialog(
        title = stringResource(R.string.recipes_category_picker_title),
        items = categories.map { recipeCategoryLabel(it) to (it == selected) },
        onSelect = { index -> onSelect(categories[index]) },
        onDismiss = onDismiss,
        cancelLabel = stringResource(R.string.recipes_cancel),
    )
}

/** 统一的选择弹窗：细线列表 + 选中对勾 + 文字取消。 */
@Composable
private fun PickerDialog(
    title: String,
    items: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            if (items.isEmpty()) {
                Text(text = stringResource(R.string.recipes_picker_empty))
            } else {
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
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = cancelLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
