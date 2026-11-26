package com.culinary.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.culinary.core.common.Clock
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.DefaultShelfLifeUnit
import com.culinary.core.domain.repository.InventoryArchiveRepository
import com.culinary.core.domain.rule.inventory.calculateDefaultExpiryDate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class ExpiryScanTestUiState(
    val isRunning: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ExpiryScanTestViewModel @Inject constructor(
    private val repository: InventoryArchiveRepository,
    private val clock: Clock,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExpiryScanTestUiState())
    val uiState: StateFlow<ExpiryScanTestUiState> = _uiState.asStateFlow()

    fun notificationPermissionDenied() {
        _uiState.value = ExpiryScanTestUiState(message = "未授予通知权限，扫描测试未启动。请在系统设置中允许 MISEBASE 发送通知。")
    }

    fun scanInventory() {
        if (_uiState.value.isRunning) return
        viewModelScope.launch {
            _uiState.value = ExpiryScanTestUiState(isRunning = true)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val archives = when (val value = repository.listArchives()) {
                        is Outcome.Failure -> error(value.error.toString())
                        is Outcome.Success -> value.value
                    }
                    val batches = when (val value = repository.listLiveBatches()) {
                        is Outcome.Failure -> error(value.error.toString())
                        is Outcome.Success -> value.value
                    }
                    val today = clock.now().atZone(ZoneId.systemDefault()).toLocalDate()
                    val archiveById = archives.associateBy { it.id }
                    val expired = batches.filter { batch ->
                        if (batch.onHand <= 0.0) return@filter false
                        val archive = batch.archiveId?.let(archiveById::get) ?: return@filter false
                        val calculatedExpiry = if (archive.shelfLifeEnabled) {
                            calculateDefaultExpiryDate(
                                productionDate = batch.productionDate,
                                shelfLifeValue = archive.defaultShelfLifeValue ?: archive.shelfLifeDays,
                                shelfLifeUnit = archive.defaultShelfLifeUnit ?: DefaultShelfLifeUnit.DAY,
                            )
                        } else {
                            null
                        }
                        val expiry = calculatedExpiry ?: batch.expiresOn
                        expiry != null && !expiry.isAfter(today)
                    }
                    val names = expired.mapNotNull { it.archiveId?.let(archiveById::get)?.name }.distinct().sorted()
                    val alert = if (expired.isEmpty()) null else InventoryExpiryAlert(
                        expiredProductCount = names.size,
                        expiredBatchCount = expired.size,
                        archiveNames = names,
                        scannedAtMillis = clock.now().toEpochMilli(),
                    )
                    alert to expired.size
                }
            }
            if (result.isFailure) {
                _uiState.value = ExpiryScanTestUiState(
                    message = "扫描失败：${result.exceptionOrNull()?.message ?: "库存读取失败"}",
                )
            } else {
                val (alert, batchCount) = result.getOrThrow()
                ExpiryAlertStore.save(alert)
                scheduleNotification(alert?.expiredProductCount ?: 0, batchCount)
                _uiState.value = ExpiryScanTestUiState(
                    message = if (alert == null) {
                        "扫描完成：未发现过期库存。测试通知将在 3 分钟后发送。"
                    } else {
                        "扫描完成：发现 ${alert.expiredProductCount} 种商品、${alert.expiredBatchCount} 个过期批次。测试通知将在 3 分钟后发送。"
                    },
                )
            }
        }
    }

    private fun scheduleNotification(productCount: Int, batchCount: Int) {
        val request = OneTimeWorkRequestBuilder<ExpiryTestNotificationWorker>()
            .setInitialDelay(3, TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    ExpiryTestNotificationWorker.KEY_PRODUCT_COUNT to productCount,
                    ExpiryTestNotificationWorker.KEY_BATCH_COUNT to batchCount,
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ExpiryTestNotificationWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
