package com.culinary.feature.scan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.culinary.core.designsystem.component.BrandPageHeader
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryButtonVariant
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles

/**
 * 扫码页（纯 UI，可测试）。
 *
 * 相机预览以 [cameraContent] 槽位注入：Compose UI Test 传空实现即可验证权限说明、
 * 扫描框、手电筒、手工输入与返回；真实页面由 [ScanRoute] 注入 CameraX 预览。
 *
 * 视觉：相机区域保持极简（四角遮罩 + 细描边），操作按钮使用白色高对比变体；
 * 权限与手工输入页回到普通页面规范（白底、细线、2dp 圆角）。
 */
@Composable
internal fun ScanScreen(
    state: ScanUiState,
    onRequestPermission: () -> Unit,
    onOpenManualEntry: () -> Unit,
    onCloseManualEntry: () -> Unit,
    onManualCodeChange: (String) -> Unit,
    onSubmitManualCode: () -> Unit,
    onToggleTorch: () -> Unit,
    onResumeScanning: () -> Unit,
    onBack: () -> Unit,
    onToggleMode: () -> Unit,
    onDismissTorchNotice: () -> Unit,
    cameraContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        BrandPageHeader(
            title = "扫码",
            supporting = "SCAN",
        )
        CulinaryRule()
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.showCamera -> {
                    cameraContent()
                    ScanFrameOverlay(modifier = Modifier.fillMaxSize())
                    CameraActions(
                        state = state,
                        onToggleTorch = onToggleTorch,
                        onOpenManualEntry = onOpenManualEntry,
                        onResumeScanning = onResumeScanning,
                    )
                    ScanModeButton(
                        state = state,
                        onToggleMode = onToggleMode,
                        modifier = Modifier.align(Alignment.TopStart).padding(CulinarySpacing.sm),
                    )
                }

                state.manualEntry ->
                    ManualEntryPane(
                        state = state,
                        onManualCodeChange = onManualCodeChange,
                        onSubmitManualCode = onSubmitManualCode,
                        onCloseManualEntry = onCloseManualEntry,
                        onBack = onBack,
                    )

                else ->
                    PermissionPane(
                        state = state,
                        onRequestPermission = onRequestPermission,
                        onOpenManualEntry = onOpenManualEntry,
                        onBack = onBack,
                    )
            }

            Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                state.error?.let { message ->
                    ErrorBanner(
                        message = message,
                        actionLabel = stringResource(R.string.scan_retry),
                        onAction = onResumeScanning,
                    )
                }
                state.torchNotice?.let { message ->
                    ErrorBanner(
                        message = message,
                        actionLabel = stringResource(R.string.scan_retry),
                        onAction = onDismissTorchNotice,
                    )
                }
            }
            state.matchedArchive?.let { archive ->
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = CulinarySpacing.sm, top = CulinarySpacing.sm)
                        .fillMaxWidth(0.56f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xF0141414))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 9.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    archive.imageUri?.takeIf { it.isNotBlank() }?.let { imageUri ->
                        AsyncImage(
                            model = imageUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = archive.brand?.takeIf { it.isNotBlank() }
                                ?.let { "${archive.name} · $it" } ?: archive.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        archive.productSpecification?.takeIf { it.isNotBlank() }?.let { specification ->
                            Text(
                                text = specification,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (state.successFeedbacks.isNotEmpty()) {
                ScanSuccessStack(
                    feedbacks = state.successFeedbacks,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** 扫描框：四块暗色遮罩 + 白色描边，框内不遮挡相机。 */
@Composable
private fun ScanFrameOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val scrim = Color.Black.copy(alpha = 0.45f)
        val frameWidth = size.width * FRAME_WIDTH_RATIO
        val frameHeight = minOf(frameWidth, size.height * FRAME_HEIGHT_RATIO)
        val left = (size.width - frameWidth) / 2f
        val top = (size.height - frameHeight) / 2f
        drawRect(color = scrim, size = Size(size.width, top))
        drawRect(
            color = scrim,
            topLeft = Offset(0f, top + frameHeight),
            size = Size(size.width, size.height - top - frameHeight),
        )
        drawRect(color = scrim, topLeft = Offset(0f, top), size = Size(left, frameHeight))
        drawRect(
            color = scrim,
            topLeft = Offset(left + frameWidth, top),
            size = Size(size.width - left - frameWidth, frameHeight),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(frameWidth, frameHeight),
            cornerRadius = CornerRadius(FRAME_CORNER_RADIUS_PX, FRAME_CORNER_RADIUS_PX),
            style = Stroke(width = FRAME_STROKE_PX),
        )
    }
}

@Composable
private fun CameraActions(
    state: ScanUiState,
    onToggleTorch: () -> Unit,
    onOpenManualEntry: () -> Unit,
    onResumeScanning: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.6f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.65f),
                    ),
                ).padding(CulinarySpacing.lg),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (state.mode == ScanMode.STOCK_IN) CulinaryIcons.Inventory else CulinaryIcons.Search,
                    contentDescription = null,
                    tint = Color(0xFFD2BA8A),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(
                        if (state.mode == ScanMode.STOCK_IN) R.string.scan_mode_stock_in
                        else R.string.scan_mode_view_details,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFD2BA8A),
                )
            }
            Text(
                text = stringResource(
                    if (state.mode == ScanMode.STOCK_IN) R.string.scan_mode_stock_in_support
                    else R.string.scan_mode_view_details_support,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.76f),
            )
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        Text(
            text = stringResource(R.string.scan_frame_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm)) {
            CulinaryButton(
                text =
                    stringResource(
                        if (state.torchOn) R.string.scan_torch_on else R.string.scan_torch_off,
                    ),
                onClick = onToggleTorch,
                variant = CulinaryButtonVariant.Overlay,
            )
            CulinaryButton(
                text = stringResource(R.string.scan_manual_open),
                onClick = onOpenManualEntry,
                variant = CulinaryButtonVariant.OverlayOutline,
            )
        }
        if (!state.scanning && !state.lookupInProgress && state.matchedArchive == null) {
            Spacer(modifier = Modifier.height(CulinarySpacing.sm))
            CulinaryButton(
                text = stringResource(R.string.scan_resume),
                onClick = onResumeScanning,
                variant = CulinaryButtonVariant.Overlay,
            )
        }
    }
}

/** 相机页左上角圆形模式切换按钮；图标始终表示当前模式。 */
@Composable
private fun ScanModeButton(
    state: ScanUiState,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onToggleMode,
        enabled = !state.lookupInProgress,
        modifier = modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape),
    ) {
        Icon(
            imageVector = if (state.mode == ScanMode.STOCK_IN) CulinaryIcons.Inventory else CulinaryIcons.Search,
            contentDescription = stringResource(
                if (state.mode == ScanMode.STOCK_IN) R.string.scan_mode_toggle_to_view
                else R.string.scan_mode_toggle_to_stock_in,
            ),
            tint = Color.White,
        )
    }
}

/** 固定在取景框上方的纵向压缩反馈：旧记录只保留名称行，最新记录完整显示。 */
@Composable
private fun ScanSuccessStack(
    feedbacks: List<ScanSuccessFeedback>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val frameHeight = minOf(maxWidth * FRAME_WIDTH_RATIO, maxHeight * FRAME_HEIGHT_RATIO)
        val frameTop = (maxHeight - frameHeight) / 2
        val stackHeight = 112.dp
        val stackTop = (frameTop - stackHeight - 24.dp).coerceAtLeast(8.dp)
        val visibleFeedbacks = feedbacks.takeLast(3)
        val olderFeedbacks = visibleFeedbacks.dropLast(1)
        val latestFeedback = visibleFeedbacks.lastOrNull()

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = CulinarySpacing.sm)
                    .offset(y = stackTop)
                    .fillMaxWidth(0.38f)
                    .height(stackHeight),
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            olderFeedbacks.forEachIndexed { index, feedback ->
                key(feedback.id) {
                    AnimatedVisibility(
                        visible = feedback.visible,
                        enter =
                            fadeIn(tween(100)) +
                                slideInVertically(
                                    initialOffsetY = { it / 3 },
                                    animationSpec = tween(150),
                                ),
                        exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(
                                        Color(0xF0141414).copy(
                                            alpha = if (index == 0) 0.48f else 0.72f,
                                        ),
                                    )
                                    .padding(horizontal = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = feedback.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.88f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            latestFeedback?.let { feedback ->
                key(feedback.id) {
                    AnimatedVisibility(
                        visible = feedback.visible,
                        enter =
                            fadeIn(tween(130)) +
                                slideInVertically(
                                    initialOffsetY = { it / 3 },
                                    animationSpec = tween(170),
                                ),
                        exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xF0141414))
                                    .border(
                                        1.dp,
                                        Color.White.copy(alpha = 0.12f),
                                        RoundedCornerShape(10.dp),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = feedback.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val detail = listOfNotNull(
                                    feedback.brand?.takeIf { it.isNotBlank() },
                                    feedback.specification?.takeIf { it.isNotBlank() },
                                ).joinToString(" · ")
                                if (detail.isNotBlank()) {
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.78f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Icon(
                                imageVector = CulinaryIcons.CheckCircle,
                                contentDescription = stringResource(R.string.scan_success_feedback),
                                tint = Color(0xFFD2BA8A),
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Suppress("LongMethod") // 权限说明的三个分支文案与动作同屏，拆分只会增加参数。
@Composable
private fun PermissionPane(
    state: ScanUiState,
    onRequestPermission: () -> Unit,
    onOpenManualEntry: () -> Unit,
    onBack: () -> Unit,
) {
    val title =
        when (state.cameraAccess) {
            CameraAccessState.DENIED -> stringResource(R.string.scan_permission_denied_title)
            CameraAccessState.UNAVAILABLE -> stringResource(R.string.scan_no_camera_title)
            else -> stringResource(R.string.scan_permission_title)
        }
    val body =
        when (state.cameraAccess) {
            CameraAccessState.DENIED -> stringResource(R.string.scan_permission_denied_body)
            CameraAccessState.UNAVAILABLE -> stringResource(R.string.scan_no_camera_body)
            else -> stringResource(R.string.scan_permission_body)
        }
    Column(
        modifier = Modifier.fillMaxSize().padding(CulinarySpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = title,
            style = CulinaryTextStyles.pageTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.lg))
        if (state.cameraAccess != CameraAccessState.UNAVAILABLE) {
            CulinaryButton(
                text =
                    stringResource(
                        if (state.cameraAccess == CameraAccessState.DENIED) {
                            R.string.scan_permission_request_again
                        } else {
                            R.string.scan_permission_grant
                        },
                    ),
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        }
        CulinaryButton(
            text = stringResource(R.string.scan_manual_open),
            onClick = onOpenManualEntry,
            variant = CulinaryButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.cameraAccess == CameraAccessState.DENIED) {
            Spacer(modifier = Modifier.height(CulinarySpacing.md))
            Text(
                text = stringResource(R.string.scan_permission_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        CulinaryTextButton(
            text = stringResource(R.string.scan_back),
            onClick = onBack,
        )
    }
}

@Composable
private fun ManualEntryPane(
    state: ScanUiState,
    onManualCodeChange: (String) -> Unit,
    onSubmitManualCode: () -> Unit,
    onCloseManualEntry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(CulinarySpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = stringResource(R.string.scan_manual_title),
            style = CulinaryTextStyles.pageTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        Text(
            text = stringResource(R.string.scan_manual_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.md))
        CulinaryTextField(
            value = state.manualCode,
            onValueChange = onManualCodeChange,
            label = { Text(text = stringResource(R.string.scan_manual_label)) },
            singleLine = true,
            isError = state.manualError != null,
            supportingText =
                state.manualError?.let { error ->
                    {
                        Text(text = error)
                    }
                },
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.md))
        CulinaryButton(
            text = stringResource(R.string.scan_manual_confirm),
            onClick = onSubmitManualCode,
            enabled = state.manualCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        CulinaryButton(
            text = stringResource(R.string.scan_manual_back_to_camera),
            onClick = onCloseManualEntry,
            variant = CulinaryButtonVariant.Secondary,
            enabled = state.hasCamera,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        CulinaryTextButton(
            text = stringResource(R.string.scan_back),
            onClick = onBack,
        )
    }
}

private const val FRAME_WIDTH_RATIO = 0.72f
private const val FRAME_HEIGHT_RATIO = 0.46f
private const val FRAME_CORNER_RADIUS_PX = 24f
private const val FRAME_STROKE_PX = 4f
