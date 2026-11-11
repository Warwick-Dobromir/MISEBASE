package com.culinary.feature.inventory

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.domain.model.BatchLifecycleStatus
import com.culinary.core.domain.model.IssueRole
import com.culinary.core.domain.model.LedgerEntryType
import com.culinary.core.domain.model.PrintStatus
import com.culinary.core.domain.model.StockStatus
import com.culinary.core.domain.model.TemporaryStatus

/** 库存 v9 的统一文案与格式化；页面不各自硬编码中文。 */
object InventoryText {
    fun stockStatus(status: StockStatus): String =
        when (status) {
            StockStatus.NORMAL -> "正常"
            StockStatus.LOW -> "低库存"
            StockStatus.OUT -> "不足"
        }

    fun stockTone(status: StockStatus): StatusTone =
        when (status) {
            StockStatus.NORMAL -> StatusTone.Success
            StockStatus.LOW -> StatusTone.Warning
            StockStatus.OUT -> StatusTone.Danger
        }

    fun batchStatus(status: BatchLifecycleStatus): String =
        when (status) {
            BatchLifecycleStatus.ACTIVE -> "正常"
            BatchLifecycleStatus.FROZEN -> "已冻结"
            BatchLifecycleStatus.RECYCLED -> "回收站"
            BatchLifecycleStatus.DEPLETED -> "已耗尽"
            BatchLifecycleStatus.EXPIRED -> "已过期"
            BatchLifecycleStatus.DISCARDED -> "已报废"
        }

    fun batchTone(status: BatchLifecycleStatus): StatusTone =
        when (status) {
            BatchLifecycleStatus.ACTIVE -> StatusTone.Success
            BatchLifecycleStatus.FROZEN -> StatusTone.Warning
            BatchLifecycleStatus.RECYCLED -> StatusTone.Neutral
            BatchLifecycleStatus.DEPLETED, BatchLifecycleStatus.EXPIRED, BatchLifecycleStatus.DISCARDED ->
                StatusTone.Danger
        }

    fun ledgerType(type: LedgerEntryType): String =
        when (type) {
            LedgerEntryType.PURCHASE_RECEIPT -> "入库"
            LedgerEntryType.PRODUCTION_ISSUE -> "出库"
            LedgerEntryType.RESERVE -> "预留"
            LedgerEntryType.RELEASE -> "释放"
            LedgerEntryType.ADJUSTMENT_IN -> "盘盈"
            LedgerEntryType.ADJUSTMENT_OUT -> "盘亏"
            LedgerEntryType.WASTE -> "损耗"
            LedgerEntryType.TRANSFER_OUT -> "调出"
            LedgerEntryType.TRANSFER_IN -> "调入"
            LedgerEntryType.RETURN -> "退货"
            LedgerEntryType.VOID_RECEIPT -> "冲销"
            LedgerEntryType.FREEZE -> "冻结"
            LedgerEntryType.UNFREEZE -> "解冻"
            LedgerEntryType.LOCATION_TRANSFER -> "库位转移"
        }

    fun issueRole(role: IssueRole): String =
        when (role) {
            IssueRole.INGREDIENT -> "食材"
            IssueRole.SAUCE -> "酱汁现货"
            IssueRole.SAUCE_GAP -> "酱汁补料"
        }

    fun printStatus(status: PrintStatus): String =
        when (status) {
            PrintStatus.PENDING -> "待打印"
            PrintStatus.PRINTED -> "已打印"
            PrintStatus.FAILED -> "打印失败"
        }

    fun temporaryStatus(status: TemporaryStatus): String =
        when (status) {
            TemporaryStatus.NORMAL -> "正常"
            TemporaryStatus.REMIND -> "需处理"
            TemporaryStatus.PENDING -> "待处理"
        }
}

/** 数量文本：去掉多余的 0，保留单位。 */
@Composable
fun QuantityInline(
    amount: Double,
    symbol: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "${formatAmount(amount)} $symbol",
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}
