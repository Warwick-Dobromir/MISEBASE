package com.culinary.feature.sauces

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.ConfirmDialog
import com.culinary.core.designsystem.component.CulinaryIconButton
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.PendingSauceImage
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceDifficulty
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 酱汁编辑器路由：新建与编辑共用同一页。 */
@Composable
fun SauceEditorRoute(
    onSaved: () -> Unit,
    onCancelled: () -> Unit = {},
    onOpenSauce: (String) -> Unit = {},
    onArchived: () -> Unit = onCancelled,
    modifier: Modifier = Modifier,
    viewModel: SauceEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saved = state.savedSauceId
    val archived = state.archived

    LaunchedEffect(saved) {
        if (saved != null) onSaved()
    }
    LaunchedEffect(archived) {
        if (archived) onArchived()
    }

    SauceEditorScreen(
        state = state,
        onSaved = viewModel::save,
        onCancelled = onCancelled,
        onArchive = viewModel::archive,
        onOpenSauce = onOpenSauce,
        onDismissError = viewModel::clearError,
        onFamilyChange = viewModel::onFamilyChange,
        onEnglishNameChange = viewModel::onEnglishNameChange,
        onNameChange = viewModel::onNameChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onYieldAmountChange = viewModel::onYieldAmountChange,
        onYieldUnitChange = viewModel::onYieldUnitChange,
        onDifficultyChange = viewModel::onDifficultyChange,
        onHeroPicked = viewModel::setHeroImage,
        onHeroClear = viewModel::clearHeroImage,
        onHeroCaptionChange = viewModel::onHeroCaptionChange,
        onLineNameChange = viewModel::onLineNameChange,
        onLineNameEnChange = viewModel::onLineNameEnChange,
        onLineAmountChange = viewModel::onLineAmountChange,
        onLineUnitChange = viewModel::onLineUnitChange,
        onToggleOptional = viewModel::toggleOptional,
        onRemoveLine = viewModel::removeLine,
        onMoveLine = viewModel::moveLine,
        onAddIngredient = viewModel::addIngredient,
        onStepTitleChange = viewModel::onStepTitleChange,
        onStepBodyChange = viewModel::onStepBodyChange,
        onStepImage = viewModel::setStepImage,
        onClearStepImage = viewModel::clearStepImage,
        onRemoveStep = viewModel::removeStep,
        onMoveStep = viewModel::moveStep,
        onAddStep = viewModel::addStep,
        onApplicationChange = viewModel::onApplicationChange,
        onAddRelation = viewModel::addRelation,
        onRemoveRelation = viewModel::removeRelation,
        onMoveRelation = viewModel::moveRelation,
        onNotesChange = viewModel::onNotesChange,
        onAddTextNote = viewModel::addTextNote,
        onNoteImage = viewModel::addNoteImage,
        onRemoveNoteImage = viewModel::removeNoteImageAt,
        modifier = modifier,
    )
}

@Suppress("LongMethod", "LongParameterList") // 编辑器九个分节的统一事件入口
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SauceEditorScreen(
    state: SauceEditorUiState,
    onSaved: () -> Unit,
    onCancelled: () -> Unit,
    onArchive: () -> Unit,
    onOpenSauce: (String) -> Unit,
    onDismissError: () -> Unit,
    onFamilyChange: (String) -> Unit,
    onEnglishNameChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onYieldAmountChange: (String) -> Unit,
    onYieldUnitChange: (com.culinary.core.domain.model.Unit) -> Unit,
    onDifficultyChange: (SauceDifficulty) -> Unit,
    onHeroPicked: (PendingSauceImage) -> Unit,
    onHeroClear: () -> Unit,
    onHeroCaptionChange: (String) -> Unit,
    onLineNameChange: (String, String) -> Unit,
    onLineNameEnChange: (String, String) -> Unit,
    onLineAmountChange: (String, String) -> Unit,
    onLineUnitChange: (String, com.culinary.core.domain.model.Unit) -> Unit,
    onToggleOptional: (String) -> Unit,
    onRemoveLine: (String) -> Unit,
    onMoveLine: (Int, Int) -> Unit,
    onAddIngredient: (com.culinary.core.domain.model.Ingredient) -> Unit,
    onStepTitleChange: (String, String) -> Unit,
    onStepBodyChange: (String, String) -> Unit,
    onStepImage: (String, PendingSauceImage) -> Unit,
    onClearStepImage: (String) -> Unit,
    onRemoveStep: (String) -> Unit,
    onMoveStep: (Int, Int) -> Unit,
    onAddStep: () -> Unit,
    onApplicationChange: (String) -> Unit,
    onAddRelation: (Sauce) -> Unit,
    onRemoveRelation: (String) -> Unit,
    onMoveRelation: (Int, Int) -> Unit,
    onNotesChange: (String) -> Unit,
    onAddTextNote: () -> Unit,
    onNoteImage: (PendingSauceImage) -> Unit,
    onRemoveNoteImage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showYieldUnitPicker by remember { mutableStateOf(false) }
    var lineUnitKey by remember { mutableStateOf<String?>(null) }

    val actions =
        SauceEditorActions(
            onFamilyChange = onFamilyChange,
            onEnglishNameChange = onEnglishNameChange,
            onNameChange = onNameChange,
            onDescriptionChange = onDescriptionChange,
            onYieldAmountChange = onYieldAmountChange,
            onOpenYieldUnit = { showYieldUnitPicker = true },
            onDifficultyChange = onDifficultyChange,
            onHeroPicked = onHeroPicked,
            onHeroClear = onHeroClear,
            onHeroCaptionChange = onHeroCaptionChange,
            onLineNameChange = onLineNameChange,
            onLineNameEnChange = onLineNameEnChange,
            onLineAmountChange = onLineAmountChange,
            onOpenLineUnit = { key -> lineUnitKey = key },
            onToggleOptional = onToggleOptional,
            onRemoveLine = onRemoveLine,
            onMoveLine = onMoveLine,
            onAddIngredient = onAddIngredient,
            onStepTitleChange = onStepTitleChange,
            onStepBodyChange = onStepBodyChange,
            onStepImage = onStepImage,
            onClearStepImage = onClearStepImage,
            onRemoveStep = onRemoveStep,
            onMoveStep = onMoveStep,
            onAddStep = onAddStep,
            onApplicationChange = onApplicationChange,
            onAddRelation = onAddRelation,
            onRemoveRelation = onRemoveRelation,
            onMoveRelation = onMoveRelation,
            onOpenSauce = onOpenSauce,
            onNotesChange = onNotesChange,
            onAddTextNote = onAddTextNote,
            onNoteImage = onNoteImage,
            onRemoveNoteImage = onRemoveNoteImage,
        )

    val imeVisible = WindowInsets.isImeVisible
    BackHandler(enabled = state.isDirty && !imeVisible) { showDiscard = true }
    BackHandler(enabled = !state.isDirty && !imeVisible) { onCancelled() }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SauceEditorTopBar(
            isSaving = state.isSaving,
            canSave = !state.isLoading && (state.isDirty || state.isNew),
            onBack = { if (state.isDirty) showDiscard = true else onCancelled() },
            onCancel = { if (state.isDirty) showDiscard = true else onCancelled() },
            onSave = onSaved,
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
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = CulinarySpacing.pageHorizontal),
            ) {
                SauceHeaderSection(state, actions)
                SauceHeroSection(state, actions)
                SauceIngredientsSection(state, actions)
                SauceMethodSection(state, actions)
                SauceApplicationSection(state, actions)
                SauceRelationsSection(state, actions)
                SauceNotesSection(state, actions)
                SauceFooter(
                    state = state,
                    onDelete = { showDelete = true },
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.spacious))
            }
        }
    }

    if (showDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.sauces_discard_title),
            message = stringResource(R.string.sauces_discard_message),
            confirmLabel = stringResource(R.string.sauces_discard_confirm),
            dismissLabel = stringResource(R.string.sauces_continue_editing),
            onConfirm = {
                showDiscard = false
                onCancelled()
            },
            onDismiss = { showDiscard = false },
            destructive = true,
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.sauces_delete_title),
            message = stringResource(R.string.sauces_delete_message),
            confirmLabel = stringResource(R.string.sauces_delete_confirm),
            dismissLabel = stringResource(R.string.sauces_cancel),
            onConfirm = {
                showDelete = false
                onArchive()
            },
            onDismiss = { showDelete = false },
            destructive = true,
        )
    }
    if (showYieldUnitPicker) {
        SauceUnitPickerDialog(
            units = state.units,
            selectedId = state.yieldUnit?.id?.value,
            onSelect = {
                onYieldUnitChange(it)
                showYieldUnitPicker = false
            },
            onDismiss = { showYieldUnitPicker = false },
        )
    }
    lineUnitKey?.let { key ->
        val line = state.lines.firstOrNull { it.key == key }
        SauceUnitPickerDialog(
            units = state.units,
            selectedId = line?.unit?.id?.value,
            onSelect = {
                onLineUnitChange(key, it)
                lineUnitKey = null
            },
            onDismiss = { lineUnitKey = null },
        )
    }
}

@Composable
private fun SauceEditorTopBar(
    isSaving: Boolean,
    canSave: Boolean,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = CulinarySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CulinaryIconButton(
                icon = CulinaryIcons.Back,
                contentDescription = stringResource(R.string.sauces_cancel),
                onClick = onBack,
            )
            Text(
                text = stringResource(R.string.sauces_cancel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .clickable(onClick = onCancel)
                        .padding(horizontal = CulinarySpacing.xs, vertical = CulinarySpacing.sm),
            )
            Spacer(modifier = Modifier.weight(1f))
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.size(CulinarySpacing.sm))
            } else {
                Text(
                    text = stringResource(R.string.sauces_save),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (canSave) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                    modifier =
                        Modifier
                            .clickable(enabled = canSave, onClick = onSave)
                            .padding(horizontal = CulinarySpacing.sm, vertical = CulinarySpacing.sm),
                )
            }
        }
        CulinaryRule()
    }
}

@Composable
private fun SauceFooter(
    state: SauceEditorUiState,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.xl)) {
        CulinaryRule()
        Spacer(modifier = Modifier.height(CulinarySpacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    if (state.updatedAtEpochMillis == null) {
                        stringResource(R.string.sauces_unsaved_date)
                    } else {
                        stringResource(
                            R.string.sauces_last_updated,
                            formatUpdatedAt(state.updatedAtEpochMillis),
                        )
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (state.sauceId != null) {
                Text(
                    text = stringResource(R.string.sauces_delete_archive),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.72f),
                    modifier = Modifier.clickable(onClick = onDelete).padding(4.dp),
                )
            }
        }
    }
}

private fun formatUpdatedAt(epochMillis: Long): String =
    Instant
        .ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))

/** 九个分节共用的编辑器事件集合；保持状态与 UI 解耦。 */
internal data class SauceEditorActions(
    val onFamilyChange: (String) -> Unit,
    val onEnglishNameChange: (String) -> Unit,
    val onNameChange: (String) -> Unit,
    val onDescriptionChange: (String) -> Unit,
    val onYieldAmountChange: (String) -> Unit,
    val onOpenYieldUnit: () -> Unit,
    val onDifficultyChange: (SauceDifficulty) -> Unit,
    val onHeroPicked: (PendingSauceImage) -> Unit,
    val onHeroClear: () -> Unit,
    val onHeroCaptionChange: (String) -> Unit,
    val onLineNameChange: (String, String) -> Unit,
    val onLineNameEnChange: (String, String) -> Unit,
    val onLineAmountChange: (String, String) -> Unit,
    val onOpenLineUnit: (String) -> Unit,
    val onToggleOptional: (String) -> Unit,
    val onRemoveLine: (String) -> Unit,
    val onMoveLine: (Int, Int) -> Unit,
    val onAddIngredient: (com.culinary.core.domain.model.Ingredient) -> Unit,
    val onStepTitleChange: (String, String) -> Unit,
    val onStepBodyChange: (String, String) -> Unit,
    val onStepImage: (String, PendingSauceImage) -> Unit,
    val onClearStepImage: (String) -> Unit,
    val onRemoveStep: (String) -> Unit,
    val onMoveStep: (Int, Int) -> Unit,
    val onAddStep: () -> Unit,
    val onApplicationChange: (String) -> Unit,
    val onAddRelation: (Sauce) -> Unit,
    val onRemoveRelation: (String) -> Unit,
    val onMoveRelation: (Int, Int) -> Unit,
    val onOpenSauce: (String) -> Unit,
    val onNotesChange: (String) -> Unit,
    val onAddTextNote: () -> Unit,
    val onNoteImage: (PendingSauceImage) -> Unit,
    val onRemoveNoteImage: (Int) -> Unit,
)
