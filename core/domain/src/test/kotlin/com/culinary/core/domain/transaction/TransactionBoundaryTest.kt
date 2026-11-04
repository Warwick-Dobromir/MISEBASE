package com.culinary.core.domain.transaction

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.SauceRepository
import com.culinary.core.domain.usecase.ingredient.SaveIngredientInput
import com.culinary.core.domain.usecase.ingredient.SaveIngredientUseCase
import com.culinary.core.domain.usecase.recipe.PublishRecipeDraftUseCase
import com.culinary.core.domain.usecase.recipe.RecipeVersionInput
import com.culinary.core.domain.usecase.sauce.SaveSauceDraftInput
import com.culinary.core.domain.usecase.sauce.SaveSauceDraftUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import com.culinary.core.domain.model.Unit as UnitModel

/**
 * Repository 事务边界（E02 §4.2）：
 *
 * 所有写用例必须经过 [TransactionRunner]；事务失败时不得留下部分写入。
 */
class TransactionBoundaryTest {
    private val ingredients = FakeIngredientRepository()
    private val sauces = FakeSauceRepository()
    private val recipes = FakeRecipeRepository()
    private var counter = 0
    private val ids = IdGenerator { "id-${counter++}" }

    @Test
    fun `ingredient save executes inside the transaction runner`() =
        runBlocking<Unit> {
            val runner = CountingRunner()
            val useCase = SaveIngredientUseCase(ingredients, ids, runner)

            useCase(SaveIngredientInput(name = "低筋面粉"))

            assertThat(runner.writes).isEqualTo(1)
            assertThat(ingredients.saved).hasSize(1)
        }

    @Test
    fun `failed transaction prevents a sauce draft write`() =
        runBlocking<Unit> {
            val runner = FailingRunner()
            val useCase = SaveSauceDraftUseCase(sauces, ids, runner)

            val result =
                useCase(
                    SaveSauceDraftInput(
                        sauceId = SauceId("sauce-1"),
                        yieldAmount = 500.0,
                        yieldUnit = gram(),
                        components = emptyList(),
                    ),
                )

            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
            assertThat(sauces.versions).isEmpty()
        }

    @Test
    fun `failed transaction prevents a recipe publish write`() =
        runBlocking<Unit> {
            val runner = FailingRunner()
            val useCase = PublishRecipeDraftUseCase(recipes, ids, runner)

            val result =
                useCase(
                    RecipeVersionInput(
                        recipeId = RecipeId("recipe-1"),
                        yieldAmount = 1000.0,
                        yieldUnit = gram(),
                        servings = 2,
                        components = emptyList(),
                    ),
                )

            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
            assertThat(recipes.versions).isEmpty()
        }

    @Test
    fun `failed transaction prevents an ingredient write`() =
        runBlocking<Unit> {
            val useCase = SaveIngredientUseCase(ingredients, ids, FailingRunner())

            val result = useCase(SaveIngredientInput(name = "低筋面粉"))

            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
            assertThat(ingredients.saved).isEmpty()
        }

    private fun gram(): UnitModel = UnitModel(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 0)

    private class CountingRunner : TransactionRunner {
        var writes = 0

        override suspend fun <T> write(block: suspend () -> T): Outcome<T> {
            writes += 1
            return Outcome.success(block())
        }
    }

    private class FailingRunner : TransactionRunner {
        override suspend fun <T> write(block: suspend () -> T): Outcome<T> =
            Outcome.failure(
                CulinaryError.Data(message = "transaction failed", code = com.culinary.core.common.ErrorCode.DB_001),
            )
    }

    private class FakeIngredientRepository : IngredientRepository {
        val saved = mutableListOf<Ingredient>()

        override fun observeActive(): Flow<List<Ingredient>> = flowOf(saved)

        override suspend fun get(id: IngredientId): Outcome<Ingredient> =
            saved.firstOrNull { it.id == id }?.let { Outcome.success(it) }
                ?: Outcome.failure(CulinaryError.NotFound("ingredient", id.value))

        override suspend fun save(ingredient: Ingredient): Outcome<IngredientId> {
            saved += ingredient
            return Outcome.success(ingredient.id)
        }

        override suspend fun archive(id: IngredientId): Outcome<Unit> = Outcome.success(Unit)
    }

    private class FakeSauceRepository : SauceRepository {
        val versions = mutableMapOf<SauceVersionId, SauceVersion>()

        override fun observeActive(): Flow<List<Sauce>> = flowOf(emptyList())

        override suspend fun get(id: SauceId): Outcome<Sauce> =
            Outcome.failure(CulinaryError.NotFound("sauce", id.value))

        override suspend fun getVersion(id: SauceVersionId): Outcome<SauceVersion> =
            versions[id]?.let { Outcome.success(it) }
                ?: Outcome.failure(CulinaryError.NotFound("sauce_version", id.value))

        override suspend fun listVersions(sauceId: SauceId): List<SauceVersion> = emptyList()

        override suspend fun findCurrentVersion(sauceId: SauceId): SauceVersion? = null

        override suspend fun nextVersionNo(sauceId: SauceId): Int = 1

        override suspend fun save(sauce: Sauce): Outcome<SauceId> = Outcome.success(sauce.id)

        override suspend fun saveDraft(version: SauceVersion): Outcome<SauceVersionId> {
            versions[version.id] = version
            return Outcome.success(version.id)
        }

        override suspend fun savePublished(version: SauceVersion): Outcome<SauceVersionId> = Outcome.success(version.id)
    }

    private class FakeRecipeRepository : RecipeRepository {
        val versions = mutableMapOf<RecipeVersionId, RecipeVersion>()

        override fun observeActive(): Flow<List<Recipe>> = flowOf(emptyList())

        override suspend fun get(id: RecipeId): Outcome<Recipe> =
            Outcome.failure(CulinaryError.NotFound("recipe", id.value))

        override suspend fun getVersion(id: RecipeVersionId): RecipeVersion? = versions[id]

        override suspend fun getCurrentVersion(recipeId: RecipeId): RecipeVersion? = null

        override suspend fun listVersions(recipeId: RecipeId): List<RecipeVersion> = emptyList()

        override suspend fun nextVersionNo(recipeId: RecipeId): Int = 1

        override suspend fun save(recipe: Recipe): Outcome<RecipeId> = Outcome.success(recipe.id)

        override suspend fun saveDraft(version: RecipeVersion): RecipeVersionId {
            versions[version.id] = version
            return version.id
        }

        override suspend fun savePublished(version: RecipeVersion): Outcome<RecipeVersionId> {
            versions[version.id] = version
            return Outcome.success(version.id)
        }
    }
}
