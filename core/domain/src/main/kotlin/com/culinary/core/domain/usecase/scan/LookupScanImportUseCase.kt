package com.culinary.core.domain.usecase.scan

import com.culinary.core.common.Outcome
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.scan.ScanImportKey
import com.culinary.core.domain.scan.ScanPreviousImport

/**
 * 查询某个条码是否已经导入过（只读）。
 *
 * 确认页用它在写入前展示"该条码已导入"：因为幂等键由条码决定，命中既有流水即证明
 * 重复确认不会再次入库。查询失败按失败返回，不吞掉，避免把"查不到"当成"没导入"。
 */
class LookupScanImportUseCase(
    private val inventoryRepository: InventoryRepository,
) {
    suspend operator fun invoke(capture: ScanCapture): Outcome<ScanPreviousImport?> {
        val key = ScanImportKey.of(capture)
        val entry =
            when (val existing = inventoryRepository.findByIdempotencyKey(key)) {
                is Outcome.Failure -> return existing
                is Outcome.Success -> existing.value ?: return Outcome.success(null)
            }
        return when (val item = inventoryRepository.findItem(entry.batchId)) {
            is Outcome.Failure -> item
            is Outcome.Success -> Outcome.success(ScanPreviousImport(entry = entry, item = item.value))
        }
    }
}
