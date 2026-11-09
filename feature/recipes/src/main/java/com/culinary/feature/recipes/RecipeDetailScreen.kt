package com.culinary.feature.recipes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.ArchiveDetailBar
import com.culinary.core.designsystem.component.ArchiveDetailFooter
import com.culinary.core.designsystem.component.ArchiveDetailHeading
import com.culinary.core.designsystem.component.ArchiveIngredientRow
import com.culinary.core.designsystem.component.ArchiveModuleHeader
import com.culinary.core.designsystem.component.ArchiveNoteImages
import com.culinary.core.designsystem.component.ArchiveProcedureStep
import com.culinary.core.designsystem.component.ArchiveProse
import com.culinary.core.designsystem.component.ArchiveRelatedRow
import com.culinary.core.designsystem.component.ArchiveVersionHistory
import com.culinary.core.designsystem.component.ArchiveVersionItem
import com.culinary.core.designsystem.component.ArchiveWideImage
import com.culinary.core.designsystem.component.ArchiveYieldRange
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryButtonVariant
import com.culinary.core.designsystem.component.CulinaryLinkText
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.formatArchiveTimestamp
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.ExpandedRecipe
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.KitchenRelatedRecord
import com.culinary.core.domain.model.KitchenStep
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeComponentKind
import com.culinary.core.domain.model.RecipeComponentView
import com.culinary.core.domain.model.RecipeDetail
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.VersionStatus

/**
 * 菜谱详情路由。
 *
 * [onStartTimer] 携带被点击步骤在原始档案中的序号（1 起）进入统一制作模式；
 * [scrollToStep] 用于制作模式返回后定位到对应步骤。
 */
@Composable
fun RecipeDetailRoute(
    onEdit: (String) -> Unit,
    onOpenRelation: (KitchenRecordKind, String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    onStartTimer: (Int) -> Unit = {},
    scrollToStep: Int? = null,
    modifier: Modifier = Modifier,
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    RecipeDetailScreen(
        state = state,
        onEdit = onEdit,
        onPublish = viewModel::publish,
        onServingsChange = viewModel::onTargetServingsChange,
        onExpand = viewModel::expand,
        onRetry = viewModel::load,
        onOpenRelation = onOpenRelation,
        onBack = onBack,
        onStartTimer = onStartTimer,
        scrollToStep = scrollToStep,
        modifier = modifier,
    )
}

@Composable
private fun RecipeDetailScreen(
    state: RecipeDetailUiState,
    onEdit: (String) -> Unit,
    onPublish: (RecipeVersionId) -> Unit,
    onServingsChange: (String) -> Unit,
    onExpand: () -> Unit,
    onRetry: () -> Unit,
    onOpenRelation: (KitchenRecordKind, String) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onStartTimer: (Int) -> Unit = {},
    scrollToStep: Int? = null,
) {
    Column(modifier = modifier.fillMaxSize()) {
        state.error?.let { message ->
            ErrorBanner(
                message = message,
                actionLabel = stringResource(R.string.recipes_retry),
                onAction = onRetry,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            )
        }
        when {
            state.isLoading -> LoadingRows()
            state.detail == null ->
                Text(
                    text = stringResource(R.string.recipes_load_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(CulinarySpacing.pageHorizontal),
                )

            else ->
                RecipeDetailContent(
                    detail = state.detail,
                    state = state,
                    onEdit = onEdit,
                    onPublish = onPublish,
                    onServingsChange = onServingsChange,
                    onExpand = onExpand,
                    onOpenRelation = onOpenRelation,
                    onBack = onBack,
                    onStartTimer = onStartTimer,
                    scrollToStep = scrollToStep,
                )
        }
    }
}

@Composable
internal fun RecipeDetailContent(
    detail: RecipeDetail,
    state: RecipeDetailUiState,
    onEdit: (String) -> Unit,
    onPublish: (RecipeVersionId) -> Unit,
    onServingsChange: (String) -> Unit,
    onExpand: () -> Unit,
    onOpenRelation: (KitchenRecordKind, String) -> Unit,
    onBack: () -> Unit = {},
    onStartTimer: (Int) -> Unit = {},
    scrollToStep: Int? = null,
) {
    val recipe = detail.recipe
    val latestDraft = detail.versions.firstOrNull { it.status == VersionStatus.DRAFT }
    val shown = detail.currentVersion ?: latestDraft ?: detail.displayVersion ?: detail.versions.firstOrNull()
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var stepTopPx by remember { mutableIntStateOf(-1) }

    LaunchedEffect(scrollToStep, stepTopPx) {
        val target = scrollToStep
        if (target != null && stepTopPx >= 0 && target > 0) {
            val offset = (stepTopPx - with(density) { 96.dp.toPx() }).toInt().coerceAtLeast(0)
            scrollState.animateScrollTo(offset)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ArchiveDetailBar(
            editLabel = "编辑档案 ↗",
            onBack = onBack,
            onEdit = { onEdit(recipe.id.value) },
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = CulinarySpacing.pageHorizontal),
        ) {
            Spacer(modifier = Modifier.height(CulinarySpacing.lg))
            ArchiveDetailHeading(
                categoryLabel = categoryEyebrow(recipe, state.categoryPath),
                englishTitle = recipe.englishName,
                chineseTitle = recipe.name,
                description = recipe.description,
            )
            ArchiveYieldRange(
                yieldAmount = measureAmount(shown?.yield?.amount),
                yieldUnit = shown?.yield?.unit?.symbol.orEmpty(),
                minRangeAmount = measureAmount(shown?.minRange?.amount),
                minRangeUnit = shown?.minRange?.unit?.symbol.orEmpty(),
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.lg))
            state.imageBytes?.let { bytes ->
                ArchiveWideImage(bytes = bytes, caption = null)
            }

            ArchiveModuleHeader(
                number = "01",
                english = "INGREDIENTS",
                chinese = "原料配方",
                subtitle = "基础配方",
            )
            if (detail.components.isEmpty()) {
                Text(
                    text = "暂未添加食材",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                detail.components.forEachIndexed { index, component ->
                    ComponentLine(component)
                    if (index < detail.components.lastIndex) {
                        CulinaryRule()
                    }
                }
            }
            ExpansionBlock(
                state = state,
                onServingsChange = onServingsChange,
                onExpand = onExpand,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.xl))

            ArchiveModuleHeader(number = "02", english = "METHOD", chinese = "制作工艺")
            if (state.steps.isEmpty()) {
                Text(
                    text = "暂未添加制作步骤",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.steps.forEachIndexed { index, step ->
                    val stepNo = index + 1
                    Column(
                        modifier =
                            Modifier.onGloballyPositioned { coordinates ->
                                if (scrollToStep == stepNo) {
                                    stepTopPx = coordinates.positionInParent().y.toInt()
                                }
                            },
                    ) {
                        ArchiveProcedureStep(
                            number = stepNo,
                            title = step.title,
                            imageBytes = state.stepImages[stepNo],
                            body = step.body,
                            timeSeconds = step.timeSeconds,
                            startTimerLabel = "启动此阶段计时",
                            onStartTimer =
                                if (step.timeSeconds > 0) {
                                    { onStartTimer(stepNo) }
                                } else {
                                    null
                                },
                        )
                    }
                    if (index < state.steps.lastIndex) {
                        CulinaryRule()
                    }
                }
                Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            }

            val application =
                listOfNotNull(
                    recipe.cuisine?.takeIf { it.isNotBlank() }?.let { "适用菜系" to it },
                    recipe.prepMinutes?.let { "准备时间" to "$it 分钟" },
                    recipe.cookMinutes?.let { "烹饪时间" to "$it 分钟" },
                    recipe.allergenNotes?.takeIf { it.isNotBlank() }?.let { "过敏原与适用提示" to it },
                    recipe.ingredientSummary
                        ?.takeIf { it.isNotBlank() && it != recipe.description }
                        ?.let { "原料摘要" to it },
                )
            ArchiveModuleHeader(number = "03", english = "APPLICATION", chinese = "适用范围")
            if (application.isEmpty()) {
                Text(
                    text = "暂未填写适用范围",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                application.forEach { (label, value) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ArchiveProse(value)
                    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                }
            }
            Spacer(modifier = Modifier.height(CulinarySpacing.lg))

            ArchiveModuleHeader(number = "04", english = "RELATED RECORDS", chinese = "关联档案")
            if (state.relations.isEmpty()) {
                Text(
                    text = "暂未添加关联档案",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.relations.forEach { relation ->
                    ArchiveRelatedRow(
                        title = relation.targetLabel,
                        subtitle = null,
                        relationLabel = relation.relationLabel,
                        onClick = { onOpenRelation(relation.targetKind, relation.targetRecordId) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(CulinarySpacing.xl))

            ArchiveModuleHeader(number = "05", english = "NOTES", chinese = "研发备注")
            val noteText = recipe.notes?.takeIf { it.isNotBlank() } ?: recipe.seedNote?.takeIf { it.isNotBlank() }
            if (noteText == null && state.noteImages.isEmpty()) {
                Text(
                    text = "暂未添加研发备注",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                noteText?.let { note ->
                    ArchiveProse(note)
                    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                }
                if (state.noteImages.isNotEmpty()) {
                    ArchiveNoteImages(images = state.noteImages)
                }
            }
            Spacer(modifier = Modifier.height(CulinarySpacing.xl))

            if (latestDraft != null) {
                CulinaryRule()
                Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "待发布修改 v" + latestDraft.versionNo,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    CulinaryLinkText(
                        text =
                            if (state.isPublishing) {
                                stringResource(R.string.recipes_publishing)
                            } else {
                                stringResource(R.string.recipes_publish, latestDraft.versionNo)
                            },
                        onClick = { onPublish(latestDraft.id) },
                    )
                }
                Spacer(modifier = Modifier.height(CulinarySpacing.lg))
            }

            val draftLabel = stringResource(R.string.recipes_status_draft)
            val publishedLabel = stringResource(R.string.recipes_status_published)
            val supersededLabel = stringResource(R.string.recipes_status_superseded)
            ArchiveVersionHistory(
                versions =
                    detail.versions
                        .sortedByDescending { it.versionNo }
                        .map { version ->
                            ArchiveVersionItem(
                                versionNo = version.versionNo,
                                statusLabel =
                                    when (version.status) {
                                        VersionStatus.DRAFT -> draftLabel
                                        VersionStatus.PUBLISHED -> publishedLabel
                                        VersionStatus.SUPERSEDED -> supersededLabel
                                    },
                                dateLabel = null,
                                isCurrent = version.isCurrent,
                            )
                        },
            )

            ArchiveDetailFooter(
                updatedLabel =
                    "最后更新 " +
                        (formatArchiveTimestamp(recipe.updatedAtEpochMillis) ?: "—"),
                versionLabel = shown?.let { "正式版本 v" + it.versionNo },
            )
        }
    }
}

@Composable
private fun ComponentLine(component: RecipeComponentView) {
    ArchiveIngredientRow(
        name = component.label,
        nameEn = null,
        amount = formatRecipeAmount(component.quantity.amount) + " " + component.quantity.unit.symbol,
        note = componentKindLabel(component),
        badge = if (component.isOptional) stringResource(R.string.recipes_optional) else null,
    )
}

@Composable
private fun componentKindLabel(component: RecipeComponentView): String =
    when (component.kind) {
        RecipeComponentKind.INGREDIENT -> "食材"
        RecipeComponentKind.RECIPE -> "子菜谱 v" + (component.referencedVersionNo ?: 0)
        RecipeComponentKind.SAUCE -> "酱汁 v" + (component.referencedVersionNo ?: 0)
    }

/** 按份数展开：默认收起，点击后才显示输入，避免把编辑控件堆在展示页。 */
@Composable
private fun ExpansionBlock(
    state: RecipeDetailUiState,
    onServingsChange: (String) -> Unit,
    onExpand: () -> Unit,
) {
    var open by remember { androidx.compose.runtime.mutableStateOf(state.expanded != null) }
    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
    CulinaryLinkText(
        text = if (open) "收起配方" else "按份数展开配方",
        onClick = { open = !open },
    )
    if (!open) return
    Spacer(modifier = Modifier.height(CulinarySpacing.xs))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CulinaryTextField(
            value = state.targetServings,
            onValueChange = onServingsChange,
            label = { Text(text = stringResource(R.string.recipes_servings_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            fillWidth = false,
            modifier = Modifier.width(132.dp),
        )
        Spacer(modifier = Modifier.width(CulinarySpacing.xs))
        CulinaryButton(
            text =
                stringResource(
                    if (state.isExpanding) R.string.recipes_expanding else R.string.recipes_expand,
                ),
            onClick = onExpand,
            variant = CulinaryButtonVariant.Secondary,
            enabled = !state.isExpanding,
            loading = state.isExpanding,
        )
    }
    state.expansionError?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = CulinarySpacing.xs),
        )
    }
    state.expanded?.let { expanded -> ExpandedLines(expanded) }
}

@Composable
private fun ExpandedLines(expanded: ExpandedRecipe) {
    Text(
        text =
            stringResource(
                R.string.recipes_expanded,
                expanded.targetServings.toString(),
                expanded.baseServings.toString(),
            ),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = CulinarySpacing.sm),
    )
    if (expanded.lines.isEmpty()) {
        Text(
            text = stringResource(R.string.recipes_expansion_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    expanded.lines.forEach { line ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (line.isOptional) {
                    Text(
                        text = stringResource(R.string.recipes_optional),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = formatRecipeAmount(line.quantity.amount) + " " + line.quantity.unit.symbol,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    Text(
        text = stringResource(R.string.recipes_expansion_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = CulinarySpacing.xs),
    )
}

@Composable
private fun categoryEyebrow(
    recipe: Recipe,
    categoryPath: List<String>,
): String =
    if (categoryPath.isNotEmpty()) {
        "RECIPE · " + categoryPath.joinToString(" / ")
    } else {
        "RECIPE · " + recipeCategoryLabel(recipe.category).uppercase()
    }

@Composable
private fun categoryLabel(
    recipe: Recipe,
    categoryPath: List<String>,
): String = categoryPath.lastOrNull() ?: recipeCategoryLabel(recipe.category)

private fun measureAmount(value: Double?): String =
    value?.let(::formatRecipeAmount) ?: "—"

private fun formatRecipeAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
