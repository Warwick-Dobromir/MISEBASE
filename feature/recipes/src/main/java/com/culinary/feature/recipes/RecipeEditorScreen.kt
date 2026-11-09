package com.culinary.feature.recipes

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.ArchiveEditorImageBox
import com.culinary.core.designsystem.component.ArchiveEditorInlineField
import com.culinary.core.designsystem.component.ArchiveEditorNotesBox
import com.culinary.core.designsystem.component.ArchiveEditorSectionHeader
import com.culinary.core.designsystem.component.ArchiveEditorTopBar
import com.culinary.core.designsystem.component.ArchiveProse
import com.culinary.core.designsystem.component.ArchiveYieldRangeFields
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryButtonVariant
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.component.formatArchiveTimestamp
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.ArchiveCategory
import com.culinary.core.domain.model.PendingRecipeImage
import com.culinary.core.domain.model.RecipeCategory
import com.culinary.core.domain.model.RecipeComponentKind
import com.culinary.core.domain.usecase.recipe.RecipeOption
import com.culinary.core.domain.usecase.sauce.SauceOption
import com.culinary.core.domain.model.Unit as UnitModel

/** 菜谱编辑器路由：档案信息、基准份数、组成行（食材/子菜谱），保存草稿或发布。 */
@Composable
fun RecipeEditorRoute(
    onSaved: () -> Unit,
    onCancelled: () -> Unit = onSaved,
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
        onNotesChange = viewModel::onNotesChange,
        onHeroPicked = viewModel::setHeroImage,
        onHeroClear = viewModel::clearHeroImage,
        onNoteImage = viewModel::addNoteImage,
        onRemoveNoteImage = viewModel::removeNoteImageAt,
        onCuisineChange = viewModel::onCuisineChange,
        onPrepMinutesChange = viewModel::onPrepMinutesChange,
        onCookMinutesChange = viewModel::onCookMinutesChange,
        onAllergenNotesChange = viewModel::onAllergenNotesChange,
        onOpenCategoryPicker = { picker = EditorPicker.CATEGORY },
        onOpenArchiveCategoryPicker = { picker = EditorPicker.ARCHIVE_CATEGORY },
        onYieldAmountChange = viewModel::onYieldAmountChange,
        onOpenYieldUnit = { picker = EditorPicker.YIELD_UNIT },
        onMinRangeAmountChange = viewModel::onMinRangeAmountChange,
        onOpenMinRangeUnit = { picker = EditorPicker.MIN_RANGE_UNIT },
        onServingsChange = viewModel::onServingsChange,
        onAddIngredient = { picker = EditorPicker.INGREDIENT },
        onAddRecipe = { picker = EditorPicker.RECIPE },
        onAddSauce = { picker = EditorPicker.SAUCE },
        onAddRelationSauce = { picker = EditorPicker.RELATION_SAUCE },
        onAddRelationRecipe = { picker = EditorPicker.RELATION_RECIPE },
        onRemoveRelation = viewModel::removeRelation,
        onLineAmountChange = viewModel::onLineAmountChange,
        onToggleOptional = viewModel::toggleOptional,
        onRemoveLine = viewModel::removeLine,
        onAddStep = viewModel::addStep,
        onStepTitleChange = viewModel::onStepTitleChange,
        onStepBodyChange = viewModel::onStepBodyChange,
        onStepTimeChange = viewModel::onStepTimeChange,
        onStepImage = viewModel::setStepImage,
        onClearStepImage = viewModel::clearStepImage,
        onRemoveStep = viewModel::removeStep,
        onSave = viewModel::save,
        onCancel = onCancelled,
        onDismissError = viewModel::clearError,
        modifier = modifier,
    )

    RecipeEditorDialogs(
        state = state,
        active = picker,
        onSelectYieldUnit = {
            viewModel.onYieldUnitChange(it)
            picker = EditorPicker.NONE
        },
        onSelectMinRangeUnit = {
            viewModel.onMinRangeUnitChange(it)
            picker = EditorPicker.NONE
        },
        onSelectCategory = {
            viewModel.onCategoryChange(it)
            picker = EditorPicker.NONE
        },
        onSelectArchiveCategory = {
            viewModel.onArchiveCategorySelect(it)
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
        onSelectRelationSauce = {
            viewModel.addSauceRelation(it)
            picker = EditorPicker.NONE
        },
        onSelectRelationRecipe = {
            viewModel.addRecipeRelation(it)
            picker = EditorPicker.NONE
        },
        onDismiss = { picker = EditorPicker.NONE },
    )
}

private enum class EditorPicker {
    NONE,
    YIELD_UNIT,
    MIN_RANGE_UNIT,
    CATEGORY,
    ARCHIVE_CATEGORY,
    INGREDIENT,
    RECIPE,
    SAUCE,
    RELATION_SAUCE,
    RELATION_RECIPE,
}

@Composable
private fun RecipeEditorDialogs(
    state: RecipeEditorUiState,
    active: EditorPicker,
    onSelectYieldUnit: (UnitModel) -> Unit,
    onSelectMinRangeUnit: (UnitModel) -> Unit,
    onSelectCategory: (RecipeCategory) -> Unit,
    onSelectArchiveCategory: (String) -> Unit,
    onSelectIngredient: (Ingredient) -> Unit,
    onSelectOption: (RecipeOption) -> Unit,
    onSelectSauce: (SauceOption) -> Unit,
    onSelectRelationSauce: (SauceOption) -> Unit,
    onSelectRelationRecipe: (RecipeOption) -> Unit,
    onDismiss: () -> Unit,
) {
    when (active) {
        EditorPicker.NONE -> Unit
        EditorPicker.YIELD_UNIT ->
            UnitPickerDialog(
                units = state.units,
                selectedId = state.yieldUnit?.id?.value,
                onSelect = onSelectYieldUnit,
                onDismiss = onDismiss,
            )

        EditorPicker.MIN_RANGE_UNIT ->
            UnitPickerDialog(
                units = state.units,
                selectedId = state.minRangeUnit?.id?.value,
                onSelect = onSelectMinRangeUnit,
                onDismiss = onDismiss,
            )

        EditorPicker.CATEGORY ->
            CategoryPickerDialog(
                selected = state.category,
                onSelect = onSelectCategory,
                onDismiss = onDismiss,
            )

        EditorPicker.ARCHIVE_CATEGORY ->
            ArchiveCategoryPickerDialog(
                categories = state.categories,
                selectedId = state.archiveCategoryId,
                onSelect = onSelectArchiveCategory,
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

        EditorPicker.RELATION_SAUCE ->
            SaucePickerDialog(
                options = state.sauceOptions,
                onSelect = onSelectRelationSauce,
                onDismiss = onDismiss,
            )

        EditorPicker.RELATION_RECIPE ->
            RecipePickerDialog(
                options = state.recipeOptions,
                onSelect = onSelectRelationRecipe,
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
    onNotesChange: (String) -> Unit,
    onCuisineChange: (String) -> Unit,
    onPrepMinutesChange: (String) -> Unit,
    onCookMinutesChange: (String) -> Unit,
    onAllergenNotesChange: (String) -> Unit,
    onHeroPicked: (PendingRecipeImage) -> Unit,
    onHeroClear: () -> Unit,
    onNoteImage: (PendingRecipeImage) -> Unit,
    onRemoveNoteImage: (Int) -> Unit,
    onOpenCategoryPicker: () -> Unit,
    onOpenArchiveCategoryPicker: () -> Unit,
    onYieldAmountChange: (String) -> Unit,
    onOpenYieldUnit: () -> Unit,
    onMinRangeAmountChange: (String) -> Unit,
    onOpenMinRangeUnit: () -> Unit,
    onServingsChange: (String) -> Unit,
    onAddIngredient: () -> Unit,
    onAddRecipe: () -> Unit,
    onAddSauce: () -> Unit,
    onAddRelationSauce: () -> Unit,
    onAddRelationRecipe: () -> Unit,
    onRemoveRelation: (String) -> Unit,
    onLineAmountChange: (String, String) -> Unit,
    onToggleOptional: (String) -> Unit,
    onRemoveLine: (String) -> Unit,
    onAddStep: () -> Unit,
    onStepTitleChange: (String, String) -> Unit,
    onStepBodyChange: (String, String) -> Unit,
    onStepTimeChange: (String, Int) -> Unit,
    onStepImage: (String, PendingRecipeImage) -> Unit,
    onClearStepImage: (String) -> Unit,
    onRemoveStep: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ArchiveEditorTopBar(
            title =
                if (state.isNew) {
                    stringResource(R.string.recipes_editor_new_title)
                } else {
                    stringResource(R.string.recipes_editor_edit_title_short)
                },
            isSaving = state.isSaving,
            canSave = !state.isLoading && (state.isDirty || state.isNew),
            onBack = onCancel,
            onCancel = onCancel,
            onSave = onSave,
        )
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
                onNotesChange = onNotesChange,
                onHeroPicked = onHeroPicked,
                onHeroClear = onHeroClear,
                onNoteImage = onNoteImage,
                onRemoveNoteImage = onRemoveNoteImage,
                onOpenArchiveCategoryPicker = onOpenArchiveCategoryPicker,
                onCuisineChange = onCuisineChange,
                onPrepMinutesChange = onPrepMinutesChange,
                onCookMinutesChange = onCookMinutesChange,
                onAllergenNotesChange = onAllergenNotesChange,
                onOpenCategoryPicker = onOpenCategoryPicker,
                onYieldAmountChange = onYieldAmountChange,
                onOpenYieldUnit = onOpenYieldUnit,
                onMinRangeAmountChange = onMinRangeAmountChange,
                onOpenMinRangeUnit = onOpenMinRangeUnit,
                onServingsChange = onServingsChange,
                onAddIngredient = onAddIngredient,
                onAddRecipe = onAddRecipe,
                onAddSauce = onAddSauce,
                onAddRelationSauce = onAddRelationSauce,
                onAddRelationRecipe = onAddRelationRecipe,
                onRemoveRelation = onRemoveRelation,
                onLineAmountChange = onLineAmountChange,
                onToggleOptional = onToggleOptional,
                onRemoveLine = onRemoveLine,
                onAddStep = onAddStep,
                onStepTitleChange = onStepTitleChange,
                onStepBodyChange = onStepBodyChange,
                onStepTimeChange = onStepTimeChange,
                onStepImage = onStepImage,
                onClearStepImage = onClearStepImage,
                onRemoveStep = onRemoveStep,
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
    onNotesChange: (String) -> Unit = {},
    onHeroPicked: (PendingRecipeImage) -> Unit = {},
    onHeroClear: () -> Unit = {},
    onNoteImage: (PendingRecipeImage) -> Unit = {},
    onRemoveNoteImage: (Int) -> Unit = {},
    onOpenArchiveCategoryPicker: () -> Unit = {},
    onCuisineChange: (String) -> Unit = {},
    onPrepMinutesChange: (String) -> Unit = {},
    onCookMinutesChange: (String) -> Unit = {},
    onAllergenNotesChange: (String) -> Unit = {},
    onYieldAmountChange: (String) -> Unit,
    onOpenYieldUnit: () -> Unit,
    onMinRangeAmountChange: (String) -> Unit = {},
    onOpenMinRangeUnit: () -> Unit = {},
    onServingsChange: (String) -> Unit,
    onAddIngredient: () -> Unit,
    onAddRecipe: () -> Unit,
    onAddSauce: () -> Unit,
    onAddRelationSauce: () -> Unit = {},
    onAddRelationRecipe: () -> Unit = {},
    onRemoveRelation: (String) -> Unit = {},
    onLineAmountChange: (String, String) -> Unit,
    onToggleOptional: (String) -> Unit,
    onRemoveLine: (String) -> Unit,
    onAddStep: () -> Unit = {},
    onStepTitleChange: (String, String) -> Unit = { _, _ -> },
    onStepBodyChange: (String, String) -> Unit = { _, _ -> },
    onStepTimeChange: (String, Int) -> Unit = { _, _ -> },
    onStepImage: (String, PendingRecipeImage) -> Unit = { _, _ -> },
    onClearStepImage: (String) -> Unit = {},
    onRemoveStep: (String) -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CulinarySpacing.pageHorizontal)
                .padding(bottom = CulinarySpacing.xl),
    ) {
        RecipeBasics(
            state = state,
            onNameChange = onNameChange,
            onEnglishNameChange = onEnglishNameChange,
            onDescriptionChange = onDescriptionChange,
            onOpenCategoryPicker = onOpenCategoryPicker,
            onOpenArchiveCategoryPicker = onOpenArchiveCategoryPicker,
        )
        RecipeYieldRangeSection(
            state = state,
            onYieldAmountChange = onYieldAmountChange,
            onOpenYieldUnit = onOpenYieldUnit,
            onMinRangeAmountChange = onMinRangeAmountChange,
            onOpenMinRangeUnit = onOpenMinRangeUnit,
            onServingsChange = onServingsChange,
        )
        RecipeHeroSection(
            state = state,
            onPicked = onHeroPicked,
            onClear = onHeroClear,
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
            stepImageBytes =
                state.steps.associate { step ->
                    step.key to (step.pendingImage?.bytes ?: step.image?.let { state.imageBytes[it.id] })
                },
            onAddStep = onAddStep,
            onStepTitleChange = onStepTitleChange,
            onStepBodyChange = onStepBodyChange,
            onStepTimeChange = onStepTimeChange,
            onStepImage = onStepImage,
            onClearStepImage = onClearStepImage,
            onRemoveStep = onRemoveStep,
        )
        RecipeApplicationSection(
            state = state,
            onCuisineChange = onCuisineChange,
            onPrepMinutesChange = onPrepMinutesChange,
            onCookMinutesChange = onCookMinutesChange,
            onAllergenNotesChange = onAllergenNotesChange,
        )
        RecipeRelationsSection(
            state = state,
            onAddSauce = onAddRelationSauce,
            onAddRecipe = onAddRelationRecipe,
            onRemove = onRemoveRelation,
        )
        RecipeNotesSection(
            state = state,
            onNotesChange = onNotesChange,
            onNoteImage = onNoteImage,
            onRemoveNoteImage = onRemoveNoteImage,
        )
        RecipeEditorFooter(state)
    }
}

@Composable
private fun RecipeBasics(
    state: RecipeEditorUiState,
    onNameChange: (String) -> Unit,
    onEnglishNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onOpenCategoryPicker: () -> Unit,
    onOpenArchiveCategoryPicker: () -> Unit,
) {
    ArchiveEditorSectionHeader(
        index = "",
        english = "CLASSIFICATION",
        title = "档案信息",
        subtitle = if (state.isNew) "新菜谱默认保存并发布首版，之后编辑生成待发布修改。" else "修改保存为待发布版本，不覆盖当前正式版本。",
        showRule = false,
    )
    ArchiveEditorInlineField(
        value = state.englishName,
        onValueChange = onEnglishNameChange,
        placeholder = "English name",
        textStyle = CulinaryTextStyles.pageTitle,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
    ArchiveEditorInlineField(
        value = state.name,
        onValueChange = onNameChange,
        placeholder = "中文名称",
        textStyle = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(CulinarySpacing.xs))
    ArchiveEditorInlineField(
        value = state.description,
        onValueChange = onDescriptionChange,
        placeholder = "描述这道菜的风格、来源与用途……",
        textStyle = MaterialTheme.typography.bodySmall,
        singleLine = false,
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(CulinarySpacing.md))
    val categoryPath =
        state.categories
            .firstOrNull { it.id == state.archiveCategoryId }
            ?.pathLabel
            .orEmpty()
            .ifBlank { "未分类" }
    Column(verticalArrangement = Arrangement.spacedBy(CulinarySpacing.xs)) {
        CulinaryButton(
            text =
                stringResource(R.string.recipes_category_label) +
                    "：" + recipeCategoryLabel(state.category),
            onClick = onOpenCategoryPicker,
            variant = CulinaryButtonVariant.Secondary,
            icon = CulinaryIcons.ChevronDown,
        )
        CulinaryButton(
            text = "分类归属：$categoryPath",
            onClick = onOpenArchiveCategoryPicker,
            variant = CulinaryButtonVariant.Secondary,
            icon = CulinaryIcons.ChevronDown,
        )
    }
}

@Composable
private fun RecipeYieldRangeSection(
    state: RecipeEditorUiState,
    onYieldAmountChange: (String) -> Unit,
    onOpenYieldUnit: () -> Unit,
    onMinRangeAmountChange: (String) -> Unit,
    onOpenMinRangeUnit: () -> Unit,
    onServingsChange: (String) -> Unit,
) {
    ArchiveEditorSectionHeader(
        index = "",
        english = "YIELD / MIN. RANGE",
        title = "产量与制作范围",
    )
    ArchiveYieldRangeFields(
        yieldAmount = state.yieldAmount,
        yieldUnit = state.yieldUnit?.symbol.orEmpty(),
        minRangeAmount = state.minRangeAmount,
        minRangeUnit = state.minRangeUnit?.symbol.orEmpty(),
        onYieldAmountChange = onYieldAmountChange,
        onYieldUnitClick = onOpenYieldUnit,
        onMinRangeAmountChange = onMinRangeAmountChange,
        onMinRangeUnitClick = onOpenMinRangeUnit,
    )
    Spacer(modifier = Modifier.height(CulinarySpacing.md))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.recipes_servings_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        ArchiveEditorInlineField(
            value = state.servings,
            onValueChange = onServingsChange,
            placeholder = "1",
            textStyle = CulinaryTextStyles.numeralS,
            keyboardType = KeyboardType.Number,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(120.dp),
        )
    }
}

@Composable
private fun RecipeHeroSection(
    state: RecipeEditorUiState,
    onPicked: (PendingRecipeImage) -> Unit,
    onClear: () -> Unit,
) {
    ArchiveEditorSectionHeader(
        index = "",
        english = "HERO MEDIA",
        title = "成品图片",
    )
    val pick = rememberRecipeImagePicker(onPicked)
    val preview =
        state.heroImagePending?.bytes
            ?: state.heroImage?.let { state.imageBytes[it.id] }
    ArchiveEditorImageBox(
        imageBytes = preview,
        onPick = pick,
        onClear = if (state.heroImage != null || state.heroImagePending != null) onClear else null,
        emptyLabel = "上传成品图片",
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
    ArchiveEditorSectionHeader(
        index = "01",
        english = "INGREDIENTS",
        title = stringResource(R.string.recipes_ingredients_title),
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
            text = "暂未添加食材",
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
    ArchiveEditorSectionHeader(
        index = "03",
        english = "APPLICATION",
        title = stringResource(R.string.recipes_application),
    )
    Text(
        text = stringResource(R.string.recipes_cuisine),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ArchiveEditorInlineField(
        value = state.cuisine,
        onValueChange = onCuisineChange,
        placeholder = "填写适用菜系",
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(CulinarySpacing.md))
    Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.md)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.recipes_prep_minutes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ArchiveEditorInlineField(
                value = state.prepMinutes,
                onValueChange = onPrepMinutesChange,
                placeholder = "0",
                textStyle = CulinaryTextStyles.numeralS,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.recipes_cook_minutes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ArchiveEditorInlineField(
                value = state.cookMinutes,
                onValueChange = onCookMinutesChange,
                placeholder = "0",
                textStyle = CulinaryTextStyles.numeralS,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Spacer(modifier = Modifier.height(CulinarySpacing.md))
    Text(
        text = stringResource(R.string.recipes_allergen),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ArchiveEditorInlineField(
        value = state.allergenNotes,
        onValueChange = onAllergenNotesChange,
        placeholder = "填写过敏原与适用提示",
        singleLine = false,
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RecipeRelationsSection(
    state: RecipeEditorUiState,
    onAddSauce: () -> Unit,
    onAddRecipe: () -> Unit,
    onRemove: (String) -> Unit,
) {
    ArchiveEditorSectionHeader(
        index = "04",
        english = "RELATED RECORDS",
        title = "关联档案",
    )
    if (state.relations.isEmpty()) {
        Text(
            text = "暂未添加关联档案",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        state.relations.forEach { relation ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = relation.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = relation.relationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CulinaryTextButton(
                    text = "移除",
                    onClick = { onRemove(relation.targetId) },
                )
            }
            CulinaryRule()
        }
    }
    Row(
        modifier = Modifier.padding(top = CulinarySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs),
    ) {
        CulinaryTextButton(text = "+ 关联酱汁", onClick = onAddSauce)
        CulinaryTextButton(text = "+ 关联菜谱", onClick = onAddRecipe)
    }
}

@Composable
private fun RecipeNotesSection(
    state: RecipeEditorUiState,
    onNotesChange: (String) -> Unit,
    onNoteImage: (PendingRecipeImage) -> Unit,
    onRemoveNoteImage: (Int) -> Unit,
) {
    ArchiveEditorSectionHeader(
        index = "05",
        english = "NOTES",
        title = stringResource(R.string.recipes_notes),
    )
    ArchiveEditorNotesBox(
        value = state.notes,
        onValueChange = onNotesChange,
        placeholder = "记录研发说明、实验结论与调整记录……",
    )
    val pickNoteImage = rememberRecipeImagePicker(onNoteImage)
    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
    val noteImages = state.noteImages.mapNotNull { it to state.imageBytes[it.id] } +
        state.noteImagesPending.map { null to it.bytes }
    if (noteImages.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs)) {
            noteImages.forEachIndexed { index, (_, bytes) ->
                if (bytes != null) {
                    RecipeNoteThumbnail(
                        bytes = bytes,
                        onRemove = { onRemoveNoteImage(index) },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
    }
    CulinaryTextButton(text = "+ 上传研发照片", onClick = pickNoteImage)
    state.seedNote.takeIf { it.isNotBlank() }?.let { seed ->
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        Text(
            text = "原有测试记录",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ArchiveProse(seed)
    }
}

@Composable
private fun RecipeNoteThumbnail(
    bytes: ByteArray,
    onRemove: () -> Unit,
) {
    val bitmap =
        remember(bytes) {
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    if (bitmap == null) return
    Box {
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(96.dp),
        )
        Icon(
            imageVector = CulinaryIcons.Close,
            contentDescription = "移除照片",
            tint = MaterialTheme.colorScheme.error,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clickable(onClick = onRemove)
                    .padding(2.dp),
        )
    }
}

@Composable
private fun RecipeEditorFooter(state: RecipeEditorUiState) {
    Spacer(modifier = Modifier.height(CulinarySpacing.lg))
    CulinaryRule()
    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
    val status =
        when {
            state.isSaving -> "正在保存…"
            state.isNew || !state.hasPublishedVersion -> "尚未保存"
            state.isDirty -> "修改尚未保存"
            else -> "已保存"
        }
    Text(
        text = status,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    formatArchiveTimestamp(state.updatedAtEpochMillis)?.let { updated ->
        Text(
            text = stringResource(R.string.recipes_last_updated, updated),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = CulinarySpacing.xxs),
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
private fun ArchiveCategoryPickerDialog(
    categories: List<ArchiveCategory>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = categories.filterNot { it.isTest }.sortedBy { it.pathLabel }
    PickerDialog(
        title = "选择分类归属",
        items = options.map { it.pathLabel to (it.id == selectedId) },
        onSelect = { index -> onSelect(options[index].id) },
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
