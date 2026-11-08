package com.culinary.feature.sauces

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles
import com.culinary.core.domain.model.PendingSauceImage
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceDifficulty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 行内无边框输入；编辑式档案不使用 Material 外框。 */
@Composable
internal fun EditorInlineField(
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

/** 章节标题：编号 / 英文小标题、中文标题、副标题，延续现有编辑式字阶。 */
@Composable
internal fun SauceSectionHeader(
    index: String,
    english: String,
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.xl, bottom = CulinarySpacing.sm)) {
        CulinaryRule()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$index / $english",
                    style = CulinaryTextStyles.eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
                Text(
                    text = title,
                    style = CulinaryTextStyles.sectionTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            action?.invoke()
        }
    }
}

/** 拖动排序手柄；只作用于手柄，避免和文本输入抢手势。 */
@Composable
internal fun DragHandle(
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

/** 完整宽度图片区域：无图是浅灰上传面，有图时右上角提供替换/删除。 */
@Suppress("LongMethod") // 空态、预览、替换/删除覆盖层是一个整体
@Composable
internal fun EditorImageBox(
    imageBytes: ByteArray?,
    onPick: () -> Unit,
    onClear: (() -> Unit)?,
    emptyLabel: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 9f,
    showActions: Boolean = true,
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
                    text = stringResource(R.string.sauces_image_replace),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable(onClick = onPick).padding(4.dp),
                )
                if (onClear != null) {
                    Text(
                        text = stringResource(R.string.sauces_image_delete),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable(onClick = onClear).padding(4.dp),
                    )
                }
            }
        }
    }
}

/** 研发备注区域：浅底细边框，不使用阴影。 */
@Composable
internal fun NotesField(
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

/** 难度选择：低强调下拉，不额外占用一整行。 */
@Composable
internal fun DifficultyField(
    value: SauceDifficulty?,
    onChange: (SauceDifficulty) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val labels =
        mapOf(
            SauceDifficulty.EASY to stringResource(R.string.sauces_difficulty_easy),
            SauceDifficulty.MEDIUM to stringResource(R.string.sauces_difficulty_medium),
            SauceDifficulty.HARD to stringResource(R.string.sauces_difficulty_hard),
        )
    Box(modifier = modifier) {
        Text(
            text = value?.let { labels[it] } ?: stringResource(R.string.sauces_difficulty_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (value == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 4.dp),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEach { (difficulty, label) ->
                DropdownMenuItem(
                    text = { Text(text = label) },
                    onClick = {
                        onChange(difficulty)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** 系统照片选择器：不申请存储权限，不引入额外图片库。 */
@Composable
internal fun rememberSauceImagePicker(onPicked: (PendingSauceImage) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    val loaded =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                if (bytes == null || bytes.isEmpty()) {
                                    null
                                } else {
                                    PendingSauceImage(
                                        bytes = bytes,
                                        mimeType = context.contentResolver.getType(uri) ?: "image/jpeg",
                                    )
                                }
                            }.getOrNull()
                        }
                    loaded?.let(onPicked)
                }
            }
        }
    return {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

internal fun ByteArray.toImageBitmapOrNull() =
    runCatching {
        BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap()
    }.getOrNull()

/** 关联酱汁选择器：只允许选择既有酱汁，排除当前档案。 */
@Composable
internal fun SaucePickerDialog(
    sauces: List<Sauce>,
    currentSauceId: String?,
    selectedIds: Set<String>,
    onSelect: (Sauce) -> Unit,
    onDismiss: () -> Unit,
) {
    val candidates = sauces.filter { it.id.value != currentSauceId && it.id.value !in selectedIds }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.sauces_relation_picker_title)) },
        text = {
            if (candidates.isEmpty()) {
                Text(text = stringResource(R.string.sauces_picker_empty))
            } else {
                Column(
                    modifier =
                        Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    candidates.forEach { sauce ->
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(sauce) }
                                    .padding(vertical = CulinarySpacing.sm),
                        ) {
                            Text(
                                text = sauce.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            sauce.englishName?.takeIf { it.isNotBlank() }?.let { englishName ->
                                Text(
                                    text = englishName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        CulinaryRule()
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.sauces_cancel))
            }
        },
    )
}

/** 食材选择器：原料只能关联既有食材档案。 */
@Composable
internal fun SauceIngredientPickerDialog(
    ingredients: List<com.culinary.core.domain.model.Ingredient>,
    onSelect: (com.culinary.core.domain.model.Ingredient) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.sauces_ingredient_picker_title)) },
        text = {
            if (ingredients.isEmpty()) {
                Text(text = stringResource(R.string.sauces_picker_empty))
            } else {
                Column(
                    modifier =
                        Modifier
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    ingredients.forEach { ingredient ->
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(ingredient) }
                                    .padding(vertical = CulinarySpacing.sm),
                        ) {
                            Text(
                                text = ingredient.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            ingredient.englishName?.takeIf { it.isNotBlank() }?.let { englishName ->
                                Text(
                                    text = englishName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        CulinaryRule()
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.sauces_cancel))
            }
        },
    )
}

/** 单位选择器；保留既有单位数据与校验。 */
@Composable
internal fun SauceUnitPickerDialog(
    units: List<com.culinary.core.domain.model.Unit>,
    selectedId: String?,
    onSelect: (com.culinary.core.domain.model.Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.sauces_unit_picker_title)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                units.forEach { unit ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(unit) }
                                .padding(vertical = CulinarySpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${unit.name}（${unit.symbol}）",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (unit.id.value == selectedId) {
                            Icon(
                                imageVector = CulinaryIcons.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    CulinaryRule()
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.sauces_cancel))
            }
        },
    )
}
