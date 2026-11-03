package com.culinary.core.domain.rule.recipe

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RecipeComponent
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.model.VersionStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 配方展开：产量缩放、子配方换算、可选行、错误与 200 节点基准（R01 阶段 8）。 */
class RecipeExploderTest {

    private val exploder = RecipeExploder()

    private val gram = Unit(
        id = UnitId("g"),
        code = "g",
        name = "gram",
        symbol = "g",
        dimension = UnitDimension.MASS,
        baseFactor = 1.0,
        isBase = true,
        isApproximate = false,
        displayPrecision = 1,
    )

    private val flour = IngredientId("flour")
    private val sugar = IngredientId("sugar")

    @Test
    fun `scales root ingredients by the requested multiplier`() {
        val version = version("root", yield = 1000.0, components = listOf(line(1, sugar, 200.0)))
        val result = explode("root", mapOf("root" to version), multiplier = 2.0)

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        val requirements = (result as Outcome.Success).value
        assertThat(requirements).hasSize(1)
        assertThat(requirements.single().quantity.amount).isWithin(1e-9).of(400.0)
    }

    @Test
    fun `converts a sub recipe reference against the child yield`() {
        val child = version("child", yield = 500.0, components = listOf(line(1, flour, 100.0)))
        val root = version(
            "root",
            yield = 1000.0,
            components = listOf(recipeLine(1, "child", 250.0)),
        )
        val result = explode("root", mapOf("root" to root, "child" to child))

        val requirements = (result as Outcome.Success).value
        assertThat(requirements).hasSize(1)
        assertThat(requirements.single().ingredientId).isEqualTo(flour)
        assertThat(requirements.single().quantity.amount).isWithin(1e-9).of(50.0)
    }

    @Test
    fun `excludes optional components unless requested`() {
        val version = version(
            "root",
            yield = 100.0,
            components = listOf(
                line(1, sugar, 10.0),
                line(2, flour, 5.0, isOptional = true),
            ),
        )
        val source = mapOf("root" to version)

        val withoutOptional = (explode("root", source) as Outcome.Success).value
        val withOptional = (explode("root", source, includeOptional = true) as Outcome.Success).value

        assertThat(withoutOptional).hasSize(1)
        assertThat(withOptional).hasSize(2)
        assertThat(withOptional.last().isOptional).isTrue()
    }

    @Test
    fun `reports a missing child version`() {
        val root = version(
            "root",
            yield = 100.0,
            components = listOf(recipeLine(1, "missing", 10.0)),
        )

        val result = explode("root", mapOf("root" to root))

        assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-NF-001")
    }

    @Test
    fun `reports a cycle instead of recursing forever`() {
        val a = version("a", yield = 100.0, components = listOf(recipeLine(1, "b", 10.0)))
        val b = version("b", yield = 100.0, components = listOf(recipeLine(1, "a", 10.0)))

        val result = explode("a", mapOf("a" to a, "b" to b))

        assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-REP-001")
    }

    @Test
    fun `expands 200 nodes within 500 ms`() {
        val versions = (0 until 200).associate { index ->
            val id = "v$index"
            id to version(
                id = id,
                yield = 100.0,
                components = listOfNotNull(
                    line(1, IngredientId("ingredient-$index"), 1.0),
                    if (index < 199) recipeLine(2, "v${index + 1}", 10.0) else null,
                ),
            )
        }

        val started = System.nanoTime()
        val result = explode("v0", versions)
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000.0

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        assertThat((result as Outcome.Success).value).hasSize(200)
        assertThat(elapsedMillis).isLessThan(500.0)
    }

    private fun explode(
        root: String,
        versions: Map<String, RecipeVersion>,
        multiplier: Double = 1.0,
        includeOptional: Boolean = false,
    ): Outcome<List<ExplodedRequirement>> =
        exploder.explode(
            rootVersionId = RecipeVersionId(root),
            source = { id -> versions[id.value] },
            multiplier = multiplier,
            includeOptional = includeOptional,
        )

    private fun version(
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

    private fun line(
        lineNo: Int,
        ingredient: IngredientId,
        amount: Double,
        isOptional: Boolean = false,
    ): RecipeComponent =
        RecipeComponent(
            lineNo = lineNo,
            target = ComponentTarget.Ingredient(ingredient),
            quantity = Quantity(amount, gram),
            isOptional = isOptional,
        )

    private fun recipeLine(
        lineNo: Int,
        child: String,
        amount: Double,
    ): RecipeComponent =
        RecipeComponent(
            lineNo = lineNo,
            target = ComponentTarget.Recipe(RecipeId("recipe-$child"), RecipeVersionId(child)),
            quantity = Quantity(amount, gram),
        )
}
