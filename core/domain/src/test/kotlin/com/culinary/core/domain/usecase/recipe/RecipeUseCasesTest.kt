package com.culinary.core.domain.usecase.recipe

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeCategory
import com.culinary.core.domain.model.RecipeComponent
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.transaction.TransactionRunner
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import com.culinary.core.domain.model.Unit as UnitModel

/** 菜谱用例：当前版本固定、循环拒绝、按份数展开与可选配料标记。 */
class RecipeUseCasesTest {
    private val recipes = FakeRecipeRepository()
    private val ingredients = FakeIngredientRepository()
    private val tx = ImmediateTransactionRunner
    private var counter = 0
    private val ids = IdGenerator { "id-${counter++}" }

    private val saveRecipe = SaveRecipeUseCase(recipes, ids, tx)
    private val saveDraft = SaveRecipeDraftUseCase(recipes, ids, tx)
    private val publishDraft = PublishRecipeDraftUseCase(recipes, ids, tx)
    private val expand = ExpandRecipeUseCase(recipes, ingredients)

    @Test
    fun `publishing resolves a current sub recipe reference to an explicit version`() =
        runBlocking<Unit> {
            val (parentId, childId) = seedRecipes()
            recipes.versions[RecipeVersionId(CHILD_VERSION)] = publishedChildVersion(childId)
            recipes.current[childId.value] = RecipeVersionId(CHILD_VERSION)

            val result =
                publishDraft(
                    RecipeVersionInput(
                        recipeId = parentId,
                        yieldAmount = 1000.0,
                        yieldUnit = gram(),
                        servings = 2,
                        components =
                            listOf(
                                RecipeLineInput(
                                    target = RecipeLineTarget.RecipeCurrent(childId),
                                    amount = 200.0,
                                    unit = gram(),
                                ),
                            ),
                    ),
                )

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val published = recipes.versions.getValue((result as Outcome.Success).value)
            assertThat(published.status).isEqualTo(VersionStatus.PUBLISHED)
            val target = published.components.single().target as ComponentTarget.Recipe
            assertThat(target.recipeId).isEqualTo(childId)
            assertThat(target.versionId).isEqualTo(RecipeVersionId(CHILD_VERSION))
            assertThat(recipes.current[parentId.value]).isEqualTo(published.id)
        }

    @Test
    fun `saving a draft also stores explicit version ids`() =
        runBlocking<Unit> {
            val (parentId, childId) = seedRecipes()
            recipes.versions[RecipeVersionId(CHILD_VERSION)] = publishedChildVersion(childId)
            recipes.current[childId.value] = RecipeVersionId(CHILD_VERSION)

            val result =
                saveDraft(
                    RecipeVersionInput(
                        recipeId = parentId,
                        yieldAmount = 500.0,
                        yieldUnit = gram(),
                        servings = 2,
                        components =
                            listOf(
                                RecipeLineInput(
                                    target = RecipeLineTarget.RecipeCurrent(childId),
                                    amount = 100.0,
                                    unit = gram(),
                                ),
                            ),
                    ),
                )

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val draft = recipes.versions.getValue((result as Outcome.Success).value)
            assertThat(draft.status).isEqualTo(VersionStatus.DRAFT)
            val target = draft.components.single().target as ComponentTarget.Recipe
            assertThat(target.versionId).isEqualTo(RecipeVersionId(CHILD_VERSION))
        }

    @Test
    fun `saving an existing draft updates the same version and number`() =
        runBlocking<Unit> {
            val (parentId, _) = seedRecipes()
            val first =
                saveDraft(
                    RecipeVersionInput(
                        recipeId = parentId,
                        yieldAmount = 500.0,
                        yieldUnit = gram(),
                        servings = 2,
                        components = emptyList(),
                    ),
                ) as Outcome.Success
            val second =
                saveDraft(
                    RecipeVersionInput(
                        recipeId = parentId,
                        yieldAmount = 750.0,
                        yieldUnit = gram(),
                        servings = 3,
                        components = emptyList(),
                        versionId = first.value,
                    ),
                ) as Outcome.Success

            assertThat(second.value).isEqualTo(first.value)
            assertThat(recipes.versions).hasSize(1)
            val saved = recipes.versions.getValue(second.value)
            assertThat(saved.versionNo).isEqualTo(1)
            assertThat(saved.yield.amount).isEqualTo(750.0)
            assertThat(saved.servings).isEqualTo(3)
        }

    @Test
    fun `saving a published version id is rejected instead of overwriting it`() =
        runBlocking<Unit> {
            val (parentId, _) = seedRecipes()
            val published = RecipeVersionId(PARENT_VERSION)
            recipes.versions[published] =
                RecipeVersion(
                    id = published,
                    recipeId = parentId,
                    versionNo = 1,
                    status = VersionStatus.PUBLISHED,
                    yield = Quantity(500.0, gram()),
                    servings = 2,
                    components = emptyList(),
                )

            val result =
                saveDraft(
                    RecipeVersionInput(
                        recipeId = parentId,
                        yieldAmount = 750.0,
                        yieldUnit = gram(),
                        servings = 3,
                        components = emptyList(),
                        versionId = published,
                    ),
                )

            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
            assertThat(
                recipes.versions
                    .getValue(published)
                    .yield.amount,
            ).isEqualTo(500.0)
        }

    @Test
    fun `draft stores min range and rejects a value above yield`() =
        runBlocking<Unit> {
            val (parentId, _) = seedRecipes()
            val saved =
                saveDraft(
                    RecipeVersionInput(
                        recipeId = parentId,
                        yieldAmount = 1000.0,
                        yieldUnit = gram(),
                        servings = 2,
                        components = emptyList(),
                        minRangeAmount = 500.0,
                        minRangeUnit = gram(),
                    ),
                ) as Outcome.Success
            assertThat(
                recipes.versions
                    .getValue(saved.value)
                    .minRange
                    ?.amount,
            ).isEqualTo(500.0)

            val rejected =
                saveDraft(
                    RecipeVersionInput(
                        recipeId = parentId,
                        yieldAmount = 1000.0,
                        yieldUnit = gram(),
                        servings = 2,
                        components = emptyList(),
                        minRangeAmount = 1500.0,
                        minRangeUnit = gram(),
                    ),
                )
            assertThat(rejected).isInstanceOf(Outcome.Failure::class.java)
        }

    @Test
    fun `publishing rejects a cycle across sub recipes`() =
        runBlocking<Unit> {
            val (parentId, childId) = seedRecipes()
            val parentOld = RecipeVersionId(PARENT_VERSION)
            val childOld = RecipeVersionId(CHILD_VERSION)
            recipes.versions[parentOld] =
                cycleVersion(parentOld, parentId, childId, childOld)
            recipes.versions[childOld] =
                cycleVersion(childOld, childId, parentId, parentOld)
            recipes.current[childId.value] = childOld

            val result =
                publishDraft(
                    RecipeVersionInput(
                        recipeId = parentId,
                        yieldAmount = 1000.0,
                        yieldUnit = gram(),
                        servings = 2,
                        components =
                            listOf(
                                RecipeLineInput(
                                    target = RecipeLineTarget.RecipeCurrent(childId),
                                    amount = 100.0,
                                    unit = gram(),
                                ),
                            ),
                    ),
                )

            assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-REP-001")
        }

    @Test
    fun `expansion scales by servings and marks optional lines`() =
        runBlocking<Unit> {
            ingredients.items +=
                Ingredient(
                    id = IngredientId("flour"),
                    name = "低筋面粉",
                    normalizedName = "低筋面粉",
                    defaultUnitId = UnitId("g"),
                    status = EntityStatus.ACTIVE,
                )
            val versionId = RecipeVersionId("root-v1")
            recipes.versions[versionId] =
                RecipeVersion(
                    id = versionId,
                    recipeId = RecipeId("root"),
                    versionNo = 1,
                    status = VersionStatus.PUBLISHED,
                    yield = Quantity(1000.0, gram()),
                    servings = 2,
                    components =
                        listOf(
                            RecipeComponent(
                                lineNo = 1,
                                target = ComponentTarget.Ingredient(IngredientId("flour")),
                                quantity = Quantity(100.0, gram()),
                            ),
                            RecipeComponent(
                                lineNo = 2,
                                target = ComponentTarget.Ingredient(IngredientId("flour")),
                                quantity = Quantity(10.0, gram()),
                                isOptional = true,
                            ),
                        ),
                )

            val result = expand(versionId, targetServings = 4.0)

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val expanded = (result as Outcome.Success).value
            assertThat(expanded.baseServings).isEqualTo(2)
            assertThat(expanded.lines).hasSize(2)
            assertThat(expanded.lines[0].quantity.amount).isWithin(1e-9).of(200.0)
            assertThat(expanded.lines[0].label).isEqualTo("低筋面粉")
            assertThat(expanded.lines[1].isOptional).isTrue()
            assertThat(expanded.lines[1].quantity.amount).isWithin(1e-9).of(20.0)
        }

    @Test
    fun `expansion reports a missing sauce graph when no sauce repository is wired`() =
        runBlocking<Unit> {
            val versionId = RecipeVersionId("sauce-parent-v1")
            recipes.versions[versionId] =
                RecipeVersion(
                    id = versionId,
                    recipeId = RecipeId("sauce-parent"),
                    versionNo = 1,
                    status = VersionStatus.PUBLISHED,
                    yield = Quantity(1000.0, gram()),
                    servings = 1,
                    components =
                        listOf(
                            RecipeComponent(
                                lineNo = 1,
                                target =
                                    ComponentTarget.Sauce(
                                        com.culinary.core.domain.model
                                            .SauceId("sauce-1"),
                                        com.culinary.core.domain.model
                                            .SauceVersionId("sauce-v1"),
                                    ),
                                quantity = Quantity(50.0, gram()),
                            ),
                        ),
                )

            val result = expand(versionId, targetServings = 1.0)

            assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-NF-001")
        }

    @Test
    fun `rejects invalid servings and non positive quantities`() =
        runBlocking<Unit> {
            val (parentId, _) = seedRecipes()

            val badServings =
                saveDraft(
                    RecipeVersionInput(parentId, 100.0, gram(), servings = 0, components = emptyList()),
                )
            val badQuantity =
                saveDraft(
                    RecipeVersionInput(
                        parentId,
                        100.0,
                        gram(),
                        servings = 1,
                        components =
                            listOf(
                                RecipeLineInput(RecipeLineTarget.Ingredient(IngredientId("x")), 0.0, gram()),
                            ),
                    ),
                )

            assertThat((badServings as Outcome.Failure).error).isInstanceOf(CulinaryError.Validation::class.java)
            assertThat((badQuantity as Outcome.Failure).error).isInstanceOf(CulinaryError.Validation::class.java)
            assertThat(recipes.versions).isEmpty()
        }

    private suspend fun seedRecipes(): Pair<RecipeId, RecipeId> {
        val parent = saveRecipe(SaveRecipeInput(name = "主菜", category = RecipeCategory.DISH))
        val child = saveRecipe(SaveRecipeInput(name = "高汤", category = RecipeCategory.STOCK))
        return (parent as Outcome.Success).value to (child as Outcome.Success).value
    }

    private fun publishedChildVersion(childId: RecipeId): RecipeVersion =
        RecipeVersion(
            id = RecipeVersionId(CHILD_VERSION),
            recipeId = childId,
            versionNo = 1,
            status = VersionStatus.PUBLISHED,
            yield = Quantity(1000.0, gram()),
            servings = 4,
            isCurrent = true,
            components = emptyList(),
        )

    private fun cycleVersion(
        id: RecipeVersionId,
        recipeId: RecipeId,
        otherRecipe: RecipeId,
        otherVersion: RecipeVersionId,
    ): RecipeVersion =
        RecipeVersion(
            id = id,
            recipeId = recipeId,
            versionNo = 1,
            status = VersionStatus.PUBLISHED,
            yield = Quantity(1000.0, gram()),
            servings = 1,
            components =
                listOf(
                    RecipeComponent(
                        lineNo = 1,
                        target = ComponentTarget.Recipe(otherRecipe, otherVersion),
                        quantity = Quantity(100.0, gram()),
                    ),
                ),
        )

    private fun gram(): UnitModel = UnitModel(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 0)

    private class FakeRecipeRepository : RecipeRepository {
        val recipes = mutableListOf<Recipe>()
        val versions = mutableMapOf<RecipeVersionId, RecipeVersion>()
        val current = mutableMapOf<String, RecipeVersionId>()

        override fun observeActive(): Flow<List<Recipe>> = flowOf(recipes.filter { it.status == EntityStatus.ACTIVE })

        override suspend fun get(id: RecipeId): Outcome<Recipe> =
            recipes.firstOrNull { it.id == id }?.let { Outcome.success(it) }
                ?: Outcome.failure(CulinaryError.NotFound("recipe", id.value))

        override suspend fun getVersion(id: RecipeVersionId): RecipeVersion? = versions[id]

        override suspend fun getCurrentVersion(recipeId: RecipeId): RecipeVersion? =
            current[recipeId.value]?.let { versions[it] }

        override suspend fun listVersions(recipeId: RecipeId): List<RecipeVersion> =
            versions.values.filter { it.recipeId == recipeId }.sortedByDescending { it.versionNo }

        override suspend fun nextVersionNo(recipeId: RecipeId): Int =
            (versions.values.filter { it.recipeId == recipeId }.maxOfOrNull { it.versionNo } ?: 0) + 1

        override suspend fun save(recipe: Recipe): Outcome<RecipeId> {
            recipes.removeAll { it.id == recipe.id }
            recipes += recipe
            return Outcome.success(recipe.id)
        }

        override suspend fun saveDraft(version: RecipeVersion): RecipeVersionId {
            versions[version.id] = version
            return version.id
        }

        override suspend fun savePublished(version: RecipeVersion): Outcome<RecipeVersionId> {
            versions.values.filter { it.recipeId == version.recipeId }.forEach { existing ->
                versions[existing.id] = existing.copy(isCurrent = false)
            }
            val published = version.copy(status = VersionStatus.PUBLISHED, isCurrent = true)
            versions[published.id] = published
            current[version.recipeId.value] = published.id
            return Outcome.success(published.id)
        }
    }

    private class FakeIngredientRepository : IngredientRepository {
        val items = mutableListOf<Ingredient>()

        override fun observeActive(): Flow<List<Ingredient>> = flowOf(items)

        override suspend fun get(id: IngredientId): Outcome<Ingredient> =
            items.firstOrNull { it.id == id }?.let { Outcome.success(it) }
                ?: Outcome.failure(CulinaryError.NotFound("ingredient", id.value))

        override suspend fun save(ingredient: Ingredient): Outcome<IngredientId> = Outcome.success(ingredient.id)

        override suspend fun archive(id: IngredientId): Outcome<Unit> = Outcome.success(Unit)
    }

    private object ImmediateTransactionRunner : TransactionRunner {
        override suspend fun <T> write(block: suspend () -> T): Outcome<T> = Outcome.success(block())
    }

    private companion object {
        const val PARENT_VERSION = "recipe-parent-v1"
        const val CHILD_VERSION = "recipe-child-v1"
    }
}
