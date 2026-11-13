package com.culinary.feature.sauces

import com.culinary.core.designsystem.component.ArchiveEditorDragHandle
import com.culinary.core.designsystem.component.ArchiveEditorImageBox
import com.culinary.core.designsystem.component.ArchiveEditorInlineField
import com.culinary.core.designsystem.component.ArchiveEditorNotesBox
import com.culinary.core.designsystem.component.ArchiveEditorSectionHeader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.culinary.core.designsystem.component.ArchiveYieldRangeFields
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.KitchenTimeSegmentsField
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles

@Suppress("LongMethod") // 头部品牌/分类路径 + 双语标题 + 三栏元数据是一个排版单元
@Composable
internal fun SauceHeaderSection(
    state: SauceEditorUiState,
    actions: SauceEditorActions,
) {
    val pathCategories =
        state.categoryPathIds.mapNotNull { id -> state.categories.firstOrNull { it.id == id } }
    Column(modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.lg)) {
        Text(
            text =
                pathCategories
                    .joinToString(" / ") { it.name }
                    .ifBlank { "SAUCE · 酱汁档案" },
            style = CulinaryTextStyles.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        ArchiveEditorInlineField(
            value = state.englishName,
            onValueChange = actions.onEnglishNameChange,
            placeholder = "Beurre Blanc",
            textStyle = CulinaryTextStyles.pageTitle,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
        ArchiveEditorInlineField(
            value = state.name,
            onValueChange = actions.onNameChange,
            placeholder = "中文名称",
            textStyle = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        ArchiveEditorInlineField(
            value = state.description,
            onValueChange = actions.onDescriptionChange,
            placeholder = "描述这款酱汁的风格与用途……",
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.lg))
        SauceClassificationSelector(state = state, actions = actions)
        Spacer(modifier = Modifier.height(CulinarySpacing.lg))
        ArchiveYieldRangeFields(
            yieldAmount = state.yieldAmount,
            yieldUnit = state.yieldUnit?.symbol.orEmpty(),
            minRangeAmount = state.minRangeAmount,
            minRangeUnit = state.minRangeUnit?.symbol.orEmpty(),
            onYieldAmountChange = actions.onYieldAmountChange,
            onYieldUnitClick = actions.onOpenYieldUnit,
            onMinRangeAmountChange = actions.onMinRangeAmountChange,
            onMinRangeUnitClick = actions.onOpenMinRangeUnit,
        )
    }
}

@Suppress("LoopWithTooManyJumpStatements")
@Composable
private fun SauceClassificationSelector(
    state: SauceEditorUiState,
    actions: SauceEditorActions,
) {
    val formal = state.categories.filter { state.isTestRecord || !it.isTest }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "CLASSIFICATION / 分类归属",
            style = CulinaryTextStyles.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        if (formal.isEmpty()) {
            Text(
                text = "分类数据尚未加载。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        var level = 0
        while (level <= state.categoryPathIds.size) {
            val parentId = if (level == 0) null else state.categoryPathIds.getOrNull(level - 1)
            val options = formal.filter { it.parentId == parentId }
            if (options.isEmpty()) break
            CategoryLevelField(
                level = level,
                selectedId = state.categoryPathIds.getOrNull(level),
                options = options,
                onSelect = { id -> actions.onCategorySelect(level, id) },
            )
            level++
            if (level > 32) break
        }
    }
}

@Composable
private fun CategoryLevelField(
    level: Int,
    selectedId: String?,
    options: List<com.culinary.core.domain.model.ArchiveCategory>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }
    Column(modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.xs)) {
        Text(
            text = "LEVEL ${(level + 1).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true }
                        .padding(vertical = CulinarySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected?.name ?: "选择分类",
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (selected == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    modifier = Modifier.weight(1f),
                )
                if (selected != null) {
                    Text(
                        text = "清除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onSelect(null) },
                    )
                }
                Icon(
                    imageVector = CulinaryIcons.ChevronDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = CulinarySpacing.xs).size(14.dp),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            expanded = false
                            onSelect(category.id)
                        },
                    )
                }
            }
        }
        CulinaryRule()
    }
}

@Composable
internal fun SauceHeroSection(
    state: SauceEditorUiState,
    actions: SauceEditorActions,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.xl)) {
        Text(
            text = "HERO MEDIA",
            style = CulinaryTextStyles.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
        Text(
            text = stringResource(R.string.sauces_hero_title),
            style = CulinaryTextStyles.sectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        val pick = rememberSauceImagePicker { actions.onHeroPicked(it) }
        ArchiveEditorImageBox(
            imageBytes = state.heroImagePending?.bytes ?: state.heroImage?.let { state.imageBytes[it.id] },
            onPick = pick,
            onClear =
                if (state.heroImage != null || state.heroImagePending != null) {
                    actions.onHeroClear
                } else {
                    null
                },
            emptyLabel = stringResource(R.string.sauces_hero_upload),
            replaceLabel = stringResource(R.string.sauces_image_replace),
            deleteLabel = stringResource(R.string.sauces_image_delete),
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        ArchiveEditorInlineField(
            value = state.heroCaption,
            onValueChange = actions.onHeroCaptionChange,
            placeholder = stringResource(R.string.sauces_hero_caption_hint),
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SauceIngredientsSection(
    state: SauceEditorUiState,
    actions: SauceEditorActions,
) {
    var showPicker by remember { mutableStateOf(false) }
    ArchiveEditorSectionHeader(
        index = "01",
        english = "INGREDIENTS",
        title = stringResource(R.string.sauces_editor_ingredients),
        subtitle = "基础配方",
    )
    state.lines.forEachIndexed { index, line ->
        SauceIngredientRow(
            line = line,
            onNameChange = { actions.onLineNameChange(line.key, it) },
            onNameEnChange = { actions.onLineNameEnChange(line.key, it) },
            onAmountChange = { actions.onLineAmountChange(line.key, it) },
            onUnitClick = { actions.onOpenLineUnit(line.key) },
            onToggleOptional = { actions.onToggleOptional(line.key) },
            onRemove = { actions.onRemoveLine(line.key) },
            onMove = { delta -> actions.onMoveLine(index, index + delta) },
        )
    }
    if (state.lines.isEmpty()) {
        Text(
            text = "暂未添加食材",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = CulinarySpacing.sm),
        )
    }
    CulinaryTextButton(
        text = stringResource(R.string.sauces_add_ingredient),
        onClick = { showPicker = true },
        modifier = Modifier.padding(top = CulinarySpacing.xxs),
    )
    if (showPicker) {
        SauceIngredientPickerDialog(
            ingredients = state.ingredients,
            onSelect = {
                actions.onAddIngredient(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Suppress("LongMethod") // 名称/英文名/数量/单位/可选/删除共享同一行布局
@Composable
private fun SauceIngredientRow(
    line: SauceLineEditor,
    onNameChange: (String) -> Unit,
    onNameEnChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onUnitClick: () -> Unit,
    onToggleOptional: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CulinaryRule()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArchiveEditorDragHandle(onMove = onMove)
            Column(modifier = Modifier.weight(1f).padding(start = CulinarySpacing.xs)) {
                if (line.isReadOnly) {
                    Text(
                        text = line.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = line.kindLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ArchiveEditorInlineField(
                        value = line.name,
                        onValueChange = onNameChange,
                        placeholder = stringResource(R.string.sauces_editor_ingredient_name),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ArchiveEditorInlineField(
                        value = line.nameEn,
                        onValueChange = onNameEnChange,
                        placeholder = "English name",
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArchiveEditorInlineField(
                        value = line.amount,
                        onValueChange = onAmountChange,
                        placeholder = "0",
                        textStyle = CulinaryTextStyles.numeralS,
                        textAlign = TextAlign.End,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                        modifier = Modifier.width(58.dp),
                    )
                    Text(
                        text = line.unit.symbol,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .clickable(onClick = onUnitClick)
                                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.sauces_optional),
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (line.isOptional) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                    modifier = Modifier.clickable(onClick = onToggleOptional).padding(4.dp),
                )
            }
            Icon(
                imageVector = CulinaryIcons.Close,
                contentDescription = stringResource(R.string.sauces_remove_line),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).clickable(onClick = onRemove).padding(2.dp),
            )
        }
    }
}

@Composable
internal fun SauceMethodSection(
    state: SauceEditorUiState,
    actions: SauceEditorActions,
) {
    ArchiveEditorSectionHeader(
        index = "02",
        english = "METHOD",
        title = stringResource(R.string.sauces_editor_method),
    )
    state.steps.forEachIndexed { index, step ->
        SauceStepRow(
            index = index,
            step = step,
            imageBytes = step.pendingImage?.bytes ?: step.image?.let { state.imageBytes[it.id] },
            onTitleChange = { actions.onStepTitleChange(step.key, it) },
            onBodyChange = { actions.onStepBodyChange(step.key, it) },
            onTimeChange = { actions.onStepTimeChange(step.key, it) },
            onImagePicked = { actions.onStepImage(step.key, it) },
            onClearImage = { actions.onClearStepImage(step.key) },
            onRemove = { actions.onRemoveStep(step.key) },
            onMove = { delta -> actions.onMoveStep(index, index + delta) },
        )
    }
    if (state.steps.isEmpty()) {
        Text(
            text = stringResource(R.string.sauces_method_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = CulinarySpacing.sm),
        )
    }
    CulinaryTextButton(
        text = stringResource(R.string.sauces_add_step),
        onClick = actions.onAddStep,
        modifier = Modifier.padding(top = CulinarySpacing.xxs),
    )
}

@Composable
private fun SauceStepRow(
    index: Int,
    step: SauceStepEditor,
    imageBytes: ByteArray?,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onTimeChange: (Int) -> Unit,
    onImagePicked: (com.culinary.core.domain.model.PendingSauceImage) -> Unit,
    onClearImage: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val pick = rememberSauceImagePicker(onPicked = onImagePicked)
    Column(modifier = Modifier.fillMaxWidth()) {
        CulinaryRule()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArchiveEditorDragHandle(onMove = onMove)
            Text(
                text = "%02d".format(index + 1),
                style = CulinaryTextStyles.numeralS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = CulinarySpacing.xs),
            )
            ArchiveEditorInlineField(
                value = step.title,
                onValueChange = onTitleChange,
                placeholder = stringResource(R.string.sauces_editor_step_title),
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = CulinaryIcons.Close,
                contentDescription = stringResource(R.string.sauces_remove_step),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).clickable(onClick = onRemove).padding(2.dp),
            )
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        ArchiveEditorImageBox(
            imageBytes = imageBytes,
            onPick = pick,
            onClear = if (imageBytes != null) onClearImage else null,
            emptyLabel = stringResource(R.string.sauces_step_upload),
            replaceLabel = stringResource(R.string.sauces_image_replace),
            deleteLabel = stringResource(R.string.sauces_image_delete),
            aspectRatio = 4f / 3f,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        ArchiveEditorInlineField(
            value = step.body,
            onValueChange = onBodyChange,
            placeholder = stringResource(R.string.sauces_editor_step_body),
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = false,
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        KitchenTimeSegmentsField(
            seconds = step.timeSeconds,
            onSecondsChange = onTimeChange,
            modifier = Modifier.padding(top = CulinarySpacing.xs),
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
    }
}

@Composable
internal fun SauceApplicationSection(
    state: SauceEditorUiState,
    actions: SauceEditorActions,
) {
    ArchiveEditorSectionHeader(
        index = "03",
        english = "APPLICATION",
        title = stringResource(R.string.sauces_application),
    )
    ArchiveEditorInlineField(
        value = state.application,
        onValueChange = actions.onApplicationChange,
        placeholder = stringResource(R.string.sauces_application_hint),
        textStyle = MaterialTheme.typography.bodyLarge,
        singleLine = false,
        minLines = 4,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Suppress("LongMethod") // 关系行、空态、添加入口与选择弹窗属于同一分节
@Composable
internal fun SauceRelationsSection(
    state: SauceEditorUiState,
    actions: SauceEditorActions,
) {
    var showPicker by remember { mutableStateOf(false) }
    ArchiveEditorSectionHeader(
        index = "04",
        english = "RELATED SAUCES",
        title = stringResource(R.string.sauces_relations_title),
    )
    state.relations.forEachIndexed { index, relation ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArchiveEditorDragHandle(onMove = { delta -> actions.onMoveRelation(index, index + delta) })
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable { actions.onOpenSauce(relation.sauceId) }
                        .padding(horizontal = CulinarySpacing.xs, vertical = 4.dp),
            ) {
                Text(
                    text = relation.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (relation.nameEn.isNotBlank()) {
                    Text(
                        text = relation.nameEn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "↗",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .clickable { actions.onOpenSauce(relation.sauceId) }
                        .padding(horizontal = CulinarySpacing.xs, vertical = 4.dp),
            )
            Icon(
                imageVector = CulinaryIcons.Close,
                contentDescription = stringResource(R.string.sauces_relation_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .size(20.dp)
                        .clickable { actions.onRemoveRelation(relation.sauceId) }
                        .padding(2.dp),
            )
        }
        CulinaryRule()
    }
    if (state.relations.isEmpty()) {
        Text(
            text = stringResource(R.string.sauces_relation_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = CulinarySpacing.sm),
        )
    }
    CulinaryTextButton(
        text = stringResource(R.string.sauces_relation_add),
        onClick = { showPicker = true },
        modifier = Modifier.padding(top = CulinarySpacing.xxs),
    )
    if (showPicker) {
        SaucePickerDialog(
            sauces = state.sauceOptions,
            currentSauceId = state.sauceId,
            selectedIds = state.relations.map { it.sauceId }.toSet(),
            onSelect = {
                actions.onAddRelation(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
internal fun SauceNotesSection(
    state: SauceEditorUiState,
    actions: SauceEditorActions,
) {
    ArchiveEditorSectionHeader(
        index = "05",
        english = "NOTES",
        title = stringResource(R.string.sauces_notes),
    )
    Text(
        text = stringResource(R.string.sauces_notes_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
    ArchiveEditorNotesBox(
        value = state.notes,
        onValueChange = actions.onNotesChange,
        placeholder = stringResource(R.string.sauces_notes_placeholder),
    )
    Spacer(modifier = Modifier.height(CulinarySpacing.xs))
    Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.md)) {
        CulinaryTextButton(
            text = stringResource(R.string.sauces_notes_add_text),
            onClick = actions.onAddTextNote,
        )
        val pick = rememberSauceImagePicker { actions.onNoteImage(it) }
        CulinaryTextButton(
            text = stringResource(R.string.sauces_notes_add_photo),
            onClick = pick,
        )
    }
    val pending = state.noteImagesPending
    val existing = state.noteImages
    if (pending.isNotEmpty() || existing.isNotEmpty()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = CulinarySpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm),
        ) {
            pending.forEachIndexed { index, image ->
                NoteThumbnail(
                    bytes = image.bytes,
                    onRemove = { actions.onRemoveNoteImage(index) },
                )
            }
            existing.forEachIndexed { index, image ->
                NoteThumbnail(
                    bytes = state.imageBytes[image.id],
                    onRemove = { actions.onRemoveNoteImage(pending.size + index) },
                )
            }
        }
    }
}

@Composable
private fun NoteThumbnail(
    bytes: ByteArray?,
    onRemove: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .width(88.dp)
                .height(88.dp)
                .clip(RectangleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        bytes?.toImageBitmapOrNull()?.let { bitmap ->
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(88.dp),
            )
        }
        Icon(
            imageVector = CulinaryIcons.Close,
            contentDescription = stringResource(R.string.sauces_image_delete),
            tint = MaterialTheme.colorScheme.error,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .background(MaterialTheme.colorScheme.surface)
                    .size(20.dp)
                    .clickable(onClick = onRemove)
                    .padding(2.dp),
        )
    }
}

@Composable
private fun MetaLabel(text: String) {
    Text(
        text = text,
        style = CulinaryTextStyles.eyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
