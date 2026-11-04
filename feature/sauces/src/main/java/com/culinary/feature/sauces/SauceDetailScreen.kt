package com.culinary.feature.sauces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.QuantityText
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.theme.CulinarySizes
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.SauceDetail
import com.culinary.core.domain.model.SauceLineView
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.VersionStatus

/** 酱汁详情路由：展示档案、当前版本、组成与版本历史，并可发布草稿。 */
@Composable
fun SauceDetailRoute(
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SauceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SauceDetailScreen(
        state = state,
        onEdit = onEdit,
        onPublish = viewModel::publish,
        onRetry = viewModel::load,
        onDismissError = viewModel::clearError,
        modifier = modifier,
    )
}

@Composable
internal fun SauceDetailScreen(
    state: SauceDetailUiState,
    onEdit: (String) -> Unit,
    onPublish: (SauceVersionId) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        state.error?.let { message ->
            ErrorBanner(
                message = message,
                actionLabel = stringResource(R.string.sauces_retry),
                onAction = onRetry,
            )
        }
        when {
            state.isLoading -> LoadingRows()
            state.detail == null ->
                Text(
                    text = stringResource(R.string.sauces_load_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(CulinarySpacing.md),
                )

            else ->
                SauceDetailContent(
                    detail = state.detail,
                    isPublishing = state.isPublishing,
                    onEdit = onEdit,
                    onPublish = onPublish,
                    onDismissError = onDismissError,
                )
        }
    }
}

@Composable
internal fun SauceDetailContent(
    detail: SauceDetail,
    isPublishing: Boolean,
    onEdit: (String) -> Unit,
    onPublish: (SauceVersionId) -> Unit,
    onDismissError: () -> Unit,
) {
    val latestDraft = detail.versions.firstOrNull { it.status == VersionStatus.DRAFT }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = CulinarySpacing.md,
                end = CulinarySpacing.md,
                bottom = CulinarySizes.listBottomInset,
            ),
    ) {
        item { DetailHeader(detail) }
        item { CurrentVersionSection(detail) }
        item {
            DetailActions(
                detail = detail,
                latestDraft = latestDraft,
                isPublishing = isPublishing,
                onEdit = onEdit,
                onPublish = onPublish,
                onDismissError = onDismissError,
            )
        }
        item { ComponentsHeader(lineCount = detail.lines.size) }
        items(detail.lines, key = { it.lineNo }) { line -> SauceLineRow(line) }
        item { SectionHeader(title = stringResource(R.string.sauces_versions)) }
        items(detail.versions, key = { it.id.value }) { version -> VersionRow(version) }
    }
}

@Composable
private fun DetailHeader(detail: SauceDetail) {
    Text(
        text = detail.sauce.name,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = CulinarySpacing.sm),
    )
    Row(
        modifier = Modifier.padding(top = CulinarySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusBadge(
            text =
                stringResource(
                    if (detail.sauce.isActive) R.string.sauces_active else R.string.sauces_archived,
                ),
            tone = if (detail.sauce.isActive) StatusTone.Success else StatusTone.Neutral,
        )
        Text(
            text = detail.sauce.family ?: stringResource(R.string.sauces_uncategorized),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CurrentVersionSection(detail: SauceDetail) {
    SectionHeader(title = stringResource(R.string.sauces_current_version))
    val current = detail.currentVersion
    if (current == null) {
        Text(
            text = stringResource(R.string.sauces_no_version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "v${current.versionNo}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            StatusBadge(
                text = stringResource(R.string.sauces_status_published),
                tone = StatusTone.Success,
                modifier = Modifier.padding(start = CulinarySpacing.xs),
            )
            Spacer(modifier = Modifier.weight(1f))
            QuantityText(
                amount = formatAmount(current.yield.amount),
                unit = current.yield.unit.symbol,
            )
        }
    }
}

@Composable
private fun DetailActions(
    detail: SauceDetail,
    latestDraft: SauceVersion?,
    isPublishing: Boolean,
    onEdit: (String) -> Unit,
    onPublish: (SauceVersionId) -> Unit,
    onDismissError: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md),
        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm),
    ) {
        OutlinedButton(onClick = { onEdit(detail.sauce.id.value) }) {
            Text(text = stringResource(R.string.sauces_edit))
        }
        if (latestDraft != null) {
            Button(
                onClick = {
                    onDismissError()
                    onPublish(latestDraft.id)
                },
                enabled = !isPublishing,
            ) {
                Text(
                    text =
                        if (isPublishing) {
                            stringResource(R.string.sauces_publishing)
                        } else {
                            stringResource(R.string.sauces_publish, latestDraft.versionNo)
                        },
                )
            }
        }
    }
}

@Composable
private fun ComponentsHeader(lineCount: Int) {
    SectionHeader(title = stringResource(R.string.sauces_components, lineCount))
    if (lineCount == 0) {
        Text(
            text = stringResource(R.string.sauces_no_components),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SauceLineRow(line: SauceLineView) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = line.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (line.isOptional) {
            StatusBadge(
                text = stringResource(R.string.sauces_optional),
                tone = StatusTone.Neutral,
                modifier = Modifier.padding(end = CulinarySpacing.xs),
            )
        }
        QuantityText(
            amount = formatAmount(line.quantity.amount),
            unit = line.quantity.unit.symbol,
        )
    }
}

@Composable
private fun VersionRow(version: SauceVersion) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "v${version.versionNo}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        StatusBadge(
            text = statusLabel(version.status),
            tone = statusTone(version.status),
            modifier = Modifier.padding(start = CulinarySpacing.xs),
        )
        if (version.isCurrent) {
            StatusBadge(
                text = stringResource(R.string.sauces_current_badge),
                tone = StatusTone.Accent,
                modifier = Modifier.padding(start = CulinarySpacing.xs),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        QuantityText(
            amount = formatAmount(version.yield.amount),
            unit = version.yield.unit.symbol,
        )
    }
}

@Composable
private fun statusLabel(status: VersionStatus): String =
    stringResource(
        when (status) {
            VersionStatus.DRAFT -> R.string.sauces_status_draft
            VersionStatus.PUBLISHED -> R.string.sauces_status_published
            VersionStatus.SUPERSEDED -> R.string.sauces_status_superseded
        },
    )

private fun statusTone(status: VersionStatus): StatusTone =
    when (status) {
        VersionStatus.DRAFT -> StatusTone.Warning
        VersionStatus.PUBLISHED -> StatusTone.Success
        VersionStatus.SUPERSEDED -> StatusTone.Neutral
    }

private fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
