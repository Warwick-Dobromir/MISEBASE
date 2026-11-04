package com.culinary.core.domain.rule.recipe

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RecipeComponent
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Servings
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.model.VersionStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 份数缩放（阶段 2）：以版本标注份数为基准，沿展开路径等比缩放。 */
class RecipeExploderServingsTest {
    private val exploder = RecipeExploder()

    @Test
    fun `scales the root recipe from four servings to two`() {
        val version =
            version(
                id = "root",
                servings = 4,
                components = listOf(line(1, Flour, 400.0)),
            )

        val result =
            exploder.explodeForServings(
                rootVersionId = RecipeVersionId("root"),
                servings = Servings(2.0),
                source = { id -> if (id.value == "root") version else null },
            )

        val requirements = (result as Outcome.Success).value
        assertThat(requirements.single().quantity.amount).isWithin(1e-9).of(200.0)
    }

    @Test
    fun `scales up when more servings are requested`() {
        val version = version(id = "root", servings = 2, components = listOf(line(1, Flour, 100.0)))

        val result =
            exploder.explodeForServings(
                rootVersionId = RecipeVersionId("root"),
                servings = Servings(3.0),
                source = { id -> if (id.value == "root") version else null },
            )

        assertThat((result as Outcome.Success).value.single().quantity.amount).isWithin(1e-9).of(150.0)
    }

    @Test
    fun `scales the sub recipe through the parent servings`() {
        val child =
            version(
                id = "child",
                servings = 1,
                components = listOf(line(1, Flour, 100.0)),
            )
        val root =
            version(
                id = "root",
                servings = 2,
                components =
                    listOf(
                        RecipeComponent(
                            lineNo = 1,
                            target = ComponentTarget.Recipe(RecipeId("recipe-child"), RecipeVersionId("child")),
                            quantity = Quantity(100.0, Gram),
                        ),
                    ),
            )

        val result =
            exploder.explodeForServings(
                rootVersionId = RecipeVersionId("root"),
                servings = Servings(4.0),
                source = { id ->
                    when (id.value) {
                        "root" -> root
                        "child" -> child
                        else -> null
                    }
                },
            )

        assertThat((result as Outcome.Success).value.single().quantity.amount).isWithin(1e-9).of(20.0)
    }

    @Test
    fun `reports a missing root version`() {
        val result =
            exploder.explodeForServings(
                rootVersionId = RecipeVersionId("missing"),
                servings = Servings(1.0),
                source = { null },
            )

        assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-NF-001")
    }

    @Test
    fun `rejects a non positive servings count at construction`() {
        runCatching { Servings(-1.0) }
            .onSuccess { error("expected Servings to reject a negative count") }
    }

    private fun version(
        id: String,
        servings: Int,
        components: List<RecipeComponent>,
    ): RecipeVersion =
        RecipeVersion(
            id = RecipeVersionId(id),
            recipeId = RecipeId("recipe-$id"),
            versionNo = 1,
            status = VersionStatus.PUBLISHED,
            yield = Quantity(1000.0, Gram),
            components = components,
            servings = servings,
        )

    private fun line(
        lineNo: Int,
        ingredient: IngredientId,
        amount: Double,
    ): RecipeComponent =
        RecipeComponent(
            lineNo = lineNo,
            target = ComponentTarget.Ingredient(ingredient),
            quantity = Quantity(amount, Gram),
        )

    private companion object {
        val Flour = IngredientId("flour")
        val Gram = Unit(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 1)
    }
}
