package com.culinary.core.domain.usecase.scan

import com.culinary.core.common.Outcome
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LedgerEntryId
import com.culinary.core.domain.model.LedgerEntryType
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.scan.ScanImportKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant

/** 条码导入查询：命中既有流水返回导入记录，未命中返回 `null`，失败不伪装成空。 */
class LookupScanImportUseCaseTest {
    private val gram = Unit(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 1)

    @Test
    fun `returns the previous import for the same code`() =
        runBlocking<kotlin.Unit> {
            val capture = ScanCapture(rawValue = "6901234567892")
            val entry = ledgerEntry()
            val item = inventoryItem()
            val useCase =
                LookupScanImportUseCase(
                    FakeInventoryRepository(
                        entries = mapOf(ScanImportKey.of(capture) to entry),
                        items = mapOf(entry.batchId to item),
                    ),
                )

            val result = useCase(capture)

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val previous = (result as Outcome.Success).value
            assertThat(previous?.entry).isEqualTo(entry)
            assertThat(previous?.item).isEqualTo(item)
        }

    @Test
    fun `returns null when the code was never imported`() =
        runBlocking<kotlin.Unit> {
            val useCase = LookupScanImportUseCase(FakeInventoryRepository())

            val result = useCase(ScanCapture(rawValue = "never-seen"))

            assertThat(result).isEqualTo(Outcome.success(null))
        }

    @Test
    fun `propagates repository failures`() =
        runBlocking<kotlin.Unit> {
            val failure =
                Outcome.failure(
                    com.culinary.core.common.CulinaryError
                        .Data(message = "db down"),
                )
            val useCase = LookupScanImportUseCase(FakeInventoryRepository(lookupFailure = failure))

            val result = useCase(ScanCapture(rawValue = "6901234567892"))

            assertThat(result).isEqualTo(failure)
        }

    private fun ledgerEntry(): InventoryLedgerEntry =
        InventoryLedgerEntry(
            id = LedgerEntryId("ledger-1"),
            batchId = BatchId("batch-1"),
            productId = ProductId("product-1"),
            locationId = LocationId("loc-1"),
            type = LedgerEntryType.PURCHASE_RECEIPT,
            stockUnit = gram,
            onHandDelta = 1.0,
            reservedDelta = 0.0,
            onHandAfter = 1.0,
            reservedAfter = 0.0,
            idempotencyKey = ScanImportKey.of("6901234567892"),
            occurredAt = Instant.ofEpochMilli(1_760_000_000_000L),
        )

    private fun inventoryItem(): InventoryItem =
        InventoryItem(
            batchId = BatchId("batch-1"),
            productId = ProductId("product-1"),
            locationId = LocationId("loc-1"),
            stockUnit = gram,
            onHand = 1.0,
        )

    private class FakeInventoryRepository(
        private val entries: Map<String, InventoryLedgerEntry> = emptyMap(),
        private val items: Map<BatchId, InventoryItem> = emptyMap(),
        private val lookupFailure: Outcome<Nothing>? = null,
    ) : InventoryRepository {
        override suspend fun listItems(): Outcome<List<InventoryItem>> = Outcome.success(emptyList())

        override suspend fun findItem(batchId: BatchId): Outcome<InventoryItem?> = Outcome.success(items[batchId])

        override suspend fun findByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?> =
            lookupFailure ?: Outcome.success(entries[key])

        override suspend fun appendIfAbsent(entry: InventoryLedgerEntry): Outcome<Boolean> = Outcome.success(true)

        override suspend fun ledger(): Outcome<List<InventoryLedgerEntry>> = Outcome.success(emptyList())
    }
}
