package com.culinary.core.domain.rule.inventory

import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.PurchaseList
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.ShortfallLine
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.time.Instant

/** 采购清单（MB-D-016）：按需计算、不落采购单表、可导出。 */
class PurchaseListTest {
    private val builder = PurchaseListBuilder()

    @Test
    fun `builds a purchase list from uncovered required lines only`() {
        val list =
            builder.build(
                shortfalls =
                    listOf(
                        gap(Flour, required = 500.0, available = 200.0),
                        gap(Rice, required = 100.0, available = 100.0),
                        gap(Cheese, required = 50.0, available = 0.0, isOptional = true),
                    ),
                generatedAt = NOW,
            )

        assertThat(list.lines.map { it.ingredientId }).containsExactly(Flour)
        assertThat(
            list.lines
                .single()
                .toPurchase.amount,
        ).isWithin(1e-9).of(300.0)
    }

    @Test
    fun `orders purchase lines deterministically by ingredient and unit`() {
        val list =
            builder.build(
                shortfalls =
                    listOf(
                        gap(Rice, required = 100.0, available = 0.0),
                        gap(Flour, required = 100.0, available = 0.0),
                    ),
                generatedAt = NOW,
            )

        assertThat(list.lines.map { it.ingredientId.value }).containsExactly("flour", "rice").inOrder()
    }

    @Test
    fun `attaches an optional product id per ingredient`() {
        val list =
            builder.build(
                shortfalls = listOf(gap(Flour, required = 100.0, available = 0.0)),
                generatedAt = NOW,
                productFor = { ingredient -> if (ingredient == Flour) ProductId("product-flour") else null },
            )

        assertThat(list.lines.single().productId).isEqualTo(ProductId("product-flour"))
    }

    @Test
    fun `exports a stable csv with a fixed header`() {
        val list =
            builder.build(
                shortfalls =
                    listOf(
                        gap(Flour, required = 500.0, available = 200.0),
                        gap(Rice, required = 100.0, available = 0.0),
                    ),
                generatedAt = NOW,
            )

        val csv = CsvPurchaseListExporter().export(list)

        assertThat(csv.lines().first()).isEqualTo("ingredient_id,product_id,required,available,to_purchase,unit")
        assertThat(csv).contains("flour,,500,200,300,g")
        assertThat(csv).contains("rice,,100,0,100,g")
        assertThat(csv.endsWith("\n")).isTrue()
    }

    @Test
    fun `exports an empty list with only the header`() {
        val csv = CsvPurchaseListExporter().export(PurchaseList(generatedAt = NOW, lines = emptyList()))

        assertThat(csv).isEqualTo("ingredient_id,product_id,required,available,to_purchase,unit\n")
    }

    @Test
    fun `purchase list domain sources never reference purchase order tables`() {
        val sourceRoot = File("src/main/kotlin/com/culinary/core/domain")
        assertThat(sourceRoot.isDirectory).isTrue()

        val offenders =
            sourceRoot
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file ->
                    val code =
                        file
                            .readLines()
                            .filterNot { line ->
                                val trimmed = line.trimStart()
                                trimmed.startsWith("*") ||
                                    trimmed.startsWith("/*") ||
                                    trimmed.startsWith("//")
                            }
                    code.any { it.contains("purchase_order") }
                }.map { it.name }
                .toList()

        assertThat(offenders).isEmpty()
    }

    private fun gap(
        ingredientId: IngredientId,
        required: Double,
        available: Double,
        isOptional: Boolean = false,
    ): ShortfallLine =
        ShortfallLine(
            ingredientId = ingredientId,
            required = Quantity(required, Gram),
            available = Quantity(available, Gram),
            shortfall = Quantity(maxOf(0.0, required - available), Gram),
            isOptional = isOptional,
        )

    private companion object {
        val NOW: Instant = Instant.ofEpochMilli(1_760_000_000_000L)
        val Flour = IngredientId("flour")
        val Rice = IngredientId("rice")
        val Cheese = IngredientId("cheese")
        val Gram = Unit(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 1)
    }
}
