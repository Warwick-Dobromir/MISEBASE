package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.repository.PendingStockRepository
import com.culinary.core.domain.transaction.TransactionRunner

/** 将待入库清单一次性写入正式批次与库存流水；整单事务失败时保留原清单。 */
class ConfirmPendingStockUseCase(
    private val pending: PendingStockRepository,
    private val recordReceipt: RecordArchiveReceiptUseCase,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(): Outcome<List<ReceiptResult>> {
        val items = pending.list()
        if (items.isEmpty()) {
            return Outcome.failure(CulinaryError.Validation("pending_stock", "待入库清单为空"))
        }
        val incomplete = items.filter { it.unallocatedQuantity > 1e-9 }
        if (incomplete.isNotEmpty()) {
            val detail = incomplete.joinToString("；") { "${it.name} 尚有 ${it.unallocatedQuantity} 件未填写生产日期" }
            return Outcome.failure(CulinaryError.Validation("production_date", detail))
        }
        return transactionRunner.write {
            val results = mutableListOf<ReceiptResult>()
            items.forEach { item ->
                item.allocations.forEachIndexed { index, allocation ->
                    when (
                        val result = recordReceipt.execute(
                            ArchiveReceiptInput(
                                archiveId = item.archiveId,
                                quantity = allocation.quantity,
                                unitId = item.stockUnitId,
                                productionDate = allocation.productionDate,
                                sourceProductId = item.sourceProductId,
                                idempotencyKey = "pending-stock:${item.id}:${allocation.id}",
                            ),
                            "pending-stock:${item.id}:${allocation.id}",
                            index,
                        )
                    ) {
                        is Outcome.Failure -> return@write result
                        is Outcome.Success -> results += result.value
                    }
                }
            }
            pending.clear()
            Outcome.success(results)
        }.flatMap { it }
    }
}
