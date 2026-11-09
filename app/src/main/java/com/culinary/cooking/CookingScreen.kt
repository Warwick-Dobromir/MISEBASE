package com.culinary.cooking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.R
import com.culinary.core.common.KitchenTime
import com.culinary.core.designsystem.component.CulinaryLinkText
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.KitchenProgressLine
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.icon.CulinaryOutlineIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles

/**
 * 统一制作模式页面（菜谱与酱汁共用）。
 *
 * 竖屏单列、可纵向滚动；进度条是连续细线，编号统一使用制作流程编号。
 */
@Composable
fun CookingModeRoute(
    onExit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CookingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler { onExit(viewModel.exitStepNo()) }

    CookingModeScreen(
        state = state,
        onExit = { onExit(viewModel.exitStepNo()) },
        onToggleTimer = viewModel::toggleTimer,
        onAdjust = viewModel::adjust,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onConfirmBlocking = viewModel::confirmBlockingStep,
        modifier = modifier,
    )
}

@Composable
internal fun CookingModeScreen(
    state: CookingUiState,
    onExit: () -> Unit,
    onToggleTimer: () -> Unit,
    onAdjust: (Int) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onConfirmBlocking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        CookingTopBar(
            archiveLabel = state.source.archiveLabel,
            onBack = onExit,
        )
        when {
            state.isLoading ->
                Text(
                    text = stringResource(R.string.cooking_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(CulinarySpacing.pageHorizontal),
                )

            state.error != null ->
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(CulinarySpacing.pageHorizontal),
                )

            state.finished -> FinishedBody(state = state, onExit = onExit)

            else ->
                ActiveBody(
                    state = state,
                    onToggleTimer = onToggleTimer,
                    onAdjust = onAdjust,
                    onNext = onNext,
                    onPrevious = onPrevious,
                )
        }
    }

    state.blockingStep?.let { blocking ->
        NoTimeStepDialog(
            step = blocking,
            imageBytes = state.stepImages[blocking.number],
            onConfirm = onConfirmBlocking,
        )
    }
}

@Composable
private fun CookingTopBar(
    archiveLabel: String,
    onBack: () -> Unit,
) {
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(CulinarySpacing.xxl)
                    .padding(horizontal = CulinarySpacing.pageHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.clickable(role = Role.Button, onClick = onBack),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = CulinaryIcons.Back,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = archiveLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = CulinarySpacing.xxs),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.cooking_mode),
                style = CulinaryTextStyles.eyebrow,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        CulinaryRule()
    }
}

@Composable
private fun ActiveBody(
    state: CookingUiState,
    onToggleTimer: () -> Unit,
    onAdjust: (Int) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val current = state.current ?: return
    val next = state.plan.getOrNull(state.index + 1)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CulinarySpacing.pageHorizontal),
    ) {
        Spacer(modifier = Modifier.height(CulinarySpacing.lg))
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        state.englishTitle?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title.uppercase(),
                style = CulinaryTextStyles.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(CulinarySpacing.lg))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.cooking_progress),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "%02d / %02d".format(current.number, current.total),
                style = CulinaryTextStyles.numeralS,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        KitchenProgressLine(progress = state.progress)
        Spacer(modifier = Modifier.height(CulinarySpacing.lg))

        Text(
            text = "CURRENT STAGE · " + "%02d".format(current.number),
            style = CulinaryTextStyles.eyebrow,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
        Text(
            text = current.step.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        state.stepImages[current.number]?.let { bytes ->
            Spacer(modifier = Modifier.height(CulinarySpacing.sm))
            StepImage(bytes = bytes)
        }
        if (current.step.body.isNotBlank()) {
            Spacer(modifier = Modifier.height(CulinarySpacing.sm))
            Text(
                text = current.step.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (current.hasTime) {
            TimerBlock(
                state = state,
                onToggleTimer = onToggleTimer,
                onAdjust = onAdjust,
            )
        } else {
            Text(
                text = stringResource(R.string.cooking_no_time_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = CulinarySpacing.md),
            )
        }

        CulinaryRule()
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        Text(
            text = stringResource(R.string.cooking_current_operation),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
        Text(
            text = current.step.body.ifBlank { current.step.title },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(CulinarySpacing.lg))
        CulinaryRule()
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "‹ " + stringResource(R.string.cooking_previous),
                style = MaterialTheme.typography.labelLarge,
                color =
                    if (state.plan.take(state.index).any { it.hasTime }) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier =
                    Modifier
                        .clickable(
                            enabled = state.plan.take(state.index).any { it.hasTime },
                            onClick = onPrevious,
                        ).padding(vertical = CulinarySpacing.xs),
            )
            Text(
                text = stringResource(R.string.cooking_next) + " ›",
                style = MaterialTheme.typography.labelLarge,
                color =
                    if (next != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier =
                    Modifier
                        .clickable(enabled = next != null, onClick = onNext)
                        .padding(vertical = CulinarySpacing.xs),
            )
        }
        next?.let {
            Text(
                text = stringResource(R.string.cooking_next_step, it.step.title.ifBlank { it.step.body.take(16) }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.xxl))
    }
}

@Composable
private fun TimerBlock(
    state: CookingUiState,
    onToggleTimer: () -> Unit,
    onAdjust: (Int) -> Unit,
) {
    Spacer(modifier = Modifier.height(CulinarySpacing.lg))
    Text(
        text = stringResource(R.string.cooking_remaining),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = KitchenTime.format(state.remainingSeconds),
        style = CulinaryTextStyles.numeralXl,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = CookingPlan.presetLabel(state.presetSeconds),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundControl(text = "−", onClick = { onAdjust(-60) })
        Spacer(modifier = Modifier.width(CulinarySpacing.lg))
        RoundControl(
            text = if (state.isRunning) "❚❚" else "▶",
            onClick = onToggleTimer,
            emphasized = true,
        )
        Spacer(modifier = Modifier.width(CulinarySpacing.lg))
        RoundControl(text = "+", onClick = { onAdjust(60) })
    }
    Spacer(modifier = Modifier.height(CulinarySpacing.xs))
    Text(
        text =
            if (state.isRunning) {
                stringResource(R.string.cooking_pause)
            } else {
                stringResource(R.string.cooking_start)
            },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggleTimer)
                .padding(vertical = CulinarySpacing.xs),
    )
}

@Composable
private fun RoundControl(
    text: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    Box(
        modifier =
            Modifier
                .size(if (emphasized) 56.dp else 44.dp)
                .clip(CircleShape)
                .background(
                    if (emphasized) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                ).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color =
                if (emphasized) {
                    MaterialTheme.colorScheme.onSecondary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

@Composable
private fun FinishedBody(
    state: CookingUiState,
    onExit: () -> Unit,
) {
    val last = state.lastTimedStep
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CulinarySpacing.pageHorizontal),
    ) {
        Spacer(modifier = Modifier.height(CulinarySpacing.lg))
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        state.englishTitle?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title.uppercase(),
                style = CulinaryTextStyles.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.lg))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.cooking_progress),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "%02d / %02d".format(last?.number ?: state.plan.size, state.plan.size),
                style = CulinaryTextStyles.numeralS,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        KitchenProgressLine(progress = 1f)
        Spacer(modifier = Modifier.height(CulinarySpacing.xxl))
        Icon(
            imageVector = CulinaryOutlineIcons.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(40.dp).align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.md))
        Text(
            text = stringResource(R.string.cooking_time_up),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.cooking_all_done),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.lg))
        Text(
            text = "00/00/00",
            style = CulinaryTextStyles.numeralXl,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xl))
        CulinaryRule()
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        Text(
            text = stringResource(R.string.cooking_last_stage),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        last?.let { step ->
            Text(
                text = step.step.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = CookingPlan.presetLabel(step.step.timeSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.lg))
        CulinaryRule()
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        Text(
            text = stringResource(R.string.cooking_finished_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        Text(
            text = stringResource(R.string.cooking_return),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onExit)
                    .padding(vertical = CulinarySpacing.sm),
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xxl))
    }
}

/** 无时间步骤阻断弹窗：遮罩不可点击、无跳过、无取消，只能"确认 →"。 */
@Composable
private fun NoTimeStepDialog(
    step: CookingPlanStep,
    imageBytes: ByteArray?,
    onConfirm: () -> Unit,
) {
    BackHandler {}
    androidx.compose.ui.window.Dialog(
        onDismissRequest = {},
        properties =
            androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
    ) {
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.extraSmall,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(CulinarySpacing.pageHorizontal)
                        .verticalScroll(rememberScrollState()),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.cooking_no_time_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "%02d / %02d".format(step.number, step.total),
                        style = CulinaryTextStyles.numeralS,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
                Text(
                    text = stringResource(R.string.cooking_no_time_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.md))
                CulinaryRule()
                Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                Text(
                    text = step.step.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                imageBytes?.let { bytes ->
                    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                    StepImage(bytes = bytes)
                }
                if (step.step.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                    Text(
                        text = step.step.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.height(CulinarySpacing.md))
                CulinaryRule()
                Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                Text(
                    text = stringResource(R.string.cooking_confirm),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.End,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClick = onConfirm)
                            .padding(vertical = CulinarySpacing.sm),
                )
            }
        }
    }
}

@Composable
private fun StepImage(bytes: ByteArray) {
    val bitmap =
        androidx.compose.runtime.remember(bytes) {
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    if (bitmap == null) return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(MaterialTheme.shapes.extraSmall),
    )
}
