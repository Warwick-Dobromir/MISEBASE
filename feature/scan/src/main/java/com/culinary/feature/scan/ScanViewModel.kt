package com.culinary.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Clock
import com.culinary.core.common.Outcome
import com.culinary.core.database.dao.TemporaryBarcodeDao
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.barcode.ScanFrameGate
import com.culinary.core.domain.barcode.ScanSource
import com.culinary.core.domain.repository.InventoryArchiveRepository
import com.culinary.core.domain.repository.PendingStockDraft
import com.culinary.core.domain.repository.PendingStockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 扫码页状态机。
 *
 * 条码命中已有库存档案时，仅显示短暂的档案提示并继续扫描；
 * 未命中时发出一次性 pendingCapture，由 app 直接进入新建库存档案。
 * 本 ViewModel 不创建档案、不写库存数量。
 */
@HiltViewModel
class ScanViewModel
    @Inject
    constructor(
        private val gate: ScanFrameGate,
        private val clock: Clock,
        private val archiveRepository: InventoryArchiveRepository?,
        private val pendingStockRepository: PendingStockRepository? = null,
        private val temporaryBarcodeDao: TemporaryBarcodeDao? = null,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ScanUiState())
        val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

        private var dismissMatchJob: Job? = null
        private val feedbackDismissJobs = mutableMapOf<Long, Job>()
        private var feedbackSequence = 0L

        fun onToggleMode() {
            if (_uiState.value.lookupInProgress) return
            gate.reset()
            dismissMatchJob?.cancel()
            cancelFeedbackDismissJobs()
            _uiState.update { state ->
                state.copy(
                    mode = if (state.mode == ScanMode.STOCK_IN) ScanMode.VIEW_DETAILS else ScanMode.STOCK_IN,
                    matchedArchive = null,
                    successFeedbacks = emptyList(),
                    openArchiveId = null,
                    openBatchId = null,
                    status = null,
                    error = null,
                )
            }
        }

        fun onArchiveNavigationConsumed() {
            _uiState.update { it.copy(openArchiveId = null) }
        }

        fun onBatchNavigationConsumed() {
            _uiState.update { it.copy(openBatchId = null) }
        }

        fun onCameraAvailability(available: Boolean) {
            _uiState.update { state ->
                state.copy(
                    hasCamera = available,
                    manualEntry = if (available) state.manualEntry else true,
                    cameraAccess =
                        when {
                            !available -> CameraAccessState.UNAVAILABLE
                            state.cameraAccess == CameraAccessState.UNAVAILABLE -> CameraAccessState.NOT_REQUESTED
                            else -> state.cameraAccess
                        },
                    scanning = available && state.cameraAccess == CameraAccessState.GRANTED,
                )
            }
        }

        fun onPermissionChecked(granted: Boolean) {
            _uiState.update { state ->
                if (granted) {
                    state.copy(
                        cameraAccess = CameraAccessState.GRANTED,
                        scanning = state.hasCamera,
                        manualEntry = false,
                        error = null,
                    )
                } else {
                    state.copy(
                        cameraAccess =
                            if (state.hasCamera) CameraAccessState.NOT_REQUESTED else CameraAccessState.UNAVAILABLE,
                        scanning = false,
                    )
                }
            }
        }

        fun onPermissionResult(granted: Boolean) {
            if (granted) {
                gate.reset()
                _uiState.update { state ->
                    state.copy(
                        cameraAccess = CameraAccessState.GRANTED,
                        scanning = state.hasCamera,
                        manualEntry = false,
                        error = null,
                    )
                }
            } else {
                _uiState.update { state ->
                    state.copy(
                        cameraAccess = CameraAccessState.DENIED,
                        scanning = false,
                        error = null,
                    )
                }
            }
        }

        /** 相机帧被接受后先暂停分析，查询档案；相机预览本身保持显示。 */
        fun onCameraFrame(capture: ScanCapture) {
            val state = _uiState.value
            if (!state.showCamera || !state.scanning) return
            if (!gate.offer(capture.normalizedValue, clock.now().toEpochMilli())) return
            _uiState.update {
                it.copy(
                    scanning = false,
                    lookupInProgress = true,
                    torchOn = false,
                    status = null,
                    error = null,
                    pendingCapture = null,
                )
            }
            resolveCapture(capture.copy(source = ScanSource.CAMERA))
        }

        private fun resolveCapture(capture: ScanCapture) {
            val repository = archiveRepository
            // Null is used only by isolated ViewModel tests; production Hilt injects the repository.
            if (repository == null) {
                if (_uiState.value.mode == ScanMode.VIEW_DETAILS) {
                    _uiState.update { state ->
                        state.copy(
                            lookupInProgress = false,
                            scanning = state.hasCamera && state.cameraAccess == CameraAccessState.GRANTED,
                            error = "当前无法查询库存档案，请重试。",
                        )
                    }
                } else {
                    _uiState.update { it.copy(lookupInProgress = false, pendingCapture = capture) }
                }
                return
            }
            viewModelScope.launch {
                val temporaryBarcode = temporaryBarcodeDao?.findAllocatedByBarcode(capture.rawValue.trim())
                if (temporaryBarcode?.batchId != null) {
                    gate.resume()
                    _uiState.update { state ->
                        state.copy(
                            lookupInProgress = false,
                            scanning = false,
                            pendingCapture = null,
                            matchedArchive = null,
                            openBatchId = temporaryBarcode.batchId,
                            error = null,
                        )
                    }
                    return@launch
                }
                when (val result = repository.listArchives()) {
                    is Outcome.Failure -> {
                        gate.resume()
                        _uiState.update { state ->
                            state.copy(
                                lookupInProgress = false,
                                scanning = state.hasCamera && state.cameraAccess == CameraAccessState.GRANTED,
                                error = "查询库存档案失败，请重试。",
                            )
                        }
                    }

                    is Outcome.Success -> {
                        val archive =
                            result.value.firstOrNull {
                                it.barcode?.trim()?.equals(capture.rawValue.trim(), ignoreCase = false) == true
                            }
                        if (archive == null) {
                            if (_uiState.value.mode == ScanMode.VIEW_DETAILS) {
                                gate.resume()
                                _uiState.update { state ->
                                    state.copy(
                                        lookupInProgress = false,
                                        scanning = state.hasCamera && state.cameraAccess == CameraAccessState.GRANTED,
                                        error = "未找到对应库存档案。",
                                    )
                                }
                            } else {
                                _uiState.update { it.copy(lookupInProgress = false, pendingCapture = capture) }
                            }
                            return@launch
                        }

                        if (_uiState.value.mode == ScanMode.VIEW_DETAILS) {
                            gate.resume()
                            _uiState.update { state ->
                                state.copy(
                                    lookupInProgress = false,
                                    scanning = false,
                                    matchedArchive = null,
                                    openArchiveId = archive.id,
                                    status = null,
                                    error = null,
                                )
                            }
                            return@launch
                        }

                        val pendingRepository = pendingStockRepository
                        val added =
                            pendingRepository != null &&
                                runCatching {
                                    val links =
                                        when (val linkResult = repository.archiveLinks(archive.id)) {
                                            is Outcome.Success -> linkResult.value
                                            is Outcome.Failure -> emptyList()
                                        }
                                    pendingRepository.addOrIncrement(
                                        PendingStockDraft(
                                            archiveId = archive.id,
                                            name = archive.name,
                                            brand = archive.brand,
                                            specification = archive.productSpecification,
                                            imageUri = archive.imageUri,
                                            stockUnitId = archive.stockUnit.id.value,
                                            sourceProductId = links.firstOrNull { it.isDefault }?.product?.id?.value
                                                ?: links.firstOrNull()?.product?.id?.value,
                                        ),
                                    )
                                }.isSuccess

                        gate.resume()
                        val feedback = ScanSuccessFeedback(
                            id = ++feedbackSequence,
                            name = archive.name,
                            brand = archive.brand,
                            specification = archive.productSpecification,
                            imageUri = archive.imageUri,
                            success = added,
                        )
                        val retainedIds = _uiState.value.successFeedbacks
                            .takeLast(MAX_SUCCESS_FEEDBACKS - 1)
                            .map { it.id }
                            .toSet()
                        _uiState.value.successFeedbacks
                            .filterNot { it.id in retainedIds }
                            .forEach { old -> feedbackDismissJobs.remove(old.id)?.cancel() }
                        _uiState.update { state ->
                            state.copy(
                                lookupInProgress = false,
                                scanning = state.hasCamera && state.cameraAccess == CameraAccessState.GRANTED,
                                manualEntry =
                                    if (state.hasCamera && state.cameraAccess == CameraAccessState.GRANTED) {
                                        false
                                    } else {
                                        state.manualEntry
                                    },
                                matchedArchive = archive,
                                successFeedbacks = (state.successFeedbacks + feedback).takeLast(MAX_SUCCESS_FEEDBACKS),
                                status = null,
                                error =
                                    when {
                                        added -> null
                                        pendingRepository == null -> "待入库服务不可用，请重试。"
                                        else -> "加入待入库失败，请重试。"
                                    },
                            )
                        }

                        scheduleFeedbackDismissal(feedback.id)
                        dismissMatchJob?.cancel()
                        dismissMatchJob =
                            viewModelScope.launch {
                                delay(MATCH_NOTICE_MILLIS)
                                _uiState.update { it.copy(matchedArchive = null) }
                            }
                    }
                }
            }
        }

        /** 返回扫码页时清除本次会话的去抖记录，允许重新识别同一条码。 */
        fun onResumeScanning() {
            gate.reset()
            dismissMatchJob?.cancel()
            cancelFeedbackDismissJobs()
            _uiState.update { state ->
                state.copy(
                    scanning = state.hasCamera && state.cameraAccess == CameraAccessState.GRANTED,
                    lookupInProgress = false,
                    pendingCapture = null,
                    matchedArchive = null,
                    successFeedbacks = emptyList(),
                    status = null,
                    error = null,
                    manualError = null,
                )
            }
        }

        fun onCaptureConsumed() {
            _uiState.update { it.copy(pendingCapture = null) }
        }

        fun onToggleTorch() {
            val state = _uiState.value
            if (state.cameraAccess != CameraAccessState.GRANTED || !state.hasCamera) return
            _uiState.update { it.copy(torchOn = !it.torchOn, torchNotice = null) }
        }

        fun onTorchResult(success: Boolean) {
            if (success) return
            _uiState.update { it.copy(torchOn = false, torchNotice = TORCH_UNSUPPORTED) }
        }

        fun onTorchNoticeShown() {
            _uiState.update { it.copy(torchNotice = null) }
        }

        fun onOpenManualEntry() {
            _uiState.update {
                it.copy(
                    manualEntry = true,
                    scanning = false,
                    manualError = null,
                    error = null,
                    status = null,
                )
            }
        }

        fun onCloseManualEntry() {
            _uiState.update {
                it.copy(
                    manualEntry = false,
                    manualError = null,
                    scanning = it.hasCamera && it.cameraAccess == CameraAccessState.GRANTED,
                )
            }
        }

        fun onManualCodeChange(value: String) {
            _uiState.update { it.copy(manualCode = value, manualError = null) }
        }

        /** 手工输入与相机识别走同一条档案匹配/新建分支。 */
        fun onSubmitManualCode() {
            val value = _uiState.value.manualCode
            val validation = ScanCapture.validate(value)
            if (validation != null) {
                _uiState.update { it.copy(manualError = validation) }
                return
            }
            val capture = ScanCapture(rawValue = value.trim(), format = null, source = ScanSource.MANUAL)
            _uiState.update {
                it.copy(
                    scanning = false,
                    lookupInProgress = true,
                    manualError = null,
                    error = null,
                    pendingCapture = null,
                )
            }
            resolveCapture(capture)
        }

        fun onError(message: String) {
            _uiState.update { it.copy(error = message) }
        }

        fun onDismissError() {
            _uiState.update { it.copy(error = null) }
        }

        private fun scheduleFeedbackDismissal(feedbackId: Long) {
            feedbackDismissJobs[feedbackId]?.cancel()
            feedbackDismissJobs[feedbackId] = viewModelScope.launch {
                delay(FEEDBACK_VISIBLE_MILLIS)
                _uiState.update { state ->
                    state.copy(
                        successFeedbacks = state.successFeedbacks.map { feedback ->
                            if (feedback.id == feedbackId) feedback.copy(visible = false) else feedback
                        },
                    )
                }
                delay(FEEDBACK_EXIT_MILLIS)
                _uiState.update { state ->
                    state.copy(successFeedbacks = state.successFeedbacks.filterNot { it.id == feedbackId })
                }
                feedbackDismissJobs.remove(feedbackId)
            }
        }

        private fun cancelFeedbackDismissJobs() {
            feedbackDismissJobs.values.forEach { it.cancel() }
            feedbackDismissJobs.clear()
        }

        private companion object {
            const val TORCH_UNSUPPORTED = "此设备不支持手电筒。"
            const val MATCH_NOTICE_MILLIS = 2_600L
            const val FEEDBACK_VISIBLE_MILLIS = 2_350L
            const val FEEDBACK_EXIT_MILLIS = 280L
            const val MAX_SUCCESS_FEEDBACKS = 3
        }
    }
