package com.culinary.core.domain.rule.recipe

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RecipeComponent
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.model.VersionStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import com.culinary.core.domain.model.Unit as MeasureUnit

class RecipeGraphValidatorTest {
    private val validator = RecipeGraphValidator()

    @Test
    fun `accepts an acyclic reference chain`() {
        val source =
            source(
                version("A", "B"),
                version("B", "C"),
                version("C"),
            )

        val result = validator.validate(RecipeVersionId("A"), source)

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
    }

    @Test
    fun `detects a direct self reference`() {
        val source = source(version("A", "A"))

        val result = validator.validate(RecipeVersionId("A"), source)

        assertThat(errorOf(result).code).isEqualTo(ErrorCode.REP_001)
        assertThat(cyclePath(result)).containsExactly("A", "A").inOrder()
    }

    @Test
    fun `detects a multi level cycle and reports the path`() {
        val source =
            source(
                version("A", "B"),
                version("B", "C"),
                version("C", "A"),
            )

        val result = validator.validate(RecipeVersionId("A"), source)

        assertThat(errorOf(result).code).isEqualTo(ErrorCode.REP_001)
        assertThat(cyclePath(result)).containsExactly("A", "B", "C", "A").inOrder()
    }

    @Test
    fun `reports a missing referenced version`() {
        val source = source(version("A", "missing"))

        val result = validator.validate(RecipeVersionId("A"), source)

        val error = errorOf(result)
        assertThat(error.code).isEqualTo(ErrorCode.NF_001)
        assertThat(error).isInstanceOf(CulinaryError.NotFound::class.java)
        assertThat((error as CulinaryError.NotFound).id).isEqualTo("missing")
    }

    @Test
    fun `accepts a diamond where one child is referenced twice`() {
        val source =
            source(
                version("A", "B", "C"),
                version("B", "D"),
                version("C", "D"),
                version("D"),
            )

        val result = validator.validate(RecipeVersionId("A"), source)

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
    }

    private fun errorOf(result: Outcome<Unit>): CulinaryError =
        (result as? Outcome.Failure)?.error ?: error("expected a failure, was $result")

    private fun cyclePath(result: Outcome<Unit>): List<String> {
        val detail = (errorOf(result) as CulinaryError.Domain).detail
        return (detail as DomainDetail.RecipeCycle).path
    }

    private fun source(vararg versions: RecipeVersion): RecipeGraphSource {
        val byId = versions.associateBy { it.id }
        return RecipeGraphSource { byId[it] }
    }

    private fun version(
        id: String,
        vararg children: String,
    ): RecipeVersion =
        RecipeVersion(
            id = RecipeVersionId(id),
            recipeId = RecipeId("recipe-$id"),
            versionNo = 1,
            status = VersionStatus.PUBLISHED,
            yield = Quantity(1.0, Gram),
            components =
                children.mapIndexed { index, child ->
                    RecipeComponent(
                        lineNo = index + 1,
                        target = ComponentTarget.Recipe(RecipeId("recipe-$child"), RecipeVersionId(child)),
                        quantity = Quantity(1.0, Gram),
                    )
                },
        )

    private companion object {
        val Gram = MeasureUnit(UnitId("u-g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 1)
    }
}
