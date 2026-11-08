package com.culinary.kitchen.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.R
import com.culinary.core.designsystem.component.ConfirmDialog
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryButtonVariant
import com.culinary.core.designsystem.component.CulinaryRelationItem
import com.culinary.core.designsystem.component.CulinaryRelationSection
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.KitchenRecordKind

@Composable
fun KitchenReferenceEditorRoute(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KitchenReferenceEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    KitchenReferenceEditorScreen(
        state = state,
        onNameChange = viewModel::onNameChange,
        onCategoryChange = viewModel::onCategoryChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@Suppress("LongMethod") // 三段字段与错误/保存状态共同组成一个完整编辑表单
@Composable
internal fun KitchenReferenceEditorScreen(
    state: KitchenReferenceEditorUiState,
    onNameChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typeLabel = stringResource(state.type.typeLabelRes())
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()),
    ) {
        PageHeader(
            eyebrow = "KITCHEN",
            title =
                stringResource(
                    if (state.isNew) R.string.kitchen_reference_new_title else R.string.kitchen_reference_edit_title,
                    typeLabel,
                ),
        )
        state.error?.let { error ->
            ErrorBanner(
                message = error.message(),
                modifier =
                    Modifier.padding(
                        start = CulinarySpacing.pageHorizontal,
                        end = CulinarySpacing.pageHorizontal,
                        bottom = CulinarySpacing.md,
                    ),
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CulinarySpacing.pageHorizontal),
            verticalArrangement = Arrangement.spacedBy(CulinarySpacing.field),
        ) {
            CulinaryTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = stringResource(R.string.kitchen_reference_name),
                placeholder = stringResource(R.string.kitchen_reference_name_hint),
                singleLine = true,
                enabled = !state.isSaving,
            )
            CulinaryTextField(
                value = state.category,
                onValueChange = onCategoryChange,
                label = stringResource(R.string.kitchen_reference_category),
                placeholder = stringResource(R.string.kitchen_reference_category_hint),
                singleLine = true,
                enabled = !state.isSaving,
            )
            CulinaryTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = stringResource(R.string.kitchen_reference_description),
                placeholder = stringResource(R.string.kitchen_reference_description_hint),
                minLines = 5,
                enabled = !state.isSaving,
            )
            CulinaryButton(
                text = stringResource(R.string.kitchen_reference_save),
                onClick = onSave,
                loading = state.isSaving,
                enabled = !state.isLoading && !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.xl))
        }
    }
}

@Composable
fun KitchenReferenceDetailRoute(
    onEdit: () -> Unit,
    onArchived: () -> Unit,
    onOpenRelation: (KitchenRecordKind, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: KitchenReferenceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmArchive by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    LaunchedEffect(state.archived) {
        if (state.archived) onArchived()
    }

    KitchenReferenceDetailScreen(
        state = state,
        onEdit = onEdit,
        onArchive = { confirmArchive = true },
        onOpenRelation = onOpenRelation,
        modifier = modifier,
    )

    if (confirmArchive) {
        ConfirmDialog(
            title = stringResource(R.string.kitchen_reference_archive_title),
            message = stringResource(R.string.kitchen_reference_archive_body),
            confirmLabel = stringResource(R.string.kitchen_reference_archive_confirm),
            onConfirm = {
                confirmArchive = false
                viewModel.archive()
            },
            onDismiss = { confirmArchive = false },
            dismissLabel = stringResource(R.string.kitchen_reference_cancel),
            destructive = true,
        )
    }
}

@Suppress("LongMethod") // 详情、错误、分类说明与编辑/归档操作保持单屏结构
@Composable
internal fun KitchenReferenceDetailScreen(
    state: KitchenReferenceDetailUiState,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onOpenRelation: (KitchenRecordKind, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val typeLabel = stringResource(state.type.typeLabelRes())
    val record = state.record
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()),
    ) {
        PageHeader(
            eyebrow = stringResource(R.string.kitchen_reference_detail_eyebrow),
            title = record?.name ?: typeLabel,
            supporting = typeLabel,
        )
        state.error?.let { error ->
            ErrorBanner(
                message = error.message(),
                modifier =
                    Modifier.padding(
                        start = CulinarySpacing.pageHorizontal,
                        end = CulinarySpacing.pageHorizontal,
                        bottom = CulinarySpacing.md,
                    ),
            )
        }
        if (record != null) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CulinarySpacing.pageHorizontal),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs)) {
                    if (record.isSeed) {
                        StatusBadge(
                            text = stringResource(R.string.kitchen_reference_seed_badge),
                            tone = StatusTone.Accent,
                        )
                    }
                }
                record.category?.takeIf { it.isNotBlank() }?.let { category ->
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(CulinarySpacing.md))
                }
                record.englishName?.takeIf { it.isNotBlank() }?.let { englishName ->
                    Text(
                        text = stringResource(R.string.kitchen_reference_english_name, englishName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                }
                Text(
                    text = record.description ?: stringResource(R.string.kitchen_reference_no_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                record.seedNote?.takeIf { it.isNotBlank() }?.let { seedNote ->
                    Spacer(modifier = Modifier.height(CulinarySpacing.md))
                    Text(
                        text = stringResource(R.string.kitchen_reference_seed_note, seedNote),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(CulinarySpacing.lg))
                CulinaryRule()
                Spacer(modifier = Modifier.height(CulinarySpacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm),
                ) {
                    CulinaryButton(
                        text = stringResource(R.string.kitchen_reference_edit),
                        onClick = onEdit,
                        variant = CulinaryButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    CulinaryButton(
                        text = stringResource(R.string.kitchen_reference_archive),
                        onClick = onArchive,
                        variant = CulinaryButtonVariant.Danger,
                        loading = state.isArchiving,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            }
            CulinaryRelationSection(
                title = stringResource(R.string.kitchen_reference_relations),
                items =
                    state.relations.map { relation ->
                        CulinaryRelationItem(
                            eyebrow = relation.relationLabel,
                            title = relation.targetLabel,
                            onClick = { onOpenRelation(relation.targetKind, relation.targetRecordId) },
                        )
                    },
            )
        } else if (!state.isLoading && state.error == null) {
            Text(
                text = stringResource(R.string.kitchen_reference_not_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            )
        }
    }
}

@Composable
private fun KitchenReferenceError?.message(): String =
    when (this) {
        KitchenReferenceError.NAME_REQUIRED -> stringResource(R.string.kitchen_reference_name_error)
        KitchenReferenceError.DUPLICATE -> stringResource(R.string.kitchen_reference_duplicate_error)
        KitchenReferenceError.NOT_FOUND -> stringResource(R.string.kitchen_reference_not_found)
        KitchenReferenceError.GENERIC, null -> stringResource(R.string.kitchen_reference_error)
    }
