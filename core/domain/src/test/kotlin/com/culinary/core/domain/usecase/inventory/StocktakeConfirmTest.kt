package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.IdGenerator
import com.culinary.core.domain.model.BatchLifecycleStatus
import com.culinary.core.domain.model.LedgerEntryType
import com.culinary.core.domain.model.StocktakeScope
import com.culinary.core.domain.model.StocktakeStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant

/** 盘点确认（v9）：逐行差额写 ADJUSTMENT_IN/OUT、冻结批次仍可盘点、重复确认不重复写。 */
class StocktakeConfirmTest {
    private val repository = FakeInventoryArchiveRepository()
    private var counter = 0
    private val ids = IdGenerator { "id-${counter++}" }
    private val stocktakes =
        StocktakeUseCases(
            repository = repository,
            categoryRepository = UnusedArchiveCategoryRepository,
            transactionRunner = ImmediateRunner,
            idGenerator = ids,
            clock = TestClock,
        )

    @Test
    fun `creating a stocktake snapshots live batches including frozen ones`() =
        runBlocking {
            seedBatches()

            val created = stocktakes.create(StocktakeScope.ALL).successValue()

            assertThat(created.status).isEqualTo(StocktakeStatus.DRAFT)
            assertThat(created.scope).isEqualTo(StocktakeScope.ALL)
            assertThat(created.scopeLabel).isEqualTo("全部库存")
            assertThat(created.lines.map { it.batchId })
                .containsExactly("batch-1", "batch-frozen")
                .inOrder()
            assertThat(created.lines.map { it.bookQty }).containsExactly(10.0, 5.0).inOrder()
            assertThat(created.lines.map { it.countedQty }).containsExactly(10.0, 5.0).inOrder()
            assertThat(created.lines.filter { it.frozen }.map { it.batchId })
                .containsExactly("batch-frozen")
            assertThat(created.diffCount).isEqualTo(0)
        }

    @Test
    fun `confirm posts one adjustment in and one out and confirms the stocktake`() =
        runBlocking {
            seedBatches()
            val created = stocktakes.create(StocktakeScope.ALL).successValue()
            val up = created.lines.first { it.batchId == "batch-1" }
            val down = created.lines.first { it.batchId == "batch-frozen" }
            stocktakes.counted(created.id, up.id, 12.0).successValue()
            stocktakes.counted(created.id, down.id, 4.0).successValue()

            val confirmed = stocktakes.confirm(created.id).successValue()

            assertThat(confirmed.status).isEqualTo(StocktakeStatus.CONFIRMED)
            assertThat(confirmed.confirmedAt).isEqualTo(TestInstant)
            assertThat(confirmed.diffCount).isEqualTo(2)
            assertThat(repository.ledger).hasSize(2)

            val inbound = repository.ledger.first { it.batchId.value == "batch-1" }
            assertThat(inbound.type).isEqualTo(LedgerEntryType.ADJUSTMENT_IN)
            assertThat(inbound.onHandDelta).isWithin(1e-9).of(2.0)
            assertThat(inbound.onHandAfter).isWithin(1e-9).of(12.0)
            assertThat(inbound.idempotencyKey).isEqualTo("stocktake:${created.id}:${up.id}")
            assertThat(inbound.referenceType).isEqualTo("inventory_stocktake")
            assertThat(inbound.referenceId).isEqualTo(created.id)

            // 冻结批次允许盘点调整，调整后仍是冻结状态。
            val outbound = repository.ledger.first { it.batchId.value == "batch-frozen" }
            assertThat(outbound.type).isEqualTo(LedgerEntryType.ADJUSTMENT_OUT)
            assertThat(outbound.onHandDelta).isWithin(1e-9).of(-1.0)
            assertThat(outbound.onHandAfter).isWithin(1e-9).of(4.0)
            assertThat(outbound.idempotencyKey).isEqualTo("stocktake:${created.id}:${down.id}")

            assertThat(repository.batches.getValue("batch-1").onHand).isWithin(1e-9).of(12.0)
            assertThat(repository.batches.getValue("batch-frozen").onHand).isWithin(1e-9).of(4.0)
            assertThat(repository.batches.getValue("batch-frozen").status)
                .isEqualTo(BatchLifecycleStatus.FROZEN)
        }

    @Test
    fun `confirming twice does not post duplicate adjustments`() =
        runBlocking {
            seedBatches()
            val created = stocktakes.create(StocktakeScope.ALL).successValue()
            val up = created.lines.first { it.batchId == "batch-1" }
            stocktakes.counted(created.id, up.id, 12.0).successValue()
            stocktakes.confirm(created.id).successValue()

            val second = stocktakes.confirm(created.id).successValue()

            assertThat(second.status).isEqualTo(StocktakeStatus.CONFIRMED)
            assertThat(repository.ledger).hasSize(1)
            assertThat(repository.appendLedgerAttempts).isEqualTo(1)
            assertThat(repository.batches.getValue("batch-1").onHand).isWithin(1e-9).of(12.0)
        }

    private fun seedBatches() {
        val archive = ingredientArchive("archive-butter", "冷黄油")
        repository.seedArchive(archive)
        repository.seedBatch("batch-1", archive, onHand = 10.0, receivedAt = Instant.ofEpochMilli(2_000))
        repository.seedBatch(
            "batch-frozen",
            archive,
            onHand = 5.0,
            receivedAt = Instant.ofEpochMilli(1_000),
            status = BatchLifecycleStatus.FROZEN,
        )
    }
}
