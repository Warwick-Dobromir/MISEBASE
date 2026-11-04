package com.culinary.feature.recipes

import androidx.lifecycle.SavedStateHandle
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.IngredientUnitConversion
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeCategory
import com.culinary.core.domain.model.RecipeComponent
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.SauceRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.transaction.TransactionRunner
import com.culinary.core.domain.usecase.ingredient.ListIngredientsUseCase
import com.culinary.core.domain.usecase.recipe.ExpandRecipeUseCase
import com.culinary.core.domain.usecase.recipe.GetRecipeDetailUseCase
import com.culinary.core.domain.usecase.recipe.ListRecipeOptionsUseCase
import com.culinary.core.domain.usecase.recipe.ListRecipesUseCase
import com.culinary.core.domain.usecase.recipe.PublishRecipeDraftUseCase
import com.culinary.core.domain.usecase.recipe.PublishStoredRecipeVersionUseCase
import com.culinary.core.domain.usecase.recipe.RecipeOption
import com.culinary.core.domain.usecase.recipe.SaveRecipeDraftUseCase
import com.culinary.core.domain.usecase.recipe.SaveRecipeUseCase
import com.culinary.core.domain.usecase.unit.ListUnitsUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.culinary.core.domain.model.Unit as UnitModel

/** 菜谱 ViewModel：列表、详情展开、编辑器与子菜谱版本固定。 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val recipes = FakeRecipeRepository()
    private val ingredients = FakeIngredientRepository()
    private val sauces = FakeSauceRepository()
    private val units = FakeUnitRepository()
    private val tx = ImmediateTransactionRunner
    private var counter = 0
    private val ids = IdGenerator { "id-${counter++}" }

    private val getDetail = GetRecipeDetailUseCase(recipes, ingredients, sauces)
    private val expand = ExpandRecipeUseCase(recipes, ingredients)
    private val saveRecipe = SaveRecipeUseCase(recipes, ids, tx)
    private val saveDraft = SaveRecipeDraftUseCase(recipes, ids, tx)
    private val publishDraft = PublishRecipeDraftUseCase(recipes, ids, tx)
    private val publishStored = PublishStoredRecipeVersionUseCase(recipes, tx)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `list view model exposes category rows`() =
        runTest {
            recipes.recipes += recipe(RECIPE_ID, "主菜", RecipeCategory.DISH)
            val viewModel = RecipesListViewModel(ListRecipesUseCase(recipes))

            advanceUntilIdle()

            val row =
                viewModel.uiState.value.rows
                    .single()
            assertThat(row.name).isEqualTo("主菜")
            assertThat(row.category).isEqualTo(RecipeCategory.DISH)
        }

    @Test
    fun `detail view model loads and expands at base servings`() =
        runTest {
            seedRecipeWithVersion(servings = 2)
            val viewModel = detailViewModel()

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isLoading).isFalse()
            assertThat(state.detail?.recipe?.name).isEqualTo("主菜")
            assertThat(state.detail?.baseServings).isEqualTo(2)
            assertThat(state.targetServings).isEqualTo("2")
            assertThat(
                state.expanded
                    ?.lines
                    ?.single()
                    ?.quantity
                    ?.amount,
            ).isWithin(1e-9).of(100.0)
        }

    @Test
    fun `changing target servings rescales the expansion`() =
        runTest {
            seedRecipeWithVersion(servings = 2)
            val viewModel = detailViewModel()
            advanceUntilIdle()

            viewModel.onTargetServingsChange("4")
            viewModel.expand()
            advanceUntilIdle()

            assertThat(
                viewModel.uiState.value.expanded
                    ?.lines
                    ?.single()
                    ?.quantity
                    ?.amount,
            ).isWithin(1e-9)
                .of(200.0)
        }

    @Test
    fun `editor creates a recipe and draft with servings and optional line`() =
        runTest {
            ingredients.items += ingredient()
            val viewModel = editorViewModel(null)
            advanceUntilIdle()
            viewModel.onNameChange("  新菜 ")
            viewModel.onCategoryChange(RecipeCategory.DISH)
            viewModel.onYieldAmountChange("1000")
            viewModel.onServingsChange("4")
            viewModel.addIngredient(ingredients.items.single())
            viewModel.onLineAmountChange(
                viewModel.uiState.value.lines
                    .single()
                    .key,
                "120",
            )
            viewModel.toggleOptional(
                viewModel.uiState.value.lines
                    .single()
                    .key,
            )
            viewModel.save(publish = false)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.error).isNull()
            assertThat(viewModel.uiState.value.savedRecipeId).isNotNull()
            val recipe = recipes.recipes.single()
            assertThat(recipe.name).isEqualTo("新菜")
            val version = recipes.versions.values.single()
            assertThat(version.status).isEqualTo(VersionStatus.DRAFT)
            assertThat(version.servings).isEqualTo(4)
            assertThat(version.components.single().isOptional).isTrue()
        }

    @Test
    fun `editor publishes a sub recipe with a pinned current version`() =
        runTest {
            val child = recipe(CHILD_ID, "高汤", RecipeCategory.STOCK)
            recipes.recipes += child
            val childVersion = version(CHILD_VERSION, child.id, versionNo = 1, status = VersionStatus.PUBLISHED)
            recipes.versions[childVersion.id] = childVersion
            recipes.current[child.id.value] = childVersion.id
            val viewModel = editorViewModel(null)
            advanceUntilIdle()

            viewModel.onNameChange("主菜")
            viewModel.onYieldAmountChange("1000")
            viewModel.onServingsChange("2")
            viewModel.addRecipeOption(
                RecipeOption(recipe = child, currentVersion = childVersion),
            )
            viewModel.onLineAmountChange(
                viewModel.uiState.value.lines
                    .single()
                    .key,
                "200",
            )
            viewModel.save(publish = true)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.error).isNull()
            val published =
                recipes.versions.values.single { it.recipeId.value != child.id.value }
            assertThat(published.status).isEqualTo(VersionStatus.PUBLISHED)
            val target = published.components.single().target as ComponentTarget.Recipe
            assertThat(target.recipeId).isEqualTo(child.id)
            assertThat(target.versionId).isEqualTo(childVersion.id)
        }

    @Test
    fun `editor rejects a blank name without writing`() =
        runTest {
            val viewModel = editorViewModel(null)
            advanceUntilIdle()

            viewModel.onNameChange("  ")
            viewModel.onYieldAmountChange("100")
            viewModel.save(publish = false)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.error).isEqualTo("名称不能为空。")
            assertThat(recipes.recipes).isEmpty()
        }

    @Test
    fun `detail view model publishes a stored draft and moves the current pointer`() =
        runTest {
            seedRecipeWithVersion(servings = 2, status = VersionStatus.DRAFT)
            recipes.current.clear()
            val viewModel = detailViewModel()
            advanceUntilIdle()
            val draftId = recipes.versions.keys.single()

            viewModel.publish(draftId)
            advanceUntilIdle()

            assertThat(recipes.versions.getValue(draftId).status).isEqualTo(VersionStatus.PUBLISHED)
            assertThat(recipes.current[RECIPE_ID]).isEqualTo(draftId)
        }

    private fun detailViewModel(): RecipeDetailViewModel =
        RecipeDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(RecipesRoutes.ARG_RECIPE_ID to RECIPE_ID)),
            getRecipeDetail = getDetail,
            expandRecipe = expand,
            publishStoredVersion = publishStored,
        )

    private fun editorViewModel(id: String?): RecipeEditorViewModel =
        RecipeEditorViewModel(
            savedStateHandle =
                SavedStateHandle(
                    mapOf(RecipesRoutes.ARG_RECIPE_ID to (id ?: RecipesRoutes.NEW_ID)),
                ),
            getRecipeDetail = getDetail,
            saveRecipe = saveRecipe,
            saveRecipeDraft = saveDraft,
            publishRecipeDraft = publishDraft,
            listUnits = ListUnitsUseCase(units),
            listIngredients = ListIngredientsUseCase(ingredients),
            listRecipeOptions = ListRecipeOptionsUseCase(recipes),
        )

    private suspend fun seedRecipeWithVersion(
        servings: Int,
        status: VersionStatus = VersionStatus.PUBLISHED,
    ) {
        ingredients.items += ingredient()
        val recipe = recipe(RECIPE_ID, "主菜", RecipeCategory.DISH)
        recipes.recipes += recipe
        val version =
            RecipeVersion(
                id = RecipeVersionId(VERSION_ID),
                recipeId = recipe.id,
                versionNo = 1,
                status = status,
                yield = Quantity(1000.0, gram()),
                servings = servings,
                isCurrent = status == VersionStatus.PUBLISHED,
                components =
                    listOf(
                        RecipeComponent(
                            lineNo = 1,
                            target = ComponentTarget.Ingredient(IngredientId(INGREDIENT_ID)),
                            quantity = Quantity(100.0, gram()),
                        ),
                    ),
            )
        recipes.versions[version.id] = version
        if (status == VersionStatus.PUBLISHED) {
            recipes.current[recipe.id.value] = version.id
        }
    }

    private fun recipe(
        id: String,
        name: String,
        category: RecipeCategory,
    ): Recipe =
        Recipe(
            id = RecipeId(id),
            name = name,
            normalizedName = name,
            category = category,
            status = EntityStatus.ACTIVE,
        )

    private fun ingredient(): Ingredient =
        Ingredient(
            id = IngredientId(INGREDIENT_ID),
            name = "低筋面粉",
            normalizedName = "低筋面粉",
            defaultUnitId = UnitId(UNIT_ID),
            status = EntityStatus.ACTIVE,
        )

    private fun version(
        id: String,
        recipeId: RecipeId,
        versionNo: Int,
        status: VersionStatus,
    ): RecipeVersion =
        RecipeVersion(
            id = RecipeVersionId(id),
            recipeId = recipeId,
            versionNo = versionNo,
            status = status,
            yield = Quantity(1000.0, gram()),
            servings = 1,
            components = emptyList(),
        )

    private fun gram(): UnitModel = UnitModel(UnitId(UNIT_ID), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 0)

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

    private class FakeSauceRepository : SauceRepository {
        override fun observeActive(): Flow<List<Sauce>> = flowOf(emptyList())

        override suspend fun get(id: SauceId): Outcome<Sauce> =
            Outcome.failure(CulinaryError.NotFound("sauce", id.value))

        override suspend fun getVersion(id: SauceVersionId): Outcome<SauceVersion> =
            Outcome.failure(CulinaryError.NotFound("sauce_version", id.value))

        override suspend fun listVersions(sauceId: SauceId): List<SauceVersion> = emptyList()

        override suspend fun findCurrentVersion(sauceId: SauceId): SauceVersion? = null

        override suspend fun nextVersionNo(sauceId: SauceId): Int = 1

        override suspend fun save(sauce: Sauce): Outcome<SauceId> = Outcome.success(sauce.id)

        override suspend fun saveDraft(version: SauceVersion): Outcome<SauceVersionId> = Outcome.success(version.id)

        override suspend fun savePublished(version: SauceVersion): Outcome<SauceVersionId> = Outcome.success(version.id)
    }

    private class FakeUnitRepository : UnitRepository {
        private val items =
            listOf(
                UnitModel(UnitId(UNIT_ID), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 0),
            )

        override suspend fun all(): List<UnitModel> = items

        override suspend fun find(id: UnitId): UnitModel? = items.firstOrNull { it.id == id }

        override suspend fun conversionsFor(ingredientId: IngredientId): List<IngredientUnitConversion> = emptyList()
    }

    private object ImmediateTransactionRunner : TransactionRunner {
        override suspend fun <T> write(block: suspend () -> T): Outcome<T> = Outcome.success(block())
    }

    private companion object {
        const val RECIPE_ID = "recipe-1"
        const val CHILD_ID = "recipe-child"
        const val CHILD_VERSION = "recipe-child-v1"
        const val VERSION_ID = "recipe-v1"
        const val INGREDIENT_ID = "ingredient-flour"
        const val UNIT_ID = "unit-g"
    }
}
