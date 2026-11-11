package com.culinary.feature.inventory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryButtonVariant
import com.culinary.core.designsystem.component.CulinaryFilterPill
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinaryColorScheme
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.InventoryArchiveRow
import com.culinary.core.domain.model.InventoryBatchRecord
import com.culinary.core.domain.model.IssuePlan
import com.culinary.core.domain.model.IssuePlanLine
import com.culinary.core.domain.model.IssueType
import com.culinary.core.domain.model.Unit as MeasureUnit
import com.culinary.core.domain.usecase.inventory.BuildManualIssuePlanUseCase
import com.culinary.core.domain.usecase.inventory.BuildRecipeIssuePlanUseCase
import com.culinary.core.domain.usecase.inventory.ConfirmIssueUseCase
import com.culinary.core.domain.usecase.inventory.GetInventoryArchiveDetailUseCase
import com.culinary.core.domain.usecase.inventory.IssueIntent
import com.culinary.core.domain.usecase.inventory.IssueIntentItem
import com.culinary.core.domain.usecase.inventory.ListArchiveFormOptionsUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryArchivesUseCase
import com.culinary.core.domain.usecase.recipe.ListRecipeOptionsUseCase
import com.culinary.core.domain.usecase.recipe.RecipeOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// 路由
// ---------------------------------------------------------------------------

/** 撕单触发阈值：向下拖动超过该距离，松手即出库。 */
internal val IssueTearThreshold = 180.dp

/**
 * 出库：`?archiveId=` → 手工出库；`?recipeVersionId=` → 菜谱出库。
 *
 * 出库单以热敏小票呈现，向下拖动超过阈值撕下并提交；未达阈值回弹且不扣库存。
 * 库存不足时底部上滑提示面板，撕单无效。
 */
@Composable
fun IssueRoute(
    onBack: () -> Unit,
    onPrint: (String, String) -> Unit,
    onOpenPickRecipe: ((String) -> Unit)? = null,
    viewModel: IssueViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.printRef) {
        val ref = state.printRef
        if (ref != null) {
            viewModel.consumePrint()
            onPrint("ISSUE", ref)
        }
    }
    IssueScreen(
        state = state,
        onBack = onBack,
        onModeChange = viewModel::setMode,
        onArchiveSelected = viewModel::selectArchive,
        onQuantityChange = viewModel::onQuantityChange,
        onUnitSelected = viewModel::selectUnit,
        onBatchSelected = viewModel::selectManualBatch,
        onRecipeSelected = viewModel::selectRecipeVersion,
        onServingsChange = viewModel::onServingsChange,
        onBuildPlan = viewModel::buildPlan,
        onTear = viewModel::submit,
        onOpenRecipe = onOpenPickRecipe,
        onReload = viewModel::load,
        onDismissError = viewModel::clearError,
    )
}

// ---------------------------------------------------------------------------
// 状态
// ---------------------------------------------------------------------------

enum class IssueMode {
    MANUAL,
    RECIPE,
}

/** 出库单页面状态：计划只读，确认时才在事务内重新分配。 */
data class IssueUiState(
    val mode: IssueMode = IssueMode.MANUAL,
    val archives: List<InventoryArchiveRow> = emptyList(),
    val units: List<MeasureUnit> = emptyList(),
    val batches: List<InventoryBatchRecord> = emptyList(),
    val recipes: List<RecipeOption> = emptyList(),
    val selectedArchiveId: String? = null,
    val quantity: String = "",
    val unitCode: String = "",
    val manualBatchId: String? = null,
    val servings: String = "1",
    val selectedRecipeVersionId: String? = null,
    val plan: IssuePlan? = null,
    val isLoading: Boolean = true,
    val isBuilding: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
    val confirmedSlipNo: String? = null,
    val printRef: String? = null,
    val error: String? = null,
) {
    val selectedArchiveName: String?
        get() = archives.firstOrNull { it.archive.id == selectedArchiveId }?.archive?.name

    val selectedUnitSymbol: String
        get() = units.firstOrNull { it.code == unitCode }?.symbol ?: unitCode

    val selectedBatchLabel: String?
        get() =
            batches.firstOrNull { it.id == manualBatchId }?.let { batch ->
                val code = batch.batchCode?.takeIf { it.isNotBlank() } ?: batch.id.take(8)
                "$code · 可用 ${formatAmount(batch.available)} ${batch.unit.symbol}"
            }

    val selectedRecipeName: String?
        get() =
            recipes
                .firstOrNull { it.currentVersion?.id?.value == selectedRecipeVersionId }
                ?.recipe
                ?.name

    val shortfalls: List<IssuePlanLine>
        get() = plan?.shortfalls.orEmpty()

    val canTear: Boolean
        get() = plan != null && plan.lines.isNotEmpty() && plan.isCovered && !isSubmitting && !submitted
}

/** 出库 ViewModel：构建计划 → 撕单确认；幂等键在进入页面时固定一次。 */
@HiltViewModel
class IssueViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val listArchives: ListInventoryArchivesUseCase,
        private val listFormOptions: ListArchiveFormOptionsUseCase,
        private val getArchiveDetail: GetInventoryArchiveDetailUseCase,
        private val listRecipes: ListRecipeOptionsUseCase,
        private val buildManualPlan: BuildManualIssuePlanUseCase,
        private val buildRecipePlan: BuildRecipeIssuePlanUseCase,
        private val confirmIssue: ConfirmIssueUseCase,
    ) : ViewModel() {
        private val routeArchiveId: String? =
            savedStateHandle.get<String>(InventoryRoutes.ARG_ARCHIVE_ID)?.takeIf { it.isNotBlank() }

        private val routeRecipeVersionId: String? =
            savedStateHandle.get<String>(InventoryRoutes.ARG_RECIPE_VERSION_ID)?.takeIf { it.isNotBlank() }

        /** 同一屏幕只生成一次：重复撕单 / 动画重复触发都命中幂等重放。 */
        private val idempotencyKey: String = UUID.randomUUID().toString()

        private val _uiState =
            MutableStateFlow(
                IssueUiState(
                    mode = if (routeRecipeVersionId != null) IssueMode.RECIPE else IssueMode.MANUAL,
                    selectedRecipeVersionId = routeRecipeVersionId,
                ),
            )
        val uiState: StateFlow<IssueUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val options =
                    when (val result = listFormOptions()) {
                        is Outcome.Failure -> {
                            _uiState.update {
                                it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                            }
                            return@launch
                        }

                        is Outcome.Success -> result.value
                    }
                val archives =
                    when (val result = listArchives()) {
                        is Outcome.Failure -> {
                            _uiState.update {
                                it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                            }
                            return@launch
                        }

                        is Outcome.Success -> result.value.rows
                    }
                val recipes = runCatching { listRecipes() }.getOrDefault(emptyList())
                val archive =
                    archives.firstOrNull { it.archive.id == routeArchiveId } ?: archives.firstOrNull()
                _uiState.update {
                    it.copy(
                        archives = archives,
                        units = options.units,
                        recipes = recipes,
                        isLoading = false,
                        selectedArchiveId = archive?.archive?.id,
                        unitCode = archive?.archive?.stockUnit?.code.orEmpty(),
                        servings =
                            recipes
                                .firstOrNull { it.currentVersion?.id?.value == routeRecipeVersionId }
                                ?.currentVersion
                                ?.servings
                                ?.toString()
                                ?: it.servings,
                    )
                }
                archive?.archive?.id?.let { loadBatches(it) }
                if (routeRecipeVersionId != null) buildPlan()
            }
        }

        private suspend fun loadBatches(archiveId: String) {
            val batches =
                when (val result = getArchiveDetail(archiveId)) {
                    is Outcome.Failure -> emptyList()
                    is Outcome.Success ->
                        result.value.batches
                            .filter { it.isLive && !it.isFrozen && it.available > 0.0 }
                            .sortedWith(compareBy({ it.fifoKey }, { it.id }))
                }
            _uiState.update { it.copy(batches = batches, manualBatchId = null) }
        }

        fun setMode(mode: IssueMode) {
            if (_uiState.value.mode == mode) return
            _uiState.update { it.copy(mode = mode, plan = null, error = null, submitted = false) }
        }

        fun selectArchive(archiveId: String) {
            val archive = _uiState.value.archives.firstOrNull { it.archive.id == archiveId }
            _uiState.update {
                it.copy(
                    selectedArchiveId = archiveId,
                    unitCode = archive?.archive?.stockUnit?.code ?: it.unitCode,
                    plan = null,
                    error = null,
                )
            }
            viewModelScope.launch { loadBatches(archiveId) }
        }

        fun onQuantityChange(value: String) {
            _uiState.update { it.copy(quantity = value, plan = null, error = null) }
        }

        fun selectUnit(code: String) {
            _uiState.update { it.copy(unitCode = code, plan = null, error = null) }
        }

        fun selectManualBatch(batchId: String?) {
            _uiState.update { it.copy(manualBatchId = batchId, plan = null) }
        }

        fun selectRecipeVersion(versionId: String) {
            val option = _uiState.value.recipes.firstOrNull { it.currentVersion?.id?.value == versionId }
            _uiState.update {
                it.copy(
                    selectedRecipeVersionId = versionId,
                    servings = option?.currentVersion?.servings?.toString() ?: it.servings,
                    plan = null,
                    error = null,
                )
            }
        }

        fun onServingsChange(value: String) {
            _uiState.update { it.copy(servings = value, plan = null, error = null) }
        }

        fun buildPlan() {
            val state = _uiState.value
            viewModelScope.launch {
                _uiState.update { it.copy(isBuilding = true, error = null) }
                val result =
                    when (state.mode) {
                        IssueMode.MANUAL -> buildManual()
                        IssueMode.RECIPE -> buildRecipe()
                    }
                when (result) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isBuilding = false, plan = null, error = InventoryErrorMessages.describe(result.error))
                        }

                    is Outcome.Success ->
                        _uiState.update { it.copy(isBuilding = false, plan = result.value, submitted = false) }
                }
            }
        }

        private suspend fun buildManual(): Outcome<IssuePlan> {
            val state = _uiState.value
            val archiveId =
                state.selectedArchiveId
                    ?: return Outcome.failure(invalid("archiveId", "请选择库存档案。"))
            val amount = state.quantity.trim().toDoubleOrNull()
            if (amount == null || !amount.isFinite() || amount <= 0.0) {
                return Outcome.failure(invalid("quantity", "数量必须是大于 0 的数字。"))
            }
            val unit =
                state.units.firstOrNull { it.code == state.unitCode }
                    ?: return Outcome.failure(invalid("unitId", "请选择单位。"))
            return buildManualPlan(archiveId, amount, unit.id.value, state.manualBatchId)
        }

        private suspend fun buildRecipe(): Outcome<IssuePlan> {
            val state = _uiState.value
            val versionId =
                state.selectedRecipeVersionId
                    ?: return Outcome.failure(invalid("recipeVersionId", "请选择菜谱。"))
            val servings = state.servings.trim().toDoubleOrNull()
            if (servings == null || !servings.isFinite() || servings <= 0.0) {
                return Outcome.failure(invalid("servings", "份数必须是大于 0 的数字。"))
            }
            return buildRecipePlan(
                com.culinary.core.domain.model.RecipeVersionId(versionId),
                servings,
            )
        }

        /** 撕单提交：只做一次；幂等键固定，重复触发不会重复扣减。 */
        fun submit() {
            val state = _uiState.value
            if (state.submitted || state.isSubmitting) return
            val plan = state.plan
            if (plan == null) {
                _uiState.update { it.copy(error = "请先生成出库单。") }
                return
            }
            if (!plan.isCovered) {
                _uiState.update { it.copy(error = "库存不足，无法出库。") }
                return
            }
            if (plan.lines.isEmpty()) {
                _uiState.update { it.copy(error = "出库单没有物料，无法提交。") }
                return
            }
            val items =
                plan.lines.map { line ->
                    IssueIntentItem(
                        archiveId = line.archiveId,
                        role = line.role,
                        quantity = line.required,
                        manualBatchId = state.manualBatchId.takeIf { plan.type == IssueType.MANUAL },
                    )
                }
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, error = null) }
                val result =
                    confirmIssue(
                        IssueIntent(
                            type = plan.type,
                            items = items,
                            idempotencyKey = idempotencyKey,
                            recipeVersionId = plan.recipeVersionId,
                            recipeName = plan.recipeName,
                            sauceGaps = plan.sauceGaps,
                        ),
                    )
                when (result) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isSubmitting = false, error = InventoryErrorMessages.describe(result.error))
                        }

                    is Outcome.Success ->
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                submitted = true,
                                confirmedSlipNo = result.value.slip.slipNo,
                                printRef = result.value.slip.id,
                            )
                        }
                }
            }
        }

        fun consumePrint() {
            _uiState.update { it.copy(printRef = null) }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }

        private fun invalid(
            field: String,
            message: String,
        ): com.culinary.core.common.CulinaryError =
            com.culinary.core.common.CulinaryError.Validation(field = field, rule = message)
    }

// ---------------------------------------------------------------------------
// 页面
// ---------------------------------------------------------------------------

private enum class IssuePicker {
    ARCHIVE,
    UNIT,
    BATCH,
    RECIPE,
}

@Suppress("LongMethod", "CyclomaticComplexMethod") // 表单 + 小票 + 不足面板共用一套状态，拆分只会重复参数。
@Composable
internal fun IssueScreen(
    state: IssueUiState,
    onBack: () -> Unit,
    onModeChange: (IssueMode) -> Unit,
    onArchiveSelected: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onUnitSelected: (String) -> Unit,
    onBatchSelected: (String?) -> Unit,
    onRecipeSelected: (String) -> Unit,
    onServingsChange: (String) -> Unit,
    onBuildPlan: () -> Unit,
    onTear: () -> Unit,
    onOpenRecipe: ((String) -> Unit)?,
    onReload: () -> Unit,
    onDismissError: () -> Unit,
) {
    var picker by remember { mutableStateOf<IssuePicker?>(null) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { IssueTearThreshold.toPx() }
    val maxOffsetPx = with(density) { 520.dp.toPx() }
    val minOffsetPx = with(density) { (-96).dp.toPx() }

    var offsetY by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var tearing by remember { mutableStateOf(false) }

    val animatedOffset by animateFloatAsState(
        targetValue = offsetY,
        animationSpec =
            when {
                dragging -> snap()
                tearing -> tween(durationMillis = 220)
                else -> spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
            },
        label = "issueSlipOffset",
    )

    // 计划变化或出错时复位，避免小票停在半撕状态。
    LaunchedEffect(state.plan) {
        offsetY = 0f
        tearing = false
    }
    LaunchedEffect(state.error) {
        if (state.error != null) {
            offsetY = 0f
            tearing = false
        }
    }
    LaunchedEffect(tearing) {
        if (tearing) {
            delay(220)
            onTear()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            state.error?.let { message ->
                ErrorBanner(
                    message = message,
                    onAction = onDismissError,
                    modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
                )
            }
            if (state.isLoading) {
                LoadingRows()
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = if (state.shortfalls.isEmpty()) CulinarySpacing.xl else 220.dp),
                ) {
                    PageHeader(
                        eyebrow = "ISSUE",
                        title = "出库",
                        supporting = "确认时在事务内按 FIFO 重新分配；冻结批次不参与出库。",
                        actions = { CulinaryTextButton(text = "返回", onClick = onBack) },
                    )
                    Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
                        state.confirmedSlipNo?.let { slipNo ->
                            StatusBadge(
                                text = "已出库 · 单据号 $slipNo",
                                tone = StatusTone.Success,
                                modifier = Modifier.padding(bottom = CulinarySpacing.sm),
                            )
                        }
                        if (state.archives.isEmpty() && state.recipes.isEmpty()) {
                            EmptyState(
                                title = "没有可出库的档案或菜谱",
                                description = "请先建立库存档案并录入库存，或选择已发布的菜谱。",
                                actionLabel = "重新加载",
                                onAction = onReload,
                            )
                            return@Column
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs)) {
                            CulinaryFilterPill(
                                text = "手工出库",
                                selected = state.mode == IssueMode.MANUAL,
                                onClick = { onModeChange(IssueMode.MANUAL) },
                            )
                            CulinaryFilterPill(
                                text = "菜谱出库",
                                selected = state.mode == IssueMode.RECIPE,
                                onClick = { onModeChange(IssueMode.RECIPE) },
                            )
                        }
                        SectionHeader(
                            title = if (state.mode == IssueMode.MANUAL) "手工出库" else "菜谱出库",
                            subtitle =
                                if (state.mode == IssueMode.MANUAL) {
                                    "选择档案、数量与单位，可指定批次；不指定时按 FIFO。"
                                } else {
                                    "填写份数后计算：先扣酱汁现货，缺口再按最小制作容量展开基础食材。"
                                },
                        )
                        if (state.mode == IssueMode.MANUAL) {
                            ManualIssueFields(
                                state = state,
                                onOpenPicker = { picker = it },
                                onQuantityChange = onQuantityChange,
                            )
                        } else {
                            RecipeIssueFields(
                                state = state,
                                onOpenPicker = { picker = it },
                                onServingsChange = onServingsChange,
                            )
                        }
                        CulinaryButton(
                            text = if (state.isBuilding) "正在生成" else "生成出库单",
                            onClick = onBuildPlan,
                            variant = CulinaryButtonVariant.Secondary,
                            enabled = !state.isBuilding && !state.isSubmitting,
                            loading = state.isBuilding,
                            modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md),
                        )
                        SectionHeader(title = "出库单", subtitle = "向下拖动超过 ${IssueTearThreshold.value.toInt()}dp 撕下即出库")
                        val plan = state.plan
                        if (plan == null) {
                            EmptyState(
                                title = "尚未生成出库单",
                                description = "填写出库信息后点击「生成出库单」，小票会显示在这里。",
                                compact = true,
                            )
                        } else {
                            IssueSlip(
                                plan = plan,
                                offsetY = animatedOffset,
                                canTear = state.canTear,
                                onDragStart = {
                                    dragging = true
                                    tearing = false
                                },
                                onDrag = { delta ->
                                    offsetY = (offsetY + delta).coerceIn(minOffsetPx, maxOffsetPx)
                                },
                                onDragEnd = {
                                    dragging = false
                                    if (offsetY >= thresholdPx && state.canTear) {
                                        tearing = true
                                        offsetY = maxOffsetPx
                                    } else {
                                        tearing = false
                                        offsetY = 0f
                                    }
                                },
                            )
                            if (!plan.isCovered) {
                                Text(
                                    text = "库存不足，撕单无法提交；请先补货或调整数量。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CulinaryColorScheme.current.danger,
                                    modifier = Modifier.padding(top = CulinarySpacing.xs),
                                )
                            } else if (state.mode == IssueMode.RECIPE) {
                                val versionId = plan.recipeVersionId
                                if (versionId != null && onOpenRecipe != null) {
                                    CulinaryTextButton(
                                        text = "查看该菜谱",
                                        onClick = { onOpenRecipe.invoke(versionId) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.shortfalls.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            ShortfallPanel(shortfalls = state.shortfalls)
        }
    }

    when (picker) {
        IssuePicker.ARCHIVE ->
            IssuePickerDialog(
                title = "库存档案",
                options = state.archives.map { it.archive.id to it.archive.name },
                emptyText = "还没有库存档案。",
                onSelect = {
                    onArchiveSelected(it)
                    picker = null
                },
                onDismiss = { picker = null },
            )

        IssuePicker.UNIT ->
            IssuePickerDialog(
                title = "单位",
                options = state.units.map { it.code to "${it.name}（${it.symbol}）" },
                emptyText = "没有可用单位。",
                onSelect = {
                    onUnitSelected(it)
                    picker = null
                },
                onDismiss = { picker = null },
            )

        IssuePicker.BATCH ->
            IssuePickerDialog(
                title = "指定批次",
                options =
                    listOf("" to "不指定（按 FIFO 自动扣减）") +
                        state.batches.map { batch ->
                            val code = batch.batchCode?.takeIf { it.isNotBlank() } ?: batch.id.take(8)
                            batch.id to "$code · 可用 ${formatAmount(batch.available)} ${batch.unit.symbol}"
                        },
                emptyText = "该档案没有可用批次（冻结批次不出库）。",
                onSelect = {
                    onBatchSelected(it.takeIf { value -> value.isNotEmpty() })
                    picker = null
                },
                onDismiss = { picker = null },
            )

        IssuePicker.RECIPE ->
            IssuePickerDialog(
                title = "选择菜谱",
                options =
                    state.recipes.mapNotNull { option ->
                        option.currentVersion?.let { version ->
                            version.id.value to
                                "${option.recipe.name} · ${version.servings} 份" +
                                if (version.isCurrent) "" else " · 非当前版本"
                        }
                    },
                emptyText = "没有已发布版本的菜谱。",
                onSelect = {
                    onRecipeSelected(it)
                    picker = null
                },
                onDismiss = { picker = null },
            )

        null -> Unit
    }
}

@Composable
private fun ManualIssueFields(
    state: IssueUiState,
    onOpenPicker: (IssuePicker) -> Unit,
    onQuantityChange: (String) -> Unit,
) {
    CulinaryButton(
        text = state.selectedArchiveName ?: "选择库存档案",
        onClick = { onOpenPicker(IssuePicker.ARCHIVE) },
        variant = CulinaryButtonVariant.Secondary,
        icon = CulinaryIcons.ChevronDown,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(modifier = Modifier.padding(top = CulinarySpacing.field), verticalAlignment = Alignment.CenterVertically) {
        CulinaryTextField(
            value = state.quantity,
            onValueChange = onQuantityChange,
            label = "数量 *",
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            fillWidth = false,
            modifier = Modifier.weight(1f),
        )
        CulinaryButton(
            text = state.selectedUnitSymbol.ifBlank { "单位" },
            onClick = { onOpenPicker(IssuePicker.UNIT) },
            variant = CulinaryButtonVariant.Secondary,
            modifier = Modifier.padding(start = CulinarySpacing.sm),
        )
    }
    CulinaryButton(
        text = state.selectedBatchLabel ?: "指定批次（可空，默认 FIFO）",
        onClick = { onOpenPicker(IssuePicker.BATCH) },
        variant = CulinaryButtonVariant.Secondary,
        icon = CulinaryIcons.ChevronDown,
        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md),
    )
}

@Composable
private fun RecipeIssueFields(
    state: IssueUiState,
    onOpenPicker: (IssuePicker) -> Unit,
    onServingsChange: (String) -> Unit,
) {
    CulinaryButton(
        text = state.selectedRecipeName ?: "选择菜谱",
        onClick = { onOpenPicker(IssuePicker.RECIPE) },
        variant = CulinaryButtonVariant.Secondary,
        icon = CulinaryIcons.ChevronDown,
        modifier = Modifier.fillMaxWidth(),
    )
    CulinaryTextField(
        value = state.servings,
        onValueChange = onServingsChange,
        label = "份数 *",
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.padding(top = CulinarySpacing.md),
    )
}

/** 热敏小票：白底、等宽排版、上边框细线；明细只显示前两项，其余固定两行文字。 */
@Suppress("LongMethod")
@Composable
private fun IssueSlip(
    plan: IssuePlan,
    offsetY: Float,
    canTear: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val mono = TextStyle(fontFamily = FontFamily.Monospace)
    val ink = Color(0xFF1F1F1F)
    val muted = Color(0xFF5C5C5C)
    val slipTime = remember(plan) {
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }
    val visibleLines = plan.lines.take(2)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, offsetY.roundToInt()) }
                .pointerInput(canTear) {
                    detectVerticalDragGestures(
                        onDragStart = { onDragStart() },
                        onVerticalDrag = { change, dragAmount ->
                            if (canTear) {
                                change.consume()
                                onDrag(dragAmount)
                            }
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                    )
                }
                .background(Color.White)
                .padding(horizontal = CulinarySpacing.md, vertical = CulinarySpacing.md),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ink))
        Text(
            text = "MISEBASE",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = muted,
            modifier = Modifier.padding(top = CulinarySpacing.sm),
        )
        Text(
            text = "库存出库单",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            color = ink,
            modifier = Modifier.padding(top = CulinarySpacing.xxs),
        )
        Text(
            text = "INVENTORY ISSUE SLIP",
            style = mono.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
            color = muted,
        )
        SlipRule(ink)
        SlipLine(label = "单号", value = "待生成", ink = ink, muted = muted)
        SlipLine(label = "时间", value = slipTime, ink = ink, muted = muted)
        SlipLine(
            label = "出库类型",
            value = if (plan.type == IssueType.RECIPE) "菜谱出库" else "手工出库",
            ink = ink,
            muted = muted,
        )
        SlipLine(label = "关联菜谱", value = plan.recipeName ?: "—", ink = ink, muted = muted)
        SlipRule(ink)
        if (visibleLines.isEmpty()) {
            Text(text = "明细为空", style = mono, color = muted)
        } else {
            visibleLines.forEachIndexed { index, line ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Text(
                        text = "${index + 1}",
                        style = mono,
                        color = muted,
                        modifier = Modifier.padding(end = CulinarySpacing.xs),
                    )
                    Text(
                        text = line.archiveName,
                        style = mono,
                        color = ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${formatAmount(line.required)} ${line.unit.symbol}",
                        style = mono,
                        color = ink,
                    )
                }
            }
        }
        if (plan.lines.size > visibleLines.size) {
            Text(
                text = "其他物料",
                style = mono,
                color = ink,
                modifier = Modifier.padding(top = CulinarySpacing.xs),
            )
            Text(
                text = "请查看具体打印单或库存流水",
                style = mono,
                color = muted,
            )
        }
        SlipRule(ink)
        SlipLine(label = "物料档案数", value = "${plan.totalLines}", ink = ink, muted = muted)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
            Text(text = "库存校验", style = mono, color = muted, modifier = Modifier.weight(1f))
            Text(
                text = if (plan.isCovered) "通过" else "不足",
                style = mono,
                color = if (plan.isCovered) ink else CulinaryColorScheme.current.danger,
            )
        }
        SlipRule(ink)
        Text(
            text = if (canTear) "向下拖动撕下小票，即提交出库" else "小票当前不可撕（库存不足或正在提交）",
            style = mono,
            color = muted,
            modifier = Modifier.padding(top = CulinarySpacing.xs),
        )
    }
}

@Composable
private fun SlipRule(ink: Color) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = CulinarySpacing.xs)
                .height(1.dp)
                .background(ink.copy(alpha = 0.25f)),
    )
}

@Composable
private fun SlipLine(
    label: String,
    value: String,
    ink: Color,
    muted: Color,
) {
    val mono = TextStyle(fontFamily = FontFamily.Monospace)
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            text = label,
            style = mono,
            color = muted,
            modifier = Modifier.padding(end = CulinarySpacing.xs),
        )
        Text(text = value, style = mono, color = ink)
    }
}

/** 库存不足面板：底部上滑，列出不足物品、所需、可用与缺少数量。 */
@Composable
private fun ShortfallPanel(shortfalls: List<IssuePlanLine>) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.sm),
    ) {
        CulinaryRule()
        Text(
            text = "库存不足，无法出库",
            style = MaterialTheme.typography.titleMedium,
            color = CulinaryColorScheme.current.danger,
            modifier = Modifier.padding(top = CulinarySpacing.sm),
        )
        shortfalls.forEach { line ->
            Column(modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.xs)) {
                Text(
                    text = line.archiveName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        "所需 ${formatAmount(line.required)} ${line.unit.symbol} · " +
                            "可用 ${formatAmount(line.allocated)} ${line.unit.symbol} · " +
                            "缺少 ${formatAmount(line.shortfall)} ${line.unit.symbol}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CulinaryRule(modifier = Modifier.padding(top = CulinarySpacing.xs))
        }
        Text(
            text = "撕单无效。请先补货，或减少数量后重新生成出库单。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = CulinarySpacing.xs),
        )
    }
}

@Composable
private fun IssuePickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    emptyText: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraSmall,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            if (options.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    options.forEach { (value, label) ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(value) }
                                    .padding(vertical = CulinarySpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        CulinaryRule()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
