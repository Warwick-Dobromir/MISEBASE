package com.culinary.core.designsystem.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.culinary.core.common.KitchenTime
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles

/**
 * 酱汁/菜谱共用的编辑页视觉组件。
 *
 * 只提供排版、输入和轻量交互，不读取业务数据；字段映射留在各自模块。
 */

/** 顶部操作栏：返回 / 取消 / 标题 / 保存。 */
@Composable
fun ArchiveEditorTopBar(
    title: String,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean,
    canSave: Boolean,
    modifier: Modifier = Modifier,
    cancelLabel: String = "取消",
    saveLabel: String = "保存",
) {
    Column(modifier = modifier.fillMaxWidth()) {
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
            Text(
                text = cancelLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onCancel)
                        .padding(horizontal = CulinarySpacing.xs, vertical = CulinarySpacing.sm),
            )
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
