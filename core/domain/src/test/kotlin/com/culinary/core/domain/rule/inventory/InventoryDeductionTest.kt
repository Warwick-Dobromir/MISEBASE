package com.culinary.core.domain.rule.inventory

import com.culinary.core.common.ErrorCode
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.ProductRequirement
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.transaction.TransactionRunner
import com.culinary.core.domain.usecase.inventory.ConfirmInventoryDeductionInput
import com.culinary.core.domain.usecase.inventory.ConfirmInventoryDeductionUseCase
import com.culinary.core.domain.usecase.inventory.PreviewInventoryDeductionUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** 库存预览/确认扣减（MB-D-014）：FEFO、单位换算、缺口、负库存拒绝、幂等、无预留。 */
class InventoryDeductionTest {
    private val calculator = InventoryPreviewCalculator()
    private val planner = InventoryDeductionPlanner()

    @Test
    fun `preview allocates fefo and reports available and shortfall`() {
        val items =
            listOf(
                item("b-feb", onHand = 5.0, expiresOn = LocalDate.of(2026, 2, 1)),
                item("b-jan", onHand = 5.0, expiresOn = LocalDate.of(2026, 1, 1)),
            )

        val preview = preview(requirement(8.0), items)

        val line = preview.lines.single()
        assertThat(line.available.amount).isWithin(1e-9).of(8.0)
        assertThat(line.shortfall.amount).isWithin(1e-9).of(0.0)
        assertThat(line.allocations.map { it.batchId.value }).containsExactly("b-jan", "b-feb").inOrder()
        assertThat(
            line.allocations
                .first()
                .quantity.amount,
        ).isWithin(1e-9).of(5.0)
        assertThat(
            line.allocations
                .last()
                .quantity.amount,
        ).isWithin(1e-9).of(3.0)
    }

    @Test
    fun `preview keeps the uncovered remainder as shortfall`() {
        val items = listOf(item("b1", onHand = 10.0))

        val preview = preview(requirement(12.0), items)

        assertThat(
            preview.lines
                .single()
                .shortfall.amount,
        ).isWithin(1e-9).of(2.0)
        assertThat(preview.hasShortfall).isTrue()
    }

    @Test
    fun `preview converts batch units into the requirement unit`() {
        val items = listOf(item("b1", onHand = 2.0, stockUnit = Kilogram))

        val preview = preview(requirement(1500.0), items)

        assertThat(
            preview.lines
                .single()
                .available.amount,
        ).isWithin(1e-9).of(1500.0)
        assertThat(
            preview.lines
                .single()
                .shortfall.amount,
        ).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `preview treats an unconvertible batch as zero available`() {
        val items = listOf(item("b1", onHand = 2.0, stockUnit = Cup))

        val preview = preview(requirement(100.0), items)

        assertThat(
            preview.lines
                .single()
                .available.amount,
        ).isWithin(1e-9).of(0.0)
        assertThat(
            preview.lines
                .single()
                .shortfall.amount,
        ).isWithin(1e-9).of(100.0)
    }

    @Test
    fun `confirmation appends ledger entries and reduces on hand without reservations`() =
        runBlocking {
            val repository = FakeInventoryRepository(listOf(item("b1", onHand = 10.0)))
            val useCase = confirmUseCase(repository)

            val result = useCase(input(requirement(4.0), referenceId = "calc-1"))

            val value = (result as Outcome.Success).value
            assertThat(value.appliedEntries).hasSize(1)
            val entry = value.appliedEntries.single()
            assertThat(entry.onHandDelta).isWithin(1e-9).of(-4.0)
            assertThat(entry.reservedDelta).isWithin(1e-9).of(0.0)
            assertThat(entry.reservedAfter).isWithin(1e-9).of(0.0)
            assertThat(repository.items.getValue(BatchId("b1")).onHand).isWithin(1e-9).of(6.0)
            assertThat(repository.ledger).hasSize(1)
        }

    @Test
    fun `repeated confirmation with the same reference does not double write`() =
        runBlocking {
            val repository = FakeInventoryRepository(listOf(item("b1", onHand = 10.0)))
            val useCase = confirmUseCase(repository)

            useCase(input(requirement(4.0), referenceId = "calc-1"))
            val second = useCase(input(requirement(4.0), referenceId = "calc-1"))

            val value = (second as Outcome.Success).value
            assertThat(value.appliedEntries).isEmpty()
            assertThat(value.skippedIdempotentKeys).hasSize(1)
            assertThat(repository.ledger).hasSize(1)
            assertThat(repository.items.getValue(BatchId("b1")).onHand).isWithin(1e-9).of(6.0)
        }

    @Test
    fun `planner rejects an allocation that would make a batch negative`() {
        val items = listOf(item("b1", onHand = 3.0))
        val previewLine =
            InventoryPreviewLine(
                productId = PRODUCT,
                required = Quantity(5.0, Gram),
                available = Quantity(5.0, Gram),
                shortfall = Quantity(0.0, Gram),
                allocations = listOf(InventoryAllocation(BatchId("b1"), Quantity(5.0, Gram))),
            )

        val result =
            planner.planAllocations(
                lines = listOf(previewLine),
                items = items,
                referenceType = "calculation_record",
                referenceId = "calc-1",
                occurredAt = NOW,
                idGenerator = ids(),
            )

        val failure = (result as Outcome.Failure).error
        assertThat(failure.code).isEqualTo(ErrorCode.INV_003)
    }

    @Test
    fun `preview is read only`() =
        runBlocking {
            val repository = FakeInventoryRepository(listOf(item("b1", onHand = 10.0)))
            val useCase = PreviewInventoryDeductionUseCase(repository)

            useCase(listOf(requirement(4.0)))

            assertThat(repository.ledger).isEmpty()
            assertThat(repository.items.getValue(BatchId("b1")).onHand).isWithin(1e-9).of(10.0)
        }

    private fun preview(
        requirement: ProductRequirement,
        items: List<InventoryItem>,
    ): InventoryPreview = (calculator.preview(listOf(requirement), items) as Outcome.Success).value

    private fun confirmUseCase(repository: InventoryRepository) =
        ConfirmInventoryDeductionUseCase(
            inventoryRepository = repository,
            transactionRunner = ImmediateTransactionRunner,
            idGenerator = ids(),
        )

    private fun input(
        requirement: ProductRequirement,
        referenceId: String,
    ) = ConfirmInventoryDeductionInput(
        requirements = listOf(requirement),
        referenceId = referenceId,
        occurredAt = NOW,
    )

    private fun ids(): IdGenerator {
        var counter = 0
        return IdGenerator { "id-${counter++}" }
    }

    private fun requirement(amount: Double) = ProductRequirement(PRODUCT, Quantity(amount, Gram))

    private fun item(
        batchId: String,
        onHand: Double,
        stockUnit: Unit = Gram,
        expiresOn: LocalDate? = null,
    ) = InventoryItem(
        batchId = BatchId(batchId),
        productId = PRODUCT,
        locationId = LocationId("loc-1"),
        stockUnit = stockUnit,
        onHand = onHand,
        expiresOn = expiresOn,
    )

    private class FakeInventoryRepository(
        items: List<InventoryItem>,
    ) : InventoryRepository {
        val items = items.associateBy { it.batchId }.toMutableMap()
        val ledger = mutableListOf<InventoryLedgerEntry>()

        override suspend fun listItems(): Outcome<List<InventoryItem>> = Outcome.success(items.values.toList())

        override suspend fun findItem(batchId: BatchId): Outcome<InventoryItem?> = Outcome.success(items[batchId])

        override suspend fun findByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?> =
            Outcome.success(ledger.firstOrNull { it.idempotencyKey == key })

        override suspend fun appendIfAbsent(entry: InventoryLedgerEntry): Outcome<Boolean> {
            if (ledger.any { it.idempotencyKey == entry.idempotencyKey }) return Outcome.success(false)
            val item = items.getValue(entry.batchId)
            items[entry.batchId] = item.copy(onHand = entry.onHandAfter, reserved = entry.reservedAfter)
            ledger += entry
            return Outcome.success(true)
        }

        override suspend fun ledger(): Outcome<List<InventoryLedgerEntry>> = Outcome.success(ledger.toList())
    }

    private object ImmediateTransactionRunner : TransactionRunner {
        override suspend fun <T> write(block: suspend () -> T): Outcome<T> = Outcome.success(block())
    }

    private companion object {
        val NOW: Instant = Instant.ofEpochMilli(1_760_000_000_000L)
        val PRODUCT = ProductId("product-1")
        val Gram = Unit(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 1)
        val Kilogram = Unit(UnitId("kg"), "kg", "千克", "kg", UnitDimension.MASS, 1000.0, false, false, 2)
        val Cup = Unit(UnitId("cup"), "cup", "杯", "cup", UnitDimension.VOLUME, 240.0, false, true, 1)
    }
}
