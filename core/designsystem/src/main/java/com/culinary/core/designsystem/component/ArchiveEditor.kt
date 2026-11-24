package com.culinary.core.designsystem.component

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.culinary.core.common.KitchenTime
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles
import kotlin.math.pow
import kotlin.math.sign

/**
 * 酱汁/菜谱共用的编辑页视觉组件。
 *
 * 只提供排版、输入和轻量交互，不读取业务数据；字段映射留在各自模块。
 */

/** 顶部操作栏：返回 / 居中标题 / 保存。 */
@Composable
fun ArchiveEditorTopBar(
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean,
    canSave: Boolean,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    cancelLabel: String = "取消",
    saveLabel: String = "保存",
    centerTitle: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (centerTitle) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = CulinarySpacing.xs),
            ) {
                Icon(
                    imageVector = CulinaryIcons.Back,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .size(44.dp)
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onBack)
                            .padding(12.dp),
                )
                Text(
                    text = title,
                    style = CulinaryTextStyles.eyebrow,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 56.dp),
                )
                Text(
                    text = saveLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color =
                        if (canSave && !isSaving) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .clickable(
                                enabled = canSave && !isSaving,
                                role = androidx.compose.ui.semantics.Role.Button,
                                onClick = onSave,
                            ).padding(horizontal = CulinarySpacing.sm, vertical = CulinarySpacing.sm),
                )
            }
        } else {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = CulinarySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = CulinaryIcons.Back,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onBack)
                            .padding(12.dp),
                )
                if (onCancel != null) {
                    Text(
                        text = cancelLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onCancel)
                                .padding(horizontal = CulinarySpacing.xs, vertical = CulinarySpacing.sm),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = title,
                    style = CulinaryTextStyles.eyebrow,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = saveLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color =
                        if (canSave && !isSaving) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                    modifier =
                        Modifier
                            .clickable(
                                enabled = canSave && !isSaving,
                                role = androidx.compose.ui.semantics.Role.Button,
                                onClick = onSave,
                            ).padding(horizontal = CulinarySpacing.sm, vertical = CulinarySpacing.sm),
                )
            }
        }
        CulinaryRule()
    }
}
/** 分节标题：编号 / 英文 / 中文 / 副标题 / 右侧动作。 */
@Composable
fun ArchiveEditorSectionHeader(
    index: String,
    english: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    showRule: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = CulinarySpacing.xl, bottom = CulinarySpacing.sm)) {
        if (showRule) {
            CulinaryRule()
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (index.isBlank()) english else "$index / $english",
                    style = CulinaryTextStyles.eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
                Text(
                    text = title,
                    style = CulinaryTextStyles.sectionTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let { value ->
                    Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            action?.invoke()
        }
    }
}

/** 行内无边框输入；编辑式档案不使用 Material 外框。 */
@Composable
fun ArchiveEditorInlineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    textAlign: TextAlign = TextAlign.Start,
    enabled: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = textAlign),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        minLines = minLines,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle.copy(textAlign = textAlign),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        maxLines = if (singleLine) 1 else 3,
                    )
                }
                inner()
            }
        },
    )
}

/** Drag offset state shared with the archive category sorter. */
private data class ArchiveEditorDragOffsetState(
    val offsetPx: Float,
    val edgeOverdragPx: Float = 0f,
)

private fun archiveEditorEdgeResistance(
    overdragPx: Float,
    maxOffsetPx: Float,
): Float {
    val limit = maxOffsetPx.coerceAtLeast(1f)
    val maxRaw = limit * 5f
    val raw = overdragPx.coerceIn(-maxRaw, maxRaw)
    val progress = (kotlin.math.abs(raw) / maxRaw).coerceIn(0f, 1f)
    return limit * progress.pow(1.15f) * raw.sign
}

private fun archiveEditorDragOffsetAfterDelta(
    currentOffsetPx: Float,
    currentEdgeOverdragPx: Float,
    deltaPx: Float,
    rowHeightPx: Float,
    maxEdgeOffsetPx: Float,
    canMove: (Int) -> Boolean,
    move: (Int) -> Boolean,
): ArchiveEditorDragOffsetState {
    val threshold = rowHeightPx.coerceAtLeast(1f)
    var offset = currentOffsetPx + deltaPx
    if (currentEdgeOverdragPx != 0f) {
        val updatedRaw = currentEdgeOverdragPx + deltaPx
        if (updatedRaw != 0f && updatedRaw.sign == currentEdgeOverdragPx.sign) {
            return ArchiveEditorDragOffsetState(
                archiveEditorEdgeResistance(updatedRaw, maxEdgeOffsetPx),
                updatedRaw,
            )
        }
        offset = updatedRaw
    }
    while (offset >= threshold) {
        if (!canMove(1) || !move(1)) {
            return ArchiveEditorDragOffsetState(archiveEditorEdgeResistance(offset, maxEdgeOffsetPx), offset)
        }
        offset -= threshold
    }
    while (offset <= -threshold) {
        if (!canMove(-1) || !move(-1)) {
            return ArchiveEditorDragOffsetState(archiveEditorEdgeResistance(offset, maxEdgeOffsetPx), offset)
        }
        offset += threshold
    }
    if ((offset < 0f && !canMove(-1)) || (offset > 0f && !canMove(1))) {
        return ArchiveEditorDragOffsetState(archiveEditorEdgeResistance(offset, maxEdgeOffsetPx), offset)
    }
    return ArchiveEditorDragOffsetState(offset)
}

/**
 * Whole-row long-press sorting. The row follows the pointer, crosses siblings by row-height
 * thresholds, resists movement beyond the list bounds, and scrolls its parent near screen edges.
 */
@Composable
fun ArchiveEditorReorderableRow(
    canMove: (Int) -> Boolean,
    onMove: (Int) -> Boolean,
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var edgeOverdragY by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableIntStateOf(0) }
    var rowTopInWindowPx by remember { mutableFloatStateOf(0f) }
    var rowCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentRowCoordinates by rememberUpdatedState(rowCoordinates)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val screenHeightPx = with(density) { screenHeightDp.dp.toPx() }
    val maxEdgeOffsetPx = with(density) { 12.dp.toPx() }
    val autoScrollEdgePx = with(density) { 144.dp.toPx() }
    val currentCanMove by rememberUpdatedState(canMove)
    val currentOnMove by rememberUpdatedState(onMove)

    LaunchedEffect(dragging, scrollState, screenHeightPx) {
        var previousFrameNanos = 0L
        while (dragging) {
            val frameNanos = withFrameNanos { it }
            val frameSeconds =
                if (previousFrameNanos == 0L) 0f
                else ((frameNanos - previousFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            previousFrameNanos = frameNanos
            if (frameSeconds > 0f && rowHeightPx > 0) {
                val center = rowTopInWindowPx + rowHeightPx / 2f + dragOffsetY
                val topProgress = ((autoScrollEdgePx - center) / autoScrollEdgePx).coerceIn(0f, 1f)
                val bottomProgress =
                    ((center - (screenHeightPx - autoScrollEdgePx)) / autoScrollEdgePx).coerceIn(0f, 1f)
                val direction =
                    when {
                        topProgress > 0f && topProgress >= bottomProgress -> -1f
                        bottomProgress > 0f -> 1f
                        else -> 0f
                    }
                val progress = maxOf(topProgress, bottomProgress)
                if (direction != 0f) {
                    val speed = with(density) { 900.dp.toPx() } * progress * progress
                    val consumed = scrollState.scrollBy(direction * speed * frameSeconds)
                    dragOffsetY += consumed
                    if (edgeOverdragY != 0f) {
                        edgeOverdragY += consumed
                        dragOffsetY = archiveEditorEdgeResistance(edgeOverdragY, maxEdgeOffsetPx)
                    }
                }
            }
        }
    }

    val animatedDragOffsetY by animateFloatAsState(
        targetValue = if (dragging) dragOffsetY else 0f,
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "archive-row-drag-offset",
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (dragging) 1.045f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "archive-row-drag-scale",
    )

    Box(
        modifier = modifier
            .onSizeChanged { rowHeightPx = it.height }
            .onGloballyPositioned {
                rowCoordinates = it
                rowTopInWindowPx = it.positionInWindow().y
            }
            .pointerInput(scrollState) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    val downCoordinates = currentRowCoordinates?.takeIf { it.isAttached }
                    if (downCoordinates == null) return@awaitEachGesture
                    var cancelledBeforeLongPress = false
                    val downScreenPosition = downCoordinates.localToScreen(down.position)
                    var longPressScreenPosition = downScreenPosition
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                cancelledBeforeLongPress = true
                                break
                            }
                            val coordinates = currentRowCoordinates?.takeIf { it.isAttached }
                            if (coordinates == null) {
                                cancelledBeforeLongPress = true
                                break
                            }
                            val screenPosition = coordinates.localToScreen(change.position)
                            val dx = screenPosition.x - downScreenPosition.x
                            val dy = screenPosition.y - downScreenPosition.y
                            val touchSlop = viewConfiguration.touchSlop
                            if (dx * dx + dy * dy > touchSlop * touchSlop) {
                                cancelledBeforeLongPress = true
                                break
                            }
                            longPressScreenPosition = screenPosition
                        }
                    }
                    if (!cancelledBeforeLongPress && down.pressed) {
                        dragging = true
                        dragOffsetY = 0f
                        edgeOverdragY = 0f
                        var previousScreenY = longPressScreenPosition.y
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val coordinates = currentRowCoordinates?.takeIf { it.isAttached } ?: break
                                val currentScreenY = coordinates.localToScreen(change.position).y
                                val deltaY = currentScreenY - previousScreenY
                                if (deltaY != 0f) {
                                    change.consume()
                                    val next = archiveEditorDragOffsetAfterDelta(
                                        currentOffsetPx = dragOffsetY,
                                        currentEdgeOverdragPx = edgeOverdragY,
                                        deltaPx = deltaY,
                                        rowHeightPx = rowHeightPx.toFloat(),
                                        maxEdgeOffsetPx = maxEdgeOffsetPx,
                                        canMove = currentCanMove,
                                        move = currentOnMove,
                                    )
                                    dragOffsetY = next.offsetPx
                                    edgeOverdragY = next.edgeOverdragPx
                                }
                                previousScreenY = currentScreenY
                                if (!change.pressed) break
                            }
                        } finally {
                            dragging = false
                            dragOffsetY = 0f
                            edgeOverdragY = 0f
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                translationY = animatedDragOffsetY
            }
            .shadow(if (dragging) 7.dp else 0.dp, androidx.compose.ui.graphics.RectangleShape)
,
    ) { content() }
}

/** 拖动排序手柄；只作用于手柄，避免和文本输入抢手势。 */
@Composable
fun ArchiveEditorDragHandle(
    modifier: Modifier = Modifier,
    onMove: (Int) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var accumulated by remember { mutableStateOf(0f) }
    Icon(
        imageVector = CulinaryIcons.Sort,
        contentDescription = "拖动排序",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .size(36.dp)
                .alpha(if (dragging) 0.45f else 1f)
                .pointerInput(onMove) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragging = true
                            accumulated = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulated += dragAmount.y
                            when {
                                accumulated > 32f -> {
                                    onMove(1)
                                    accumulated = 0f
                                }

                                accumulated < -32f -> {
                                    onMove(-1)
                                    accumulated = 0f
                                }
                            }
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    )
                },
    )
}

/** 双列计量中的单列：英文名 + 中文名 + 数值 + 单位。 */
@Composable
fun ArchiveMeasurementColumn(
    englishLabel: String,
    chineseLabel: String,
    value: String,
    unitSymbol: String,
    onValueChange: (String) -> Unit,
    onUnitClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier) {
        Text(
            text = englishLabel.uppercase(),
            style = CulinaryTextStyles.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = chineseLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
        Row(verticalAlignment = Alignment.Bottom) {
            ArchiveEditorInlineField(
                value = value,
                onValueChange = onValueChange,
                placeholder = "",
                textStyle = CulinaryTextStyles.numeralS,
                keyboardType = KeyboardType.Decimal,
                enabled = enabled,
                modifier = Modifier.width(72.dp),
            )
            Text(
                text = if (value.isBlank()) "—" else unitSymbol.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .clickable(enabled = enabled, onClick = onUnitClick)
                        .padding(start = 4.dp, bottom = 2.dp, top = 8.dp),
            )
        }
    }
}

/** 双列默认产量 / 最小制作范围；两列等宽。 */
@Composable
fun ArchiveYieldRangeFields(
    yieldAmount: String,
    yieldUnit: String,
    minRangeAmount: String,
    minRangeUnit: String,
    onYieldAmountChange: (String) -> Unit,
    onYieldUnitClick: () -> Unit,
    onMinRangeAmountChange: (String) -> Unit,
    onMinRangeUnitClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.md),
    ) {
        ArchiveMeasurementColumn(
            englishLabel = "YIELD",
            chineseLabel = "默认产量",
            value = yieldAmount,
            unitSymbol = yieldUnit,
            onValueChange = onYieldAmountChange,
            onUnitClick = onYieldUnitClick,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        ArchiveMeasurementColumn(
            englishLabel = "MIN. RANGE",
            chineseLabel = "最小制作范围",
            value = minRangeAmount,
            unitSymbol = minRangeUnit,
            onValueChange = onMinRangeAmountChange,
            onUnitClick = onMinRangeUnitClick,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 三段式制作时间：`[时] / [分] / [秒]`。
 *
 * 不显示 h/m/s、不显示单位文字；数值只保存总秒数。
 */
@Composable
fun KitchenTimeSegmentsField(
    seconds: Int,
    onSecondsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "制作时间",
    enabled: Boolean = true,
) {
    val parts = remember(seconds) { partsOf(seconds) }
    var hours by remember { mutableStateOf(parts[0]) }
    var minutes by remember { mutableStateOf(parts[1]) }
    var secs by remember { mutableStateOf(parts[2]) }

    fun push() {
        onSecondsChange(KitchenTime.secondsOfParts(hours.toIntOrNull() ?: 0, minutes.toIntOrNull() ?: 0, secs.toIntOrNull() ?: 0))
    }

    LaunchedEffect(seconds) {
        val expected = partsOf(seconds)
        if (expected != listOf(hours, minutes, secs).map { it.padStart(2, '0') }) {
            hours = expected[0]
            minutes = expected[1]
            secs = expected[2]
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SegmentBox(
                value = hours,
                onValueChange = {
                    hours = it
                    push()
                },
                enabled = enabled,
            )
            Text(
                text = " / ",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SegmentBox(
                value = minutes,
                onValueChange = {
                    minutes = it
                    push()
                },
                enabled = enabled,
            )
            Text(
                text = " / ",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SegmentBox(
                value = secs,
                onValueChange = {
                    secs = it
                    push()
                },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun SegmentBox(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier =
            Modifier
                .width(48.dp)
                .height(44.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(
                    width = if (focused) 1.dp else 0.5.dp,
                    color =
                        if (focused) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                ),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.takeLast(2)
                onValueChange(digits)
            },
            enabled = enabled,
            singleLine = true,
            textStyle =
                CulinaryTextStyles.numeralS.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
        )
    }
}

private fun partsOf(seconds: Int): List<String> {
    val safe = seconds.coerceIn(0, KitchenTime.MAX_SECONDS)
    return listOf(
        (safe / 3600).toString().padStart(2, '0'),
        ((safe % 3600) / 60).toString().padStart(2, '0'),
        (safe % 60).toString().padStart(2, '0'),
    )
}

/** 研发备注：浅底细边框，不使用阴影。 */
@Composable
fun ArchiveEditorNotesBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .padding(CulinarySpacing.md),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
        minLines = 6,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                }
                inner()
            }
        },
    )
}

/** 完整宽度图片区域；空态是浅灰上传面，有图时右上角提供替换/删除。 */
@Composable
fun ArchiveEditorImageBox(
    imageBytes: ByteArray?,
    onPick: () -> Unit,
    onClear: (() -> Unit)?,
    emptyLabel: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 9f,
    showActions: Boolean = true,
    replaceLabel: String = "替换",
    deleteLabel: String = "删除",
) {
    val bitmap = remember(imageBytes) { imageBytes?.toImageBitmapOrNull() }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = emptyLabel,
                modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                        .clickable(onClick = onPick),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = CulinaryIcons.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.xs))
                Text(
                    text = emptyLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (bitmap != null && showActions) {
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .background(Color.White.copy(alpha = 0.9f))
                        .padding(horizontal = CulinarySpacing.xs, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm),
            ) {
                Text(
                    text = replaceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable(onClick = onPick).padding(4.dp),
                )
                if (onClear != null) {
                    Text(
                        text = deleteLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable(onClick = onClear).padding(4.dp),
                    )
                }
            }
        }
    }
}

internal fun ByteArray.toImageBitmapOrNull() =
    runCatching {
        BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap()
    }.getOrNull()
