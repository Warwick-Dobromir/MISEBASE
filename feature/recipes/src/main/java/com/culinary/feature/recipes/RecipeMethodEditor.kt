package com.culinary.feature.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.culinary.core.designsystem.component.ArchiveEditorDragHandle
import com.culinary.core.designsystem.component.ArchiveEditorImageBox
import com.culinary.core.designsystem.component.ArchiveEditorInlineField
import com.culinary.core.designsystem.component.ArchiveEditorSectionHeader
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.KitchenTimeSegmentsField
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles
import com.culinary.core.domain.model.PendingRecipeImage

/**
 * 菜谱编辑器的制作步骤区：编号、标题、说明、三段式制作时间。
 *
 * 与酱汁编辑器同构；无时间的步骤保留步骤本身，只不显示计时入口。
 */
@Composable
internal fun RecipeMethodEditor(
    state: RecipeEditorUiState,
    stepImageBytes: Map<String, ByteArray?>,
    onAddStep: () -> Unit,
    onStepTitleChange: (String, String) -> Unit,
    onStepBodyChange: (String, String) -> Unit,
    onStepTimeChange: (String, Int) -> Unit,
    onStepImage: (String, PendingRecipeImage) -> Unit,
    onClearStepImage: (String) -> Unit,
    onRemoveStep: (String) -> Unit,
    onMoveStep: (Int, Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ArchiveEditorSectionHeader(
            index = "02",
            english = "METHOD",
            title = "制作工艺",
        )
        state.steps.forEachIndexed { index, step ->
            CulinaryRule()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArchiveEditorDragHandle(
                    onMove = { offset ->
                        val target = index + offset
                        if (target in state.steps.indices) onMoveStep(index, target)
                    },
                )
                Text(
                    text = "%02d".format(index + 1),
                    style = CulinaryTextStyles.numeralS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = CulinarySpacing.xs),
                )
                ArchiveEditorInlineField(
                    value = step.title,
                    onValueChange = { onStepTitleChange(step.key, it) },
                    placeholder = "步骤标题",
                    textStyle = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(CulinarySpacing.xs))
                Icon(
                    imageVector = CulinaryIcons.Close,
                    contentDescription = "删除此步骤",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clickable { onRemoveStep(step.key) }
                            .padding(2.dp),
                )
            }
            Spacer(modifier = Modifier.height(CulinarySpacing.xs))
            StepImageEditor(
                step = step,
                bytes = stepImageBytes[step.key],
                onPicked = { onStepImage(step.key, it) },
                onClear = { onClearStepImage(step.key) },
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.xs))
            ArchiveEditorInlineField(
                value = step.body,
                onValueChange = { onStepBodyChange(step.key, it) },
                placeholder = "步骤说明",
                singleLine = false,
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            KitchenTimeSegmentsField(
                seconds = step.timeSeconds,
                onSecondsChange = { onStepTimeChange(step.key, it) },
                modifier = Modifier.padding(top = CulinarySpacing.xs),
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        }
        if (state.steps.isEmpty()) {
            Text(
                text = "暂未添加制作步骤",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CulinaryTextButton(text = "+ 添加步骤", onClick = onAddStep)
    }
}

@Composable
private fun StepImageEditor(
    step: RecipeStepEditor,
    bytes: ByteArray?,
    onPicked: (PendingRecipeImage) -> Unit,
    onClear: () -> Unit,
) {
    val pick = rememberRecipeImagePicker(onPicked)
    Spacer(modifier = Modifier.height(CulinarySpacing.xs))
    ArchiveEditorImageBox(
        imageBytes = bytes,
        onPick = pick,
        onClear = if (step.image != null || step.pendingImage != null) onClear else null,
        emptyLabel = "上传步骤图片",
        aspectRatio = 4f / 3f,
    )
}
