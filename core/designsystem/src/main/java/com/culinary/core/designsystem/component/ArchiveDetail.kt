package com.culinary.core.designsystem.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.icon.CulinaryOutlineIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles
import com.culinary.core.common.KitchenTime

/**
 * 菜谱 / 酱汁共用的档案详情展示框架（任务书 §四 / §五）。
 *
 * 只做排版与信息层级，不读取任何业务数据：字段映射留在各自的详情页，
 * 因此两个页面视觉一致，但业务字段不会被强行统一。
 */

/** 顶部操作栏：左侧返回箭头，右侧"编辑档案 ↗"文字入口；下方一条极细分割线。 */
@Composable
fun ArchiveDetailBar(
    editLabel: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    backLabel: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = CulinarySpacing.pageHorizontal,
                        end = CulinarySpacing.pageHorizontal,
                    ).height(CulinarySpacing.xxl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .clickable(role = Role.Button, onClick = onBack)
                        .defaultMinSize(minHeight = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = CulinaryIcons.Back,
                    contentDescription = backLabel ?: "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = editLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier =
                    Modifier
                        .clickable(role = Role.Button, onClick = onEdit)
                        .defaultMinSize(minHeight = 44.dp)
                        .padding(vertical = CulinarySpacing.sm),
            )
        }
        CulinaryRule()
    }
}

/** 档案基础信息：英文分类标签 → 英文主标题 → 中文名称 → 简短描述。 */
@Composable
fun ArchiveDetailHeading(
    categoryLabel: String?,
    englishTitle: String?,
    chineseTitle: String,
    description: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        categoryLabel?.takeIf { it.isNotBlank() }?.let { label ->
            Text(
                text = label.uppercase(),
                style = CulinaryTextStyles.eyebrow,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        }
        englishTitle?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                style =
                    MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.01.em,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
        }
        Text(
            text = chineseTitle,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        description?.takeIf { it.isNotBlank() }?.let { text ->
            Spacer(modifier = Modifier.height(CulinarySpacing.sm))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.lg))
    }
}

/** 三列基础信息项：标签在上、值在下，列宽 1:1:1。 */
data class ArchiveMetaItem(
    val label: String,
    val value: String,
)

/** 三列基础信息网格；空项直接隐藏，不用占位符补齐。 */
@Composable
fun ArchiveMetaGrid(
    items: List<ArchiveMetaItem>,
    modifier: Modifier = Modifier,
) {
    val visible = items.filter { it.value.isNotBlank() }
    if (visible.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        visible.forEach { item ->
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label.uppercase(),
                    style = CulinaryTextStyles.eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        repeat(3 - visible.size) { Spacer(modifier = Modifier.weight(1f)) }
    }
    Spacer(modifier = Modifier.height(CulinarySpacing.md))
    CulinaryRule()
}

/** 成品主图：宽幅横向展示，保持比例裁切，下方图注小字灰色。 */
@Composable
fun ArchiveWideImage(
    bytes: ByteArray,
    caption: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    if (bitmap == null) return
    Column(modifier = modifier.fillMaxWidth()) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(2.dp)),
        )
        caption?.takeIf { it.isNotBlank() }?.let { text ->
            Spacer(modifier = Modifier.height(CulinarySpacing.xs))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.lg))
    }
}

/** 模块标题：编号（香槟金）+ 英文名（粗）+ 中文名；可选副标题。 */
@Composable
fun ArchiveModuleHeader(
    number: String,
    english: String,
    chinese: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        CulinaryRule()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = number,
                style = CulinaryTextStyles.numeralM,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.width(40.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = english.uppercase(),
                    style = CulinaryTextStyles.sectionTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = chinese,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        subtitle?.takeIf { it.isNotBlank() }?.let { text ->
            Spacer(modifier = Modifier.height(CulinarySpacing.xs))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 40.dp),
            )
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
    }
}

/** 原料单行：左侧中文名 + 英文次级信息，右侧用量右对齐。 */
@Composable
fun ArchiveIngredientRow(
    name: String,
    nameEn: String?,
    amount: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    badge: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = CulinarySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                badge?.takeIf { it.isNotBlank() }?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = CulinarySpacing.xs),
                    )
                }
            }
            nameEn?.takeIf { it.isNotBlank() }?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            note?.takeIf { it.isNotBlank() }?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(CulinarySpacing.md))
        Text(
            text = amount,
            style = CulinaryTextStyles.numeralS,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 2,
        )
    }
}

/**
 * 制作工艺的单个步骤：编号 + 标题 → 步骤图片（有才显示）→ 完整说明 → 步骤时间。
 *
 * [onStartTimer] 为空表示不显示启动入口（零时间步骤）。
 */
@Composable
fun ArchiveProcedureStep(
    number: Int,
    title: String,
    imageBytes: ByteArray?,
    body: String,
    timeSeconds: Int,
    startTimerLabel: String,
    onStartTimer: (() -> Unit)?,
    modifier: Modifier = Modifier,
    titleEn: String? = null,
    stepTimeLabel: String = "步骤时间",
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = number.toString().padStart(2, '0'),
                style = CulinaryTextStyles.numeralS,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.width(32.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                titleEn?.takeIf { it.isNotBlank() }?.let { text ->
                    Text(
                        text = text.uppercase(),
                        style = CulinaryTextStyles.eyebrow,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        imageBytes?.let { bytes ->
            Spacer(modifier = Modifier.height(CulinarySpacing.sm))
            StepImage(bytes)
        }
        if (body.isNotBlank()) {
            Spacer(modifier = Modifier.height(CulinarySpacing.sm))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 32.dp),
            )
        }
        if (timeSeconds > 0) {
            Spacer(modifier = Modifier.height(CulinarySpacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stepTimeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = KitchenTime.format(timeSeconds),
                    style = CulinaryTextStyles.numeralS,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (onStartTimer != null) {
                Spacer(modifier = Modifier.height(CulinarySpacing.xs))
                Row(
                    modifier =
                        Modifier
                            .padding(start = 32.dp)
                            .clickable(role = Role.Button, onClick = onStartTimer)
                            .defaultMinSize(minHeight = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = CulinaryOutlineIcons.Clock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(CulinarySpacing.xxs))
                    Text(
                        text = startTimerLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.md))
    }
}

@Composable
private fun StepImage(bytes: ByteArray) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    if (bitmap == null) return
    Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(2.dp)),
    )
}

/** 长文本：保留原始换行，不截断、不改写。 */
@Composable
fun ArchiveProse(
    text: String,
    modifier: Modifier = Modifier,
) {
    if (text.isBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    )
}

/** 关联档案行：轻量文字布局，不用大卡片。 */
@Composable
fun ArchiveRelatedRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    relationLabel: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = CulinarySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            relationLabel?.takeIf { it.isNotBlank() }?.let { label ->
                Text(
                    text = label,
                    style = CulinaryTextStyles.eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = CulinaryIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 研发照片：横向排布，保持比例，不包裹卡片。 */
@Composable
fun ArchiveNoteImages(
    images: List<ByteArray>,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) return
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs),
    ) {
        images.forEach { bytes ->
            val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .width(148.dp)
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

/** 页脚：最后更新时间 + 品牌标识；更新时间来自真实数据。 */
@Composable
fun ArchiveDetailFooter(
    updatedLabel: String,
    modifier: Modifier = Modifier,
    brand: String = "MISEBASE",
) {
    Column(modifier = modifier.fillMaxWidth()) {
        CulinaryRule()
        Spacer(modifier = Modifier.height(CulinarySpacing.md))
        Text(
            text = updatedLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
        Text(
            text = brand,
            style = CulinaryTextStyles.eyebrow,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xl))
    }
}

/**
 * 步骤时间输入：固定六位数字，显示为 `HH/MM/SS`。
 *
 * 连续输入从秒位向左填充（输入 155 → 00/01/55）；退格删除最后一位；
 * 清空即回到零时间。不使用三个独立输入框。
 *
 * 获得焦点时全选，第一次按键替换旧值；每次输入后光标固定在末尾，
 * 避免在数字中间插入导致"3 小时"这类错误进位。
 */
@Composable
fun KitchenTimeField(
    seconds: Int,
    onSecondsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "步骤时间",
    enabled: Boolean = true,
) {
    val initial = KitchenTime.digitsOf(seconds)
    var fieldValue by remember {
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                text = initial,
                selection = androidx.compose.ui.text.TextRange(initial.length),
            ),
        )
    }
    LaunchedEffect(seconds) {
        val expected = KitchenTime.digitsOf(seconds)
        if (fieldValue.text != expected &&
            KitchenTime.secondsOfDigits(fieldValue.text) != seconds
        ) {
            fieldValue =
                androidx.compose.ui.text.input.TextFieldValue(
                    text = expected,
                    selection = androidx.compose.ui.text.TextRange(expected.length),
                )
        }
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(top = CulinarySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        BasicTextField(
            value = fieldValue,
            onValueChange = { new ->
                val digits = KitchenTime.digits(new.text)
                val updated =
                    androidx.compose.ui.text.input.TextFieldValue(
                        text = digits,
                        selection = androidx.compose.ui.text.TextRange(digits.length),
                    )
                fieldValue = updated
                val parsed = KitchenTime.secondsOfDigits(digits)
                if (parsed != seconds) onSecondsChange(parsed)
            },
            enabled = enabled,
            singleLine = true,
            textStyle =
                CulinaryTextStyles.numeralS.copy(
                    color =
                        if (seconds > 0) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    textAlign = TextAlign.End,
                ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = KitchenTimeVisualTransformation,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
            modifier =
                Modifier
                    .width(104.dp)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            fieldValue =
                                fieldValue.copy(
                                    selection =
                                        androidx.compose.ui.text.TextRange(
                                            0,
                                            fieldValue.text.length,
                                        ),
                                )
                        }
                    },
        )
    }
}

/** 六位数字 → `HH/MM/SS` 的偏移映射；输入光标始终落在数字之间。 */
object KitchenTimeVisualTransformation : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val digits = text.text
        val formatted = KitchenTime.format(KitchenTime.secondsOfDigits(digits))
        val mapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    val safe = offset.coerceIn(0, digits.length)
                    val padded = 6 - digits.length
                    val shifted = safe + padded
                    return (shifted + separatorsBefore(shifted)).coerceIn(0, formatted.length)
                }

                override fun transformedToOriginal(offset: Int): Int {
                    val separators = listOf(2, 5).count { offset > it }
                    val shifted = offset - separators
                    val padded = 6 - digits.length
                    return (shifted - padded).coerceIn(0, digits.length)
                }
            }
        return TransformedText(androidx.compose.ui.text.AnnotatedString(formatted), mapping)
    }

    private fun separatorsBefore(digitIndex: Int): Int =
        (if (digitIndex > 2) 1 else 0) + (if (digitIndex > 5) 1 else 0)
}

/** 制作模式进度：一整条连续细线，已完成部分品牌色填充。 */
@Composable
fun KitchenProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val safe = progress.coerceIn(0f, 1f)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(safe)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.secondary),
        )
    }
}

/** 页脚更新时间：真实时间戳 → `2026.11.09 08:37`；空值返回 null（不写死日期）。 */
fun formatArchiveTimestamp(epochMillis: Long?): String? {
    val value = epochMillis?.takeIf { it > 0 } ?: return null
    val local =
        java.time.Instant
            .ofEpochMilli(value)
            .atZone(java.time.ZoneId.systemDefault())
    return java.time.format.DateTimeFormatter
        .ofPattern("yyyy.MM.dd HH:mm", java.util.Locale.ROOT)
        .format(local)
}

/** 轻量文字动作（"启动此阶段计时""请返回"），品牌色、无背景。 */
@Composable
fun CulinaryLinkText(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showClock: Boolean = false,
) {
    Row(
        modifier =
            modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ).defaultMinSize(minHeight = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showClock) {
            Icon(
                imageVector = CulinaryOutlineIcons.Clock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(CulinarySpacing.xxs))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}
