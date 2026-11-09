package com.culinary.feature.sauces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.culinary.core.designsystem.component.ArchiveDetailBar
import com.culinary.core.designsystem.component.ArchiveDetailFooter
import com.culinary.core.designsystem.component.ArchiveDetailHeading
import com.culinary.core.designsystem.component.ArchiveIngredientRow
import com.culinary.core.designsystem.component.ArchiveMetaGrid
import com.culinary.core.designsystem.component.ArchiveMetaItem
import com.culinary.core.designsystem.component.ArchiveModuleHeader
import com.culinary.core.designsystem.component.ArchiveNoteImages
import com.culinary.core.designsystem.component.ArchiveProcedureStep
import com.culinary.core.designsystem.component.ArchiveProse
import com.culinary.core.designsystem.component.ArchiveRelatedRow
import com.culinary.core.designsystem.component.ArchiveWideImage
import com.culinary.core.designsystem.component.CulinaryLinkText
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.formatArchiveTimestamp
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.KitchenRelatedRecord
import com.culinary.core.domain.model.KitchenStep
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceDetail
import com.culinary.core.domain.model.SauceDifficulty
import com.culinary.core.domain.model.SauceLineView
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId

/**
 * 酱汁详情路由。
 *
 * [onStartTimer] 携带被点击步骤在原始档案中的序号（1 起）进入统一制作模式；
 * [scrollToStep] 用于制作模式返回后定位到对应步骤。
 */
@Composable
fun SauceDetailRoute(
    onEdit: (String) -> Unit,
    onOpenRelation: (KitchenRecordKind, String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    onStartTimer: (Int) -> Unit = {},
    scrollToStep: Int? = null,
    modifier: Modifier = Modifier,
    viewModel: SauceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    SauceDetailScreen(
        state = state,
        onEdit = onEdit,
        onPublish = viewModel::publish,
        onRetry = viewModel::load,
        onDismissError = viewModel::clearError,
        onOpenRelation = onOpenRelation,
        onBack = onBack,
        onStartTimer = onStartTimer,
        scrollToStep = scrollToStep,
        modifier = modifier,
    )
}

@Composable
internal fun SauceDetailScreen(
    state: SauceDetailUiState,
    onEdit: (String) -> Unit,
    onPublish: (SauceVersionId) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
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
                actionLabel = stringResource(R.string.sauces_retry),
                onAction = onRetry,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            )
        }
        when {
            state.isLoading -> LoadingRows()
            state.detail == null ->
                Text(
                    text = stringResource(R.string.sauces_load_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(CulinarySpacing.pageHorizontal),
                )

            else ->
                SauceDetailContent(
                    detail = state.detail,
                    categoryPath = state.categoryPath,
                    imageBytes = state.imageBytes,
                    isPublishing = state.isPublishing,
                    onEdit = onEdit,
                    onPublish = onPublish,
                    onDismissError = onDismissError,
                    relations = state.relations,
                    onOpenRelation = onOpenRelation,
                    steps = state.steps,
                    stepImages = state.stepImages,
                    noteImages = state.noteImages,
                    onBack = onBack,
                    onStartTimer = onStartTimer,
                    scrollToStep = scrollToStep,
                )
        }
    }
}

@Composable
internal fun SauceDetailContent(
    detail: SauceDetail,
    categoryPath: List<String> = emptyList(),
    imageBytes: ByteArray? = null,
    isPublishing: Boolean,
    onEdit: (String) -> Unit,
    onPublish: (SauceVersionId) -> Unit,
    onDismissError: () -> Unit,
    relations: List<KitchenRelatedRecord>,
    onOpenRelation: (KitchenRecordKind, String) -> Unit,
    steps: List<KitchenStep> = emptyList(),
    stepImages: Map<Int, ByteArray> = emptyMap(),
    noteImages: List<ByteArray> = emptyList(),
    onBack: () -> Unit = {},
    onStartTimer: (Int) -> Unit = {},
    scrollToStep: Int? = null,
) {
    val sauce = detail.sauce
    val latestDraft = detail.versions.firstOrNull { it.status == com.culinary.core.domain.model.VersionStatus.DRAFT }
    val shown = latestDraft ?: detail.currentVersion ?: detail.versions.firstOrNull()
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
            onEdit = { onEdit(sauce.id.value) },
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
                categoryLabel = heroEyebrow(sauce, categoryPath),
                englishTitle = sauce.englishName,
                chineseTitle = sauce.name,
                description = sauce.description ?: sauce.usageNotes,
            )
            ArchiveMetaGrid(
                items =
                    listOf(
                        ArchiveMetaItem("CATEGORY", categoryLabel(sauce, categoryPath)),
                        ArchiveMetaItem("YIELD", yieldLabel(shown)),
                        ArchiveMetaItem("DIFFICULTY", difficultyLabel(sauce.difficulty)),
                    ),
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.lg))
            imageBytes?.let { bytes ->
                ArchiveWideImage(bytes = bytes, caption = sauce.heroCaption)
            }

            if (detail.lines.isNotEmpty()) {
                ArchiveModuleHeader(
                    number = "01",
                    english = "INGREDIENTS",
                    chinese = "原料配方",
                    subtitle = yieldSubtitle(shown),
                )
                detail.lines.forEachIndexed { index, line ->
                    IngredientLine(line)
                    if (index < detail.lines.lastIndex) {
                        CulinaryRule()
                    }
                }
                Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            }

            if (steps.isNotEmpty()) {
                ArchiveModuleHeader(number = "02", english = "METHOD", chinese = "制作工艺")
                steps.forEachIndexed { index, step ->
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
                            imageBytes = stepImages[stepNo],
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
                    if (index < steps.lastIndex) {
                        CulinaryRule()
                    }
                }
                Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            }

            sauce.usageNotes?.takeIf { it.isNotBlank() }?.let { text ->
                ArchiveModuleHeader(number = "03", english = "APPLICATION", chinese = "适用范围")
                ArchiveProse(text)
                Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            }

            val relationItems = relations.filter { it.targetKind == KitchenRecordKind.SAUCE }
            if (relationItems.isNotEmpty()) {
                ArchiveModuleHeader(number = "04", english = "RELATED", chinese = "关联档案")
                relationItems.forEach { relation ->
                    ArchiveRelatedRow(
                        title = relation.targetLabel,
                        subtitle = null,
                        relationLabel = relation.relationLabel,
                        onClick = { onOpenRelation(relation.targetKind, relation.targetRecordId) },
                    )
                }
                Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            }

            val noteFields =
                listOfNotNull(
                    sauce.flavorProfile?.let { "成品标准" to it },
                    sauce.texture?.let { "质地" to it },
                    sauce.mainIngredients?.let { "主要原料" to it },
                    sauce.preparationSummary?.let { "预制说明" to it },
                    sauce.keyParameters?.let { "关键参数" to it },
                    sauce.storageNotes?.let { "储存说明" to it },
                    sauce.adjustableNotes?.let { "调整记录" to it },
                    sauce.seedNote?.let { "测试记录" to it },
                )
            if (noteFields.isNotEmpty() || noteImages.isNotEmpty()) {
                ArchiveModuleHeader(number = "05", english = "NOTES", chinese = "研发备注")
                noteFields.forEach { (label, value) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ArchiveProse(value)
                    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                }
                if (noteImages.isNotEmpty()) {
                    ArchiveNoteImages(images = noteImages)
                    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                }
                Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            }

            if (latestDraft != null) {
                CulinaryRule()
                Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.sauces_pending_draft),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    CulinaryLinkText(
                        text =
                            if (isPublishing) {
                                stringResource(R.string.sauces_publishing)
                            } else {
                                stringResource(R.string.sauces_publish, latestDraft.versionNo)
                            },
                        onClick = {
                            onDismissError()
                            onPublish(latestDraft.id)
                        },
                    )
                }
                Spacer(modifier = Modifier.height(CulinarySpacing.lg))
            }

            ArchiveDetailFooter(
                updatedLabel =
                    "最后更新 " +
                        (formatArchiveTimestamp(sauce.updatedAtEpochMillis) ?: "—"),
            )
        }
    }
}

@Composable
private fun IngredientLine(line: SauceLineView) {
    val quantity = line.quantity
    ArchiveIngredientRow(
        name = line.label,
        nameEn = null,
        amount = formatAmount(quantity.amount) + " " + quantity.unit.symbol,
        badge = if (line.isOptional) stringResource(R.string.sauces_optional) else null,
    )
}

private fun heroEyebrow(
    sauce: Sauce,
    categoryPath: List<String>,
): String =
    when {
        categoryPath.isNotEmpty() -> "SAUCE · " + categoryPath.joinToString(" / ")
        !sauce.family.isNullOrBlank() -> "SAUCE · " + sauce.family
        else -> "SAUCE"
    }

private fun categoryLabel(
    sauce: Sauce,
    categoryPath: List<String>,
): String =
    when {
        categoryPath.isNotEmpty() -> categoryPath.last()
        !sauce.family.isNullOrBlank() -> sauce.family.orEmpty()
        else -> "未分类"
    }

private fun yieldLabel(version: SauceVersion?): String =
    version?.let {
        formatAmount(it.yield.amount) + " " + it.yield.unit.symbol
    } ?: "—"

private fun yieldSubtitle(version: SauceVersion?): String =
    version?.let {
        "基础配方 · 对应产量 " + formatAmount(it.yield.amount) + " " + it.yield.unit.symbol
    } ?: "基础配方"

private fun difficultyLabel(difficulty: SauceDifficulty?): String =
    when (difficulty) {
        SauceDifficulty.EASY -> "简单"
        SauceDifficulty.MEDIUM -> "中等"
        SauceDifficulty.HARD -> "困难"
        null -> "—"
    }

private fun formatAmount(value: Double): String {
    if (value % 1.0 == 0.0) return value.toLong().toString()
    return value.toString()
}
