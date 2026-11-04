package com.culinary.core.domain.rule.recipe

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RecipeComponent
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.SauceComponent
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.model.VersionStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 酱汁图展开（Stage 4 遗留 CUL-REP-004）：递归、循环、缺失、可选、单位。 */
class RecipeExploderSauceTest {
    private val exploder = RecipeExploder()

    private val gram = unit("g", "g", UnitDimension.MASS, 1.0)
    private val piece = unit("piece", "个", UnitDimension.COUNT, 1.0)

    private val flour = IngredientId("flour")
    private val water = IngredientId("water")
    private val salt = IngredientId("salt")

    @Test
    fun `expands a sauce component recursively and scales by its yield`() {
        val sauce =
            sauce(
                "sauce-a",
                yield = 250.0,
                components =
                    listOf(
                        sauceLine(1, flour, 100.0),
                        sauceLine(2, water, 50.0),
                    ),
            )
        val root =
            recipe(
                "root",
                yield = 1000.0,
                components = listOf(recipeSauceLine(1, "sauce-a", 500.0)),
            )

        val result =
            explode(
                "root",
                recipes = mapOf("root" to root),
                sauces = mapOf("sauce-a" to sauce),
                multiplier = 2.0,
            )

        val requirements = (result as Outcome.Success).value
        assertThat(requirements.map { it.ingredientId }).containsExactly(flour, water).inOrder()
        assertThat(requirements[0].quantity.amount).isWithin(1e-9).of(400.0)
        assertThat(requirements[1].quantity.amount).isWithin(1e-9).of(200.0)
        assertThat(requirements[0].quantity.unit).isEqualTo(gram)
    }

    @Test
    fun `expands nested sauces and repeated use is not a cycle`() {
        val inner =
            sauce(
                "sauce-inner",
                yield = 50.0,
                components = listOf(sauceLine(1, flour, 10.0)),
            )
        val outer =
            sauce(
                "sauce-outer",
                yield = 100.0,
                components = listOf(sauceSauceLine(1, "sauce-inner", 100.0)),
            )
        val root =
            recipe(
                "root",
                yield = 100.0,
                components =
                    listOf(
                        recipeSauceLine(1, "sauce-outer", 200.0),
                        recipeSauceLine(2, "sauce-outer", 100.0),
                    ),
            )

        val result =
            explode(
                "root",
                recipes = mapOf("root" to root),
                sauces = mapOf("sauce-outer" to outer, "sauce-inner" to inner),
            )

        val requirements = (result as Outcome.Success).value
        assertThat(requirements).hasSize(2)
        assertThat(requirements.map { it.quantity.amount }).containsExactly(40.0, 20.0).inOrder()
    }

    @Test
    fun `expands a recipe referenced from a sauce`() {
        val childRecipe =
            recipe(
                "filling",
                yield = 100.0,
                components = listOf(recipeLine(1, flour, 10.0)),
            )
        val sauce =
            sauce(
                "sauce-a",
                yield = 100.0,
                components = listOf(sauceRecipeLine(1, "filling", 200.0)),
            )
        val root =
            recipe(
                "root",
                yield = 100.0,
                components = listOf(recipeSauceLine(1, "sauce-a", 100.0)),
            )

        val result =
            explode(
                "root",
                recipes = mapOf("root" to root, "filling" to childRecipe),
                sauces = mapOf("sauce-a" to sauce),
            )

        val requirements = (result as Outcome.Success).value
        assertThat(requirements.single().ingredientId).isEqualTo(flour)
        assertThat(requirements.single().quantity.amount).isWithin(1e-9).of(20.0)
    }

    @Test
    fun `reports a cycle across sauce references`() {
        val a =
            sauce(
                "sauce-a",
                yield = 100.0,
                components = listOf(sauceSauceLine(1, "sauce-b", 10.0)),
            )
        val b =
            sauce(
                "sauce-b",
                yield = 100.0,
                components = listOf(sauceSauceLine(1, "sauce-a", 10.0)),
            )
        val root =
            recipe(
                "root",
                yield = 100.0,
                components = listOf(recipeSauceLine(1, "sauce-a", 100.0)),
            )

        val result = explode("root", mapOf("root" to root), mapOf("sauce-a" to a, "sauce-b" to b))

        assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-REP-001")
    }

    @Test
    fun `reports a missing sauce version`() {
        val root =
            recipe(
                "root",
                yield = 100.0,
                components = listOf(recipeSauceLine(1, "missing", 100.0)),
            )

        val result = explode("root", mapOf("root" to root))

        assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-NF-001")
    }

    @Test
    fun `optional sauce is excluded by default and optional flag propagates`() {
        val sauce =
            sauce(
                "sauce-a",
                yield = 100.0,
                components =
                    listOf(
                        sauceLine(1, flour, 10.0),
                        sauceLine(2, salt, 1.0, isOptional = true),
                    ),
            )
        val root =
            recipe(
                "root",
                yield = 100.0,
                components = listOf(recipeSauceLine(1, "sauce-a", 100.0, isOptional = true)),
            )

        val without = explode("root", mapOf("root" to root), mapOf("sauce-a" to sauce))
        val with =
            explode(
                "root",
                mapOf("root" to root),
                mapOf("sauce-a" to sauce),
                includeOptional = true,
            )

        assertThat((without as Outcome.Success).value).isEmpty()
        val requirements = (with as Outcome.Success).value
        assertThat(requirements).hasSize(2)
        assertThat(requirements.all { it.isOptional }).isTrue()
    }

    @Test
    fun `rejects an unconvertible sauce component unit`() {
        val sauce =
            sauce(
                "sauce-a",
                yield = 100.0,
                components = listOf(sauceLine(1, flour, 10.0)),
            )
        val root =
            recipe(
                "root",
                yield = 100.0,
                components = listOf(recipeSauceLine(1, "sauce-a", 1.0, unit = piece)),
            )

        val result = explode("root", mapOf("root" to root), mapOf("sauce-a" to sauce))

        assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-UNIT-001")
    }

    private fun explode(
        root: String,
        recipes: Map<String, RecipeVersion>,
        sauces: Map<String, SauceVersion> = emptyMap(),
        multiplier: Double = 1.0,
        includeOptional: Boolean = false,
    ): Outcome<List<ExplodedRequirement>> =
        exploder.explode(
            rootVersionId = RecipeVersionId(root),
            source =
                object : RecipeGraphSource {
                    override fun findVersion(id: RecipeVersionId): RecipeVersion? = recipes[id.value]

                    override fun findSauceVersion(id: SauceVersionId): SauceVersion? = sauces[id.value]
                },
            multiplier = multiplier,
            includeOptional = includeOptional,
        )

    private fun recipe(
        id: String,
        yield: Double,
        components: List<RecipeComponent>,
    ): RecipeVersion =
        RecipeVersion(
            id = RecipeVersionId(id),
            recipeId = RecipeId("recipe-$id"),
            versionNo = 1,
            status = VersionStatus.PUBLISHED,
            yield = Quantity(yield, gram),
            components = components,
        )

    private fun sauce(
        id: String,
        yield: Double,
        components: List<SauceComponent>,
    ): SauceVersion =
        SauceVersion(
            id = SauceVersionId(id),
            sauceId = SauceId("sauce-$id"),
            versionNo = 1,
            status = VersionStatus.PUBLISHED,
            yield = Quantity(yield, gram),
            components = components,
        )

    private fun recipeLine(
        lineNo: Int,
        ingredient: IngredientId,
        amount: Double,
    ): RecipeComponent =
        RecipeComponent(
            lineNo = lineNo,
            target = ComponentTarget.Ingredient(ingredient),
            quantity = Quantity(amount, gram),
        )

    private fun recipeSauceLine(
        lineNo: Int,
        sauce: String,
        amount: Double,
        unit: Unit = gram,
        isOptional: Boolean = false,
    ): RecipeComponent =
        RecipeComponent(
            lineNo = lineNo,
            target = ComponentTarget.Sauce(SauceId("sauce-$sauce"), SauceVersionId(sauce)),
            quantity = Quantity(amount, unit),
            isOptional = isOptional,
        )

    private fun sauceLine(
        lineNo: Int,
        ingredient: IngredientId,
        amount: Double,
        isOptional: Boolean = false,
    ): SauceComponent =
        SauceComponent(
            lineNo = lineNo,
            target = ComponentTarget.Ingredient(ingredient),
            quantity = Quantity(amount, gram),
            isOptional = isOptional,
        )

    private fun sauceSauceLine(
        lineNo: Int,
        sauce: String,
        amount: Double,
    ): SauceComponent =
        SauceComponent(
            lineNo = lineNo,
            target = ComponentTarget.Sauce(SauceId("sauce-$sauce"), SauceVersionId(sauce)),
            quantity = Quantity(amount, gram),
        )

    private fun sauceRecipeLine(
        lineNo: Int,
        recipe: String,
        amount: Double,
    ): SauceComponent =
        SauceComponent(
            lineNo = lineNo,
            target = ComponentTarget.Recipe(RecipeId("recipe-$recipe"), RecipeVersionId(recipe)),
            quantity = Quantity(amount, gram),
        )

    private fun unit(
        id: String,
        code: String,
        dimension: UnitDimension,
        factor: Double,
    ): Unit =
        Unit(
            id = UnitId(id),
            code = code,
            name = code,
            symbol = code,
            dimension = dimension,
            baseFactor = factor,
            isBase = true,
            isApproximate = false,
            displayPrecision = 1,
        )
}
