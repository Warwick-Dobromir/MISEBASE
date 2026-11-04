package com.culinary.core.domain.usecase.scan

import com.culinary.core.common.Outcome
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.scan.ScanImportKey
import com.culinary.core.domain.scan.ScanPreviousImport

/**
 * 查询某个条码此前的导入记录（只读，MB-S5-002 修正）。
 *
 * 事件级幂等键无法再"按一个固定键是否命中"来判断重复，因此改为扫描流水集合，
 * 取属于该条码（键前缀匹配）且 `occurredAt` 最新的一条。命中只用于提示
 * "此前已导入，是否再次入库"，**不再阻止**再次入库。
 *
 * 查询失败按失败返回，不吞掉，避免把"查不到"当成"没导入"。
 */
class LookupScanImportUseCase(
    private val inventoryRepository: InventoryRepository,
) {
    suspend operator fun invoke(capture: ScanCapture): Outcome<ScanPreviousImport?> {
        val ledger =
            when (val all = inventoryRepository.ledger()) {
                is Outcome.Failure -> return all
                is Outcome.Success -> all.value
            }
        val latest =
            ledger
                .filter { ScanImportKey.isForBarcode(it.idempotencyKey, capture) }
                .maxByOrNull { it.occurredAt }
                ?: return Outcome.success(null)
        return when (val item = inventoryRepository.findItem(latest.batchId)) {
            is Outcome.Failure -> item
            is Outcome.Success -> Outcome.success(ScanPreviousImport(entry = latest, item = item.value))
        }
    }
}
