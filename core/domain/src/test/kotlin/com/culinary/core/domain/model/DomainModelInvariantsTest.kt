package com.culinary.core.domain.model

import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** 领域模型与值对象的不变量（阶段 2）。 */
class DomainModelInvariantsTest {
    @Test
    fun `inventory item derives available quantity from on hand and reserved`() {
        val item = item(onHand = 10.0, reserved = 3.0)

        assertThat(item.available).isWithin(1e-9).of(7.0)
        assertThat(item.isDepleted).isFalse()
    }

    @Test
    fun `inventory item rejects reserved greater than on hand`() {
        assertThrows(IllegalArgumentException::class.java) {
            item(onHand = 2.0, reserved = 3.0)
        }
    }

    @Test
    fun `inventory item orders undated batches last under fefo`() {
        val dated = item(expiresOn = LocalDate.of(2026, 1, 1))
        val undated = item(expiresOn = null)

        assertThat(dated.fefoKey).isLessThan(undated.fefoKey)
    }

    @Test
    fun `ledger entry rejects an entry that changes nothing`() {
        assertThrows(IllegalArgumentException::class.java) {
            ledger(onHandDelta = 0.0, reservedDelta = 0.0, onHandAfter = 5.0, reservedAfter = 0.0)
        }
    }

    @Test
    fun `ledger entry rejects a negative resulting balance`() {
        assertThrows(IllegalArgumentException::class.java) {
            ledger(onHandDelta = -6.0, reservedDelta = 0.0, onHandAfter = -1.0, reservedAfter = 0.0)
        }
    }

    @Test
    fun `ledger entry rejects reserved after above on hand after`() {
        assertThrows(IllegalArgumentException::class.java) {
            ledger(onHandDelta = -1.0, reservedDelta = 0.0, onHandAfter = 2.0, reservedAfter = 3.0)
        }
    }

    @Test
    fun `ledger entry requires an idempotency key`() {
        assertThrows(IllegalArgumentException::class.java) {
            ledger(onHandDelta = -1.0, reservedDelta = 0.0, onHandAfter = 4.0, reservedAfter = 0.0, key = " ")
        }
    }

    @Test
    fun `servings scale factor is the ratio between requested and base servings`() {
        assertThat(Servings(3.0).scaleFactor(Servings(2.0))).isWithin(1e-9).of(1.5)
    }

    @Test
    fun `servings reject non positive counts`() {
        assertThrows(IllegalArgumentException::class.java) { Servings(0.0) }
    }

    @Test
    fun `recipe version requires a positive servings count`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecipeVersion(
                id = RecipeVersionId("v1"),
                recipeId = RecipeId("r1"),
                versionNo = 1,
                status = VersionStatus.DRAFT,
                yield = Quantity(1.0, Gram),
                components = emptyList(),
                servings = 0,
            )
        }
    }

    @Test
    fun `shortfall line requires a shared unit`() {
        assertThrows(IllegalArgumentException::class.java) {
            ShortfallLine(
                ingredientId = IngredientId("flour"),
                required = Quantity(100.0, Gram),
                available = Quantity(1.0, Kilogram),
                shortfall = Quantity(0.0, Gram),
            )
        }
    }

    @Test
    fun `purchase list line requires a positive purchase quantity`() {
        assertThrows(IllegalArgumentException::class.java) {
            PurchaseListLine(
                ingredientId = IngredientId("flour"),
                required = Quantity(100.0, Gram),
                available = Quantity(100.0, Gram),
                toPurchase = Quantity(0.0, Gram),
            )
        }
    }

    @Test
    fun `calculation record requires a sha256 shaped payload hash`() {
        assertThrows(IllegalArgumentException::class.java) {
            record(hash = "not-a-hash")
        }
    }

    @Test
    fun `calculation record preview can be confirmed exactly once`() {
        val preview = record()
        val confirmed = preview.confirm(NOW)

        assertThat(confirmed).isInstanceOf(Outcome.Success::class.java)
        val value = (confirmed as Outcome.Success).value
        assertThat(value.isConfirmed).isTrue()
        assertThat(value.confirmedAt).isEqualTo(NOW)

        val again = value.confirm(NOW)
        assertThat((again as Outcome.Failure).error.code).isEqualTo(ErrorCode.INV_003)
    }

    @Test
    fun `calculation record rejects duplicate ingredient unit lines`() {
        val line = RequirementLine(IngredientId("flour"), Quantity(1.0, Gram))

        assertThrows(IllegalArgumentException::class.java) {
            record(lines = listOf(line, line))
        }
    }

    private fun item(
        onHand: Double = 10.0,
        reserved: Double = 0.0,
        expiresOn: LocalDate? = null,
    ) = InventoryItem(
        batchId = BatchId("batch-1"),
        productId = ProductId("product-1"),
        locationId = LocationId("location-1"),
        stockUnit = Gram,
        onHand = onHand,
        reserved = reserved,
        expiresOn = expiresOn,
    )

    private fun ledger(
        onHandDelta: Double,
        reservedDelta: Double,
        onHandAfter: Double,
        reservedAfter: Double,
        key: String = "key-1",
    ) = InventoryLedgerEntry(
        id = LedgerEntryId("ledger-1"),
        batchId = BatchId("batch-1"),
        productId = ProductId("product-1"),
        locationId = LocationId("location-1"),
        type = LedgerEntryType.PRODUCTION_ISSUE,
        stockUnit = Gram,
        onHandDelta = onHandDelta,
        reservedDelta = reservedDelta,
        onHandAfter = onHandAfter,
        reservedAfter = reservedAfter,
        idempotencyKey = key,
        occurredAt = NOW,
    )

    private fun record(
        hash: String = "a".repeat(64),
        lines: List<RequirementLine> = listOf(RequirementLine(IngredientId("flour"), Quantity(1.0, Gram))),
    ) = CalculationRecord(
        id = CalculationRecordId("calc-1"),
        recipeVersionId = RecipeVersionId("v1"),
        servings = Servings(2.0),
        lines = lines,
        engineVersion = "stage2-1",
        payloadHash = hash,
        status = CalculationRecordStatus.PREVIEW,
        createdAt = NOW,
    )

    private companion object {
        val NOW: Instant = Instant.ofEpochMilli(1_760_000_000_000L)
        val Gram = Unit(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 1)
        val Kilogram = Unit(UnitId("kg"), "kg", "千克", "kg", UnitDimension.MASS, 1000.0, false, false, 2)
    }
}
