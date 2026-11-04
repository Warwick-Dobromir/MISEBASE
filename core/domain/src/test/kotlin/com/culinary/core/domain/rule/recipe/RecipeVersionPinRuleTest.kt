package com.culinary.core.domain.rule.recipe

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.ComponentTargetDraft
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RecipeComponent
import com.culinary.core.domain.model.RecipeComponentDraft
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionDraft
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.model.VersionStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 子配方版本固定（MB-D-013）：发布时解析、已发布不跟随、旧展开稳定。 */
class RecipeVersionPinRuleTest {
    private val rule = RecipeVersionPinRule()
    private val exploder = RecipeExploder()

    @Test
    fun `resolves a current version reference into an explicit version id`() {
        val draft = draft(components = listOf(currentLine(1, childRecipeId)))

        val published = publish(draft, versions = mapOf("child-v1" to childVersion("child-v1", 100.0)))

        val target = (published.components.single().target as ComponentTarget.Recipe)
        assertThat(target.recipeId).isEqualTo(childRecipeId)
        assertThat(target.versionId).isEqualTo(RecipeVersionId("child-v1"))
        assertThat(published.status).isEqualTo(VersionStatus.PUBLISHED)
    }

    @Test
    fun `keeps an explicitly pinned child version`() {
        val draft =
            draft(
                components =
                    listOf(
                        RecipeComponentDraft(
                            lineNo = 1,
                            target = ComponentTargetDraft.RecipePinned(childRecipeId, RecipeVersionId("child-v1")),
                            quantity = Quantity(500.0, Gram),
                        ),
                    ),
            )

        val published = publish(draft, versions = mapOf("child-v1" to childVersion("child-v1", 100.0)))

        assertThat((published.components.single().target as ComponentTarget.Recipe).versionId)
            .isEqualTo(RecipeVersionId("child-v1"))
    }

    @Test
    fun `reports a missing current version`() {
        val result =
            rule.publish(
                draft = draft(components = listOf(currentLine(1, childRecipeId))),
                versions = { null },
                currentVersions = { null },
            )

        assertThat((result as Outcome.Failure).error.code).isEqualTo(ErrorCode.NF_001)
        assertThat((result.error as CulinaryError.NotFound).entity).isEqualTo("recipe_current_version")
    }

    @Test
    fun `reports a referenced version that no longer exists`() {
        val result =
            rule.publish(
                draft = draft(components = listOf(currentLine(1, childRecipeId))),
                versions = { null },
                currentVersions = { RecipeVersionId("gone") },
            )

        assertThat((result as Outcome.Failure).error.code).isEqualTo(ErrorCode.NF_001)
        assertThat((result.error as CulinaryError.NotFound).entity).isEqualTo("recipe_version")
    }

    @Test
    fun `published version keeps the old expansion after the child gets a new current version`() {
        val draft = draft(components = listOf(currentLine(1, childRecipeId)))
        val v1 = childVersion("child-v1", 100.0)
        val v2 = childVersion("child-v2", 300.0)

        val published =
            publish(
                draft,
                versions = mapOf("child-v1" to v1),
                current = mapOf(childRecipeId to RecipeVersionId("child-v1")),
            )

        val before =
            (explode(published, mapOf("child-v1" to v1)) as Outcome.Success)
                .value
                .single()
                .quantity.amount

        // 子菜谱发布新版本并把当前指针移到 v2；已发布父版本不得跟随。
        val after =
            (explode(published, mapOf("child-v1" to v1, "child-v2" to v2)) as Outcome.Success)
                .value
                .single()
                .quantity
                .amount

        assertThat(before).isWithin(1e-9).of(50.0)
        assertThat(after).isWithin(1e-9).of(50.0)
    }

    private fun publish(
        draft: RecipeVersionDraft,
        versions: Map<String, RecipeVersion>,
        current: Map<RecipeId, RecipeVersionId> = mapOf(childRecipeId to RecipeVersionId("child-v1")),
    ): RecipeVersion {
        val result =
            rule.publish(
                draft = draft,
                versions = { id -> versions[id.value] },
                currentVersions = { recipeId -> current[recipeId] },
            )
        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        return (result as Outcome.Success).value
    }

    private fun explode(
        published: RecipeVersion,
        versions: Map<String, RecipeVersion>,
    ): Outcome<List<ExplodedRequirement>> =
        exploder.explode(
            rootVersionId = published.id,
            source = { id -> if (id == published.id) published else versions[id.value] },
        )

    private fun draft(components: List<RecipeComponentDraft>): RecipeVersionDraft =
        RecipeVersionDraft(
            id = RecipeVersionId("root-v1"),
            recipeId = RecipeId("root"),
            versionNo = 1,
            yield = Quantity(1000.0, Gram),
            components = components,
        )

    private fun currentLine(
        lineNo: Int,
        child: RecipeId,
    ): RecipeComponentDraft =
        RecipeComponentDraft(
            lineNo = lineNo,
            target = ComponentTargetDraft.RecipeCurrent(child),
            quantity = Quantity(500.0, Gram),
        )

    private fun childVersion(
        id: String,
        flourAmount: Double,
    ): RecipeVersion =
        RecipeVersion(
            id = RecipeVersionId(id),
            recipeId = childRecipeId,
            versionNo = 1,
            status = VersionStatus.PUBLISHED,
            yield = Quantity(1000.0, Gram),
            components =
                listOf(
                    RecipeComponent(
                        lineNo = 1,
                        target = ComponentTarget.Ingredient(Flour),
                        quantity = Quantity(flourAmount, Gram),
                    ),
                ),
        )

    private companion object {
        val childRecipeId = RecipeId("child")
        val Flour = IngredientId("flour")
        val Gram = Unit(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 1)
    }
}
