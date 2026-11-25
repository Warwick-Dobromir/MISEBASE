package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.DefaultShelfLifeUnit
import com.culinary.core.domain.model.InventoryArchive
import com.culinary.core.domain.repository.InventoryArchiveRepository
import com.culinary.core.domain.repository.PendingStockRepository
import com.culinary.core.domain.transaction.TransactionRunner
import java.time.LocalDate

/** 将待入库清单一次性写入正式批次与库存流水；整单事务失败时保留原清单。 */
class ConfirmPendingStockUseCase(
    private val pending: PendingStockRepository,
    private val recordReceipt: RecordArchiveReceiptUseCase,
    private val archiveRepository: InventoryArchiveRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(): Outcome<List<ReceiptResult>> =
        transactionRunner.write {
            val items = pending.list()
            if (items.isEmpty()) {
                return@write Outcome.failure(CulinaryError.Validation("pending_stock", "待入库清单为空"))
            }
            val incomplete = items.filter { it.unallocatedQuantity > 1e-9 }
            if (incomplete.isNotEmpty()) {
                val detail = incomplete.joinToString("；") {
                    "${it.name} 尚有 ${it.unallocatedQuantity} 件未填写生产日期"
                }
                return@write Outcome.failure(CulinaryError.Validation("production_date", detail))
            }
            val results = mutableListOf<ReceiptResult>()
            items.forEach { item ->
                val archive =
                    when (val result = archiveRepository.findArchive(item.archiveId)) {
                        is Outcome.Failure -> return@write result
                        is Outcome.Success -> result.value
                            ?: return@write Outcome.failure(CulinaryError.NotFound("inventory_archive", item.archiveId))
                    }
                item.allocations.forEachIndexed { index, allocation ->
                    when (
                        val result = recordReceipt.execute(
                            ArchiveReceiptInput(
                                archiveId = item.archiveId,
                                quantity = allocation.quantity,
                                unitId = item.stockUnitId,
                                productionDate = allocation.productionDate,
                                expiresOn = expiryDate(archive, allocation.productionDate),
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

    private fun expiryDate(
        archive: InventoryArchive,
        productionDate: LocalDate,
    ): LocalDate? {
        if (!archive.shelfLifeEnabled) return null
        val value = archive.defaultShelfLifeValue ?: archive.shelfLifeDays ?: return null
        if (value <= 0) return null
        return when (archive.defaultShelfLifeUnit ?: DefaultShelfLifeUnit.DAY) {
            DefaultShelfLifeUnit.DAY -> productionDate.plusDays(value.toLong())
            DefaultShelfLifeUnit.MONTH -> productionDate.plusMonths(value.toLong())
            DefaultShelfLifeUnit.YEAR -> productionDate.plusYears(value.toLong())
        }
    }
}
