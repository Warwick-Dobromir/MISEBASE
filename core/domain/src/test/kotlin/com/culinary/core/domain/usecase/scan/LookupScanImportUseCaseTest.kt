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

/**
 * 条码导入查询（MB-S5-002）：按条码前缀在流水中找最近一次导入；
 * 命中只用于提示"此前已导入"，未命中返回 `null`，失败不伪装成空。
 */
class LookupScanImportUseCaseTest {
    private val gram = Unit(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 1)

    @Test
    fun `returns the latest import for the same code`() =
        runBlocking<kotlin.Unit> {
            val capture = ScanCapture(rawValue = "6901234567892")
            val older = ledgerEntry(id = "ledger-old", batchId = "batch-old", occurredAtMillis = 1_000)
            val latest = ledgerEntry(id = "ledger-new", batchId = "batch-new", occurredAtMillis = 2_000)
            val item = inventoryItem(batchId = "batch-new")
            val useCase =
                LookupScanImportUseCase(
                    FakeInventoryRepository(
                        ledger = listOf(older, latest),
                        items = mapOf(latest.batchId to item),
                    ),
                )

            val result = useCase(capture)

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val previous = (result as Outcome.Success).value
            assertThat(previous?.entry).isEqualTo(latest)
            assertThat(previous?.item).isEqualTo(item)
        }

    @Test
    fun `ignores imports of other codes`() =
        runBlocking<kotlin.Unit> {
            val useCase =
                LookupScanImportUseCase(
                    FakeInventoryRepository(
                        ledger = listOf(ledgerEntry(key = ScanImportKey.of(ScanCapture(rawValue = "other"), "op-1"))),
                    ),
                )

            val result = useCase(ScanCapture(rawValue = "6901234567892"))

            assertThat(result).isEqualTo(Outcome.success(null))
        }

    @Test
    fun `returns null when the code was never imported`() =
        runBlocking<kotlin.Unit> {
            val useCase = LookupScanImportUseCase(FakeInventoryRepository())

            val result = useCase(ScanCapture(rawValue = "never-seen"))

            assertThat(result).isEqualTo(Outcome.success(null))
        }

    @Test
    fun `propagates ledger failures`() =
        runBlocking<kotlin.Unit> {
            val failure =
                Outcome.failure(
                    com.culinary.core.common.CulinaryError
                        .Data(message = "db down"),
                )
            val useCase = LookupScanImportUseCase(FakeInventoryRepository(ledgerFailure = failure))

            val result = useCase(ScanCapture(rawValue = "6901234567892"))

            assertThat(result).isEqualTo(failure)
        }

    private fun ledgerEntry(
        id: String = "ledger-1",
        batchId: String = "batch-1",
        occurredAtMillis: Long = 1_760_000_000_000L,
        key: String = ScanImportKey.of(ScanCapture(rawValue = "6901234567892"), "op-1"),
    ): InventoryLedgerEntry =
        InventoryLedgerEntry(
            id = LedgerEntryId(id),
            batchId = BatchId(batchId),
            productId = ProductId("product-1"),
            locationId = LocationId("loc-1"),
            type = LedgerEntryType.PURCHASE_RECEIPT,
            stockUnit = gram,
            onHandDelta = 1.0,
            reservedDelta = 0.0,
            onHandAfter = 1.0,
            reservedAfter = 0.0,
            idempotencyKey = key,
            occurredAt = Instant.ofEpochMilli(occurredAtMillis),
        )

    private fun inventoryItem(batchId: String): InventoryItem =
        InventoryItem(
            batchId = BatchId(batchId),
            productId = ProductId("product-1"),
            locationId = LocationId("loc-1"),
            stockUnit = gram,
            onHand = 1.0,
        )

    private class FakeInventoryRepository(
        private val ledger: List<InventoryLedgerEntry> = emptyList(),
        private val items: Map<BatchId, InventoryItem> = emptyMap(),
        private val ledgerFailure: Outcome<Nothing>? = null,
    ) : InventoryRepository {
        override suspend fun listItems(): Outcome<List<InventoryItem>> = Outcome.success(emptyList())

        override suspend fun findItem(batchId: BatchId): Outcome<InventoryItem?> = Outcome.success(items[batchId])

        override suspend fun findByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?> =
            Outcome.success(ledger.firstOrNull { it.idempotencyKey == key })

        override suspend fun appendIfAbsent(entry: InventoryLedgerEntry): Outcome<Boolean> = Outcome.success(true)

        override suspend fun ledger(): Outcome<List<InventoryLedgerEntry>> = ledgerFailure ?: Outcome.success(ledger)
    }
}
