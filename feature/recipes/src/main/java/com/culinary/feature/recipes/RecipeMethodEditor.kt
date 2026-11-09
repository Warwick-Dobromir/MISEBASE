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
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.KitchenTimeField
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles

/**
 * 菜谱编辑器的制作步骤区（任务书第五阶段）。
 *
 * 与酱汁编辑器同构：编号、标题、说明、步骤时间；时间统一保存为总秒数。
 * 只增加时间字段，不改变菜谱原有业务字段与保存流程。
 */
@Composable
internal fun RecipeMethodEditor(
    state: RecipeEditorUiState,
    onAddStep: () -> Unit,
    onStepTitleChange: (String, String) -> Unit,
    onStepBodyChange: (String, String) -> Unit,
    onStepTimeChange: (String, Int) -> Unit,
    onRemoveStep: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "制作工艺",
            eyebrow = "02 / METHOD",
            subtitle = "${state.steps.size} 步 · 可设置每步时间",
        )
        state.steps.forEachIndexed { index, step ->
            CulinaryRule()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "%02d".format(index + 1),
                    style = CulinaryTextStyles.numeralS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(CulinarySpacing.xs))
                CulinaryTextField(
                    value = step.title,
                    onValueChange = { onStepTitleChange(step.key, it) },
                    label = { Text(text = "步骤标题") },
                    singleLine = true,
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
            CulinaryTextField(
                value = step.body,
                onValueChange = { onStepBodyChange(step.key, it) },
                label = { Text(text = "步骤说明") },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            KitchenTimeField(
                seconds = step.timeSeconds,
                onSecondsChange = { onStepTimeChange(step.key, it) },
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        }
        if (state.steps.isEmpty()) {
            Text(
                text = "还没有制作步骤；可添加任意数量。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CulinaryTextButton(text = "添加步骤", onClick = onAddStep)
    }
}
