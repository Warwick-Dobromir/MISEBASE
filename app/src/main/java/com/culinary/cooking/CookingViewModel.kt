package com.culinary.cooking

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ArchiveImageOwner
import com.culinary.core.domain.model.KitchenStep
import com.culinary.core.domain.model.KitchenStepCodec
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.usecase.archive.ArchiveImageUseCase
import com.culinary.core.domain.usecase.recipe.GetRecipeDetailUseCase
import com.culinary.core.domain.usecase.sauce.GetSauceDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 制作模式来源；决定返回文案与图片归属。 */
enum class CookingSource {
    SAUCE,
    RECIPE,
    ;

    val archiveLabel: String
        get() = if (this == SAUCE) "酱汁档案" else "菜谱档案"

    val imageOwner: ArchiveImageOwner
        get() = if (this == SAUCE) ArchiveImageOwner.SAUCE_VERSION else ArchiveImageOwner.RECIPE_VERSION
}

data class CookingUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val source: CookingSource = CookingSource.RECIPE,
    val title: String = "",
    val englishTitle: String? = null,
    val plan: List<CookingPlanStep> = emptyList(),
    val index: Int = 0,
    /** 当前步骤剩余秒数；进入页面不自动开始。 */
    val remainingSeconds: Int = 0,
    val presetSeconds: Int = 0,
    val isRunning: Boolean = false,
    val finished: Boolean = false,
    /** 当前需要阻断确认的无时间步骤；null 表示没有弹窗。 */
    val blockingStep: CookingPlanStep? = null,
    val stepImages: Map<Int, ByteArray> = emptyMap(),
) {
    val current: CookingPlanStep? get() = plan.getOrNull(index)
    val lastTimedStep: CookingPlanStep? get() = plan.indexOfLast { it.hasTime }.takeIf { it >= 0 }?.let(plan::get)
    val progress: Float get() = if (finished) 1f else current?.let { CookingPlan.progress(it.number, it.total) } ?: 0f
}

/**
 * 统一制作模式：菜谱与酱汁共用。
 *
 * 计时以单调时钟（elapsedRealtime）为准，UI 刷新只负责显示，
 * 因此滚动、重组或暂停不会让倒计时漂移；离开页面即停止计时任务。
 */
@HiltViewModel
class CookingViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val getSauceDetail: GetSauceDetailUseCase,
        private val getRecipeDetail: GetRecipeDetailUseCase,
        private val archiveImages: ArchiveImageUseCase = ArchiveImageUseCase(),
    ) : ViewModel() {
        private val source =
            if (savedStateHandle.get<String>(CookingRoute.KIND_ARG) == CookingRoute.KIND_SAUCE) {
                CookingSource.SAUCE
            } else {
                CookingSource.RECIPE
            }
        private val id: String = savedStateHandle.get<String>(CookingRoute.ID_ARG).orEmpty()
        private val startStep: Int = savedStateHandle.get<Int>(CookingRoute.STEP_ARG) ?: 1

        private val _uiState = MutableStateFlow(CookingUiState(source = source))
        val uiState: StateFlow<CookingUiState> = _uiState.asStateFlow()

        /** 基准剩余秒数：开始/恢复时为当时的剩余量。 */
        private var baseRemaining: Int = 0
        private var startedAtMillis: Long = 0L
        private var tickJob: Job? = null

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                when (source) {
                    CookingSource.SAUCE -> loadSauce()
                    CookingSource.RECIPE -> loadRecipe()
                }
            }
        }

        private suspend fun loadSauce() {
            when (val result = getSauceDetail(SauceId(id))) {
                is Outcome.Failure -> fail("酱汁档案读取失败，无法进入制作模式。")
                is Outcome.Success -> {
                    val detail = result.value
                    val version =
                        detail.versions.firstOrNull {
                            it.status == com.culinary.core.domain.model.VersionStatus.DRAFT
                        } ?: detail.currentVersion ?: detail.versions.firstOrNull()
                    val steps = decodeSteps(version?.stepsJson, version?.instructions)
                    val images =
                        version
                            ?.let {
                                runCatching { archiveImages.stepImages(source.imageOwner, it.id.value) }
                                    .getOrDefault(emptyMap())
                            }.orEmpty()
                    start(
                        title = detail.sauce.name,
                        englishTitle = detail.sauce.englishName,
                        steps = steps,
                        images = images,
                    )
                }
            }
        }

        private suspend fun loadRecipe() {
            when (val result = getRecipeDetail(RecipeId(id))) {
                is Outcome.Failure -> fail("菜谱档案读取失败，无法进入制作模式。")
                is Outcome.Success -> {
                    val detail = result.value
                    val version =
                        detail.versions.firstOrNull {
                            it.status == com.culinary.core.domain.model.VersionStatus.DRAFT
                        } ?: detail.currentVersion ?: detail.versions.firstOrNull()
                    val steps = decodeSteps(version?.stepsJson, version?.instructions)
                    val images =
                        version
                            ?.let {
                                runCatching { archiveImages.stepImages(source.imageOwner, it.id.value) }
                                    .getOrDefault(emptyMap())
                            }.orEmpty()
                    start(
                        title = detail.recipe.name,
                        englishTitle = detail.recipe.englishName,
                        steps = steps,
                        images = images,
                    )
                }
            }
        }

        private fun decodeSteps(
            json: String?,
            instructions: String?,
        ): List<KitchenStep> {
            var key = 0
            return runCatching {
                KitchenStepCodec.decode(json, instructions) { "step-${key++}" }
            }.getOrDefault(emptyList())
        }

        private fun start(
            title: String,
            englishTitle: String?,
            steps: List<KitchenStep>,
            images: Map<Int, ByteArray>,
        ) {
            val plan = CookingPlan.build(steps, startStep - 1)
            if (plan.isEmpty()) {
                fail("该步骤没有有效的制作流程，无法启动制作模式。")
                return
            }
            val first = plan.first()
            baseRemaining = first.step.timeSeconds
            _uiState.update {
                it.copy(
                    isLoading = false,
                    title = title,
                    englishTitle = englishTitle,
                    plan = plan,
                    index = 0,
                    remainingSeconds = if (first.hasTime) first.step.timeSeconds else 0,
                    presetSeconds = first.step.timeSeconds,
                    isRunning = false,
                    finished = false,
                    blockingStep = if (first.hasTime) null else first,
                    stepImages = images,
                )
            }
        }

        fun toggleTimer() {
            val state = _uiState.value
            if (state.finished || state.blockingStep != null) return
            val current = state.current ?: return
            if (!current.hasTime) return
            if (state.isRunning) {
                pause()
            } else {
                val resumeFrom = if (state.remainingSeconds > 0) state.remainingSeconds else current.step.timeSeconds
                baseRemaining = resumeFrom
                startedAtMillis = SystemClock.elapsedRealtime()
                _uiState.update { it.copy(isRunning = true, remainingSeconds = resumeFrom) }
                startTicking()
            }
        }

        private fun pause() {
            tickJob?.cancel()
            val remaining = remainingNow()
            baseRemaining = remaining
            _uiState.update { it.copy(isRunning = false, remainingSeconds = remaining) }
        }

        /** 调整剩余时间：不改档案预设时间，只改本次制作。 */
        fun adjust(deltaSeconds: Int) {
            val state = _uiState.value
            if (state.finished || state.blockingStep != null) return
            val current = state.current ?: return
            if (!current.hasTime) return
            val adjusted =
                (remainingNow() + deltaSeconds)
                    .coerceIn(0, com.culinary.core.common.KitchenTime.MAX_SECONDS)
            baseRemaining = adjusted
            startedAtMillis = SystemClock.elapsedRealtime()
            _uiState.update { it.copy(remainingSeconds = adjusted) }
        }

        private fun remainingNow(): Int {
            val state = _uiState.value
            if (!state.isRunning) return state.remainingSeconds
            val elapsed = ((SystemClock.elapsedRealtime() - startedAtMillis) / 1000L).toInt()
            return (baseRemaining - elapsed).coerceAtLeast(0)
        }

        private fun startTicking() {
            tickJob?.cancel()
            tickJob =
                viewModelScope.launch {
                    while (true) {
                        delay(TICK_MILLIS)
                        val state = _uiState.value
                        if (!state.isRunning) break
                        val remaining = remainingNow()
                        if (remaining <= 0) {
                            _uiState.update { it.copy(remainingSeconds = 0, isRunning = false) }
                            onTimedStepFinished()
                            break
                        }
                        _uiState.update { it.copy(remainingSeconds = remaining) }
                    }
                }
        }

        /** 计时结束：推进到下一制作步骤；到最后一个有效计时步骤即结束。 */
        private fun onTimedStepFinished() {
            val state = _uiState.value
            val current = state.current ?: return
            val nextIndex = state.index + 1
            if (!current.hasTime) return
            if (nextIndex > state.plan.lastIndex) {
                finish()
                return
            }
            val next = state.plan[nextIndex]
            baseRemaining = next.step.timeSeconds
            _uiState.update {
                it.copy(
                    index = nextIndex,
                    remainingSeconds = next.step.timeSeconds,
                    presetSeconds = next.step.timeSeconds,
                    isRunning = false,
                    blockingStep = if (next.hasTime) null else next,
                )
            }
        }

        private fun finish() {
            tickJob?.cancel()
            _uiState.update { it.copy(finished = true, isRunning = false, remainingSeconds = 0) }
        }

        /** 确认无时间步骤：逐个推进，绝不跳过。 */
        fun confirmBlockingStep() {
            val state = _uiState.value
            val blocking = state.blockingStep ?: return
            if (state.plan.getOrNull(state.index)?.number != blocking.number) return
            val nextIndex = state.index + 1
            if (nextIndex > state.plan.lastIndex) {
                _uiState.update { it.copy(blockingStep = null) }
                finish()
                return
            }
            val next = state.plan[nextIndex]
            baseRemaining = next.step.timeSeconds
            _uiState.update {
                it.copy(
                    index = nextIndex,
                    remainingSeconds = next.step.timeSeconds,
                    presetSeconds = next.step.timeSeconds,
                    isRunning = false,
                    blockingStep = if (next.hasTime) null else next,
                )
            }
        }

        /** 手动下一阶段：遇到无时间步骤同样弹窗阻断。 */
        fun next() {
            val state = _uiState.value
            if (state.finished || state.blockingStep != null) return
            if (state.isRunning) pause()
            val nextIndex = state.index + 1
            if (nextIndex > state.plan.lastIndex) return
            val next = state.plan[nextIndex]
            baseRemaining = next.step.timeSeconds
            _uiState.update {
                it.copy(
                    index = nextIndex,
                    remainingSeconds = next.step.timeSeconds,
                    presetSeconds = next.step.timeSeconds,
                    isRunning = false,
                    blockingStep = if (next.hasTime) null else next,
                )
            }
        }

        /** 上一阶段：回到最近的有时长步骤，不重复触发已完成的无时间步骤。 */
        fun previous() {
            val state = _uiState.value
            if (state.finished || state.blockingStep != null) return
            val previousIndex = (state.index - 1 downTo 0).firstOrNull { state.plan[it].hasTime } ?: return
            if (state.isRunning) pause()
            val previous = state.plan[previousIndex]
            baseRemaining = previous.step.timeSeconds
            _uiState.update {
                it.copy(
                    index = previousIndex,
                    remainingSeconds = previous.step.timeSeconds,
                    presetSeconds = previous.step.timeSeconds,
                    isRunning = false,
                )
            }
        }

        /** 退出制作模式时定位回原始步骤。 */
        fun exitStepNo(): Int =
            if (_uiState.value.finished) {
                _uiState.value.lastTimedStep?.originalStepNo ?: startStep
            } else {
                _uiState.value.current?.originalStepNo ?: startStep
            }

        private fun fail(message: String) {
            _uiState.update { it.copy(isLoading = false, error = message) }
        }

        override fun onCleared() {
            tickJob?.cancel()
            super.onCleared()
        }

        private companion object {
            const val TICK_MILLIS = 200L
        }
    }
