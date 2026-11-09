package com.culinary.cooking

import androidx.lifecycle.SavedStateHandle
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.KitchenTime
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.KitchenStep
import com.culinary.core.domain.model.KitchenStepCodec
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeCategory
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
import com.culinary.core.domain.usecase.archive.ArchiveImageUseCase
import com.culinary.core.domain.usecase.recipe.GetRecipeDetailUseCase
import com.culinary.core.domain.usecase.sauce.GetSauceDetailUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import com.culinary.core.domain.model.Unit as UnitModel

/** 制作模式计时：±1 秒进位/借位、结束停留、完成态、提示音只播放一次。 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CookingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val alert = FakeCookingAlert()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `adjusting by one second carries and borrows through minutes`() =
        runTest {
            val viewModel = viewModel(steps = listOf(step("a", 59), step("b", 30)))
            advanceUntilIdle()

            viewModel.adjust(1)
            assertThat(viewModel.uiState.value.remainingSeconds).isEqualTo(60)
            assertThat(KitchenTime.format(viewModel.uiState.value.remainingSeconds)).isEqualTo("00/01/00")

            viewModel.adjust(-1)
            assertThat(viewModel.uiState.value.remainingSeconds).isEqualTo(59)
            assertThat(KitchenTime.format(viewModel.uiState.value.remainingSeconds)).isEqualTo("00/00/59")
        }

    @Test
    fun `countdown end stays on the current step and requires an explicit advance`() =
        runTest {
            val handle = handle(steps = listOf(step("a", 1), step("b", 1)))
            val viewModel = viewModel(handle = handle, steps = listOf(step("a", 1), step("b", 1)))
            advanceUntilIdle()

            viewModel.toggleTimer()
            ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
            advanceTimeBy(500)

            val completed = viewModel.uiState.value
            assertThat(completed.index).isEqualTo(0)
            assertThat(completed.remainingSeconds).isEqualTo(0)
            assertThat(completed.isRunning).isFalse()
            assertThat(completed.currentCompleted).isTrue()
            assertThat(completed.finished).isFalse()
            assertThat(alert.plays).isEqualTo(1)

            viewModel.advanceAfterCompletion()

            assertThat(viewModel.uiState.value.index).isEqualTo(1)
            assertThat(viewModel.uiState.value.currentCompleted).isFalse()
            assertThat(alert.plays).isEqualTo(1)
        }

    @Test
    fun `completion state survives a recreated view model and does not replay the tone`() =
        runTest {
            val handle = handle(steps = listOf(step("a", 1)))
            val viewModel = viewModel(handle = handle, steps = listOf(step("a", 1)))
            advanceUntilIdle()

            viewModel.toggleTimer()
            ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
            advanceTimeBy(500)
            assertThat(alert.plays).isEqualTo(1)

            val recreated = viewModel(handle = handle, steps = listOf(step("a", 1)))
            advanceUntilIdle()

            assertThat(recreated.uiState.value.currentCompleted).isTrue()
            assertThat(recreated.uiState.value.remainingSeconds).isEqualTo(0)
            assertThat(alert.plays).isEqualTo(1)
        }

    @Test
    fun `last completed step finishes only after the user confirms it`() =
        runTest {
            val viewModel = viewModel(steps = listOf(step("only", 1)))
            advanceUntilIdle()

            viewModel.toggleTimer()
            ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
            advanceTimeBy(500)

            assertThat(viewModel.uiState.value.finished).isFalse()
            viewModel.advanceAfterCompletion()
            assertThat(viewModel.uiState.value.finished).isTrue()
            assertThat(viewModel.uiState.value.progress).isEqualTo(1f)
            assertThat(alert.plays).isEqualTo(1)
        }

    @Test
    fun `a no-time step still blocks until the user confirms it`() =
        runTest {
            val viewModel = viewModel(steps = listOf(step("timed", 60), step("manual", 0), step("next", 30)))
            advanceUntilIdle()

            viewModel.next()
            assertThat(viewModel.uiState.value.index).isEqualTo(1)
            assertThat(viewModel.uiState.value.blockingStep).isNotNull()

            viewModel.confirmBlockingStep()

            assertThat(viewModel.uiState.value.index).isEqualTo(2)
            assertThat(viewModel.uiState.value.current?.number).isEqualTo(3)
            assertThat(viewModel.uiState.value.blockingStep).isNull()
        }

    @Test
    fun `starting from a later step ignores earlier steps but keeps unified numbering`() =
        runTest {
            val viewModel =
                viewModel(
                    steps = listOf(step("ignored", 60), step("start", 0), step("end", 30)),
                    startStep = 2,
                )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.plan).hasSize(2)
            assertThat(state.plan.map { it.originalStepNo }).containsExactly(2, 3).inOrder()
            assertThat(state.plan.map { it.number }).containsExactly(1, 2).inOrder()
            assertThat(state.blockingStep?.number).isEqualTo(1)
        }

    private fun handle(
        steps: List<KitchenStep>,
        startStep: Int = 1,
    ): SavedStateHandle {
        val recipe = recipe()
        val version = version(recipe, steps)
        val handle =
            SavedStateHandle(
                mapOf(
                    CookingRoute.KIND_ARG to CookingRoute.KIND_RECIPE,
                    CookingRoute.ID_ARG to recipe.id.value,
                    CookingRoute.STEP_ARG to startStep,
                ),
            )
        repository = FakeRecipeRepository(recipe, version)
        return handle
    }

    private var repository: FakeRecipeRepository = FakeRecipeRepository(recipe(), version(recipe(), emptyList()))

    private fun viewModel(
        handle: SavedStateHandle? = null,
        steps: List<KitchenStep>,
        startStep: Int = 1,
    ): CookingViewModel {
        val saved = handle ?: handle(steps, startStep)
        val ingredients = FakeIngredientRepository()
        val sauces = FakeSauceRepository()
        return CookingViewModel(
            savedStateHandle = saved,
            getSauceDetail = GetSauceDetailUseCase(sauces, ingredients, repository),
            getRecipeDetail = GetRecipeDetailUseCase(repository, ingredients, sauces),
            archiveImages = ArchiveImageUseCase(),
            alert = alert,
        )
    }

    private fun recipe(): Recipe =
        Recipe(
            id = RecipeId("recipe-1"),
            name = "测试菜谱",
            normalizedName = "测试菜谱",
            category = RecipeCategory.DISH,
            status = EntityStatus.ACTIVE,
        )

    private fun version(
        recipe: Recipe,
        steps: List<KitchenStep>,
    ): RecipeVersion =
        RecipeVersion(
            id = RecipeVersionId("recipe-v1"),
            recipeId = recipe.id,
            versionNo = 1,
            status = com.culinary.core.domain.model.VersionStatus.PUBLISHED,
            yield = Quantity(1000.0, gram()),
            components = emptyList(),
            servings = 1,
            isCurrent = true,
            stepsJson = KitchenStepCodec.encode(steps),
        )

    private fun step(
        title: String,
        seconds: Int,
    ): KitchenStep = KitchenStep(id = title, title = title, body = "$title 说明", timeSeconds = seconds)

    private fun gram(): UnitModel =
        UnitModel(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 0)

    private class FakeCookingAlert : CookingAlert {
        var plays: Int = 0

        override fun playCompletion() {
            plays++
        }
    }

    private class FakeRecipeRepository(
        private val recipe: Recipe,
        private val version: RecipeVersion,
    ) : RecipeRepository {
        override fun observeActive(): Flow<List<Recipe>> = flowOf(listOf(recipe))

        override suspend fun get(id: RecipeId): Outcome<Recipe> =
            if (id == recipe.id) Outcome.success(recipe) else Outcome.failure(CulinaryError.NotFound("recipe", id.value))

        override suspend fun getVersion(id: RecipeVersionId): RecipeVersion? = version.takeIf { it.id == id }

        override suspend fun getCurrentVersion(recipeId: RecipeId): RecipeVersion? =
            version.takeIf { it.recipeId == recipeId }

        override suspend fun listVersions(recipeId: RecipeId): List<RecipeVersion> =
            listOf(version).filter { it.recipeId == recipeId }

        override suspend fun nextVersionNo(recipeId: RecipeId): Int = 2

        override suspend fun save(recipe: Recipe): Outcome<RecipeId> = Outcome.success(recipe.id)

        override suspend fun saveDraft(version: RecipeVersion): RecipeVersionId = version.id

        override suspend fun savePublished(version: RecipeVersion): Outcome<RecipeVersionId> = Outcome.success(version.id)
    }

    private class FakeIngredientRepository : IngredientRepository {
        override fun observeActive(): Flow<List<Ingredient>> = flowOf(emptyList())

        override suspend fun get(id: IngredientId): Outcome<Ingredient> =
            Outcome.failure(CulinaryError.NotFound("ingredient", id.value))

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

        override suspend fun savePublished(version: SauceVersion): Outcome<SauceVersionId> =
            Outcome.success(version.id)

        override suspend fun archive(id: SauceId): Outcome<Unit> = Outcome.success(Unit)
    }
}
