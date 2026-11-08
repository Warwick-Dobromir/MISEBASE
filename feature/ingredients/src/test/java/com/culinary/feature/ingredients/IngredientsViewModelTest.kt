package com.culinary.feature.ingredients

import androidx.lifecycle.SavedStateHandle
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.IngredientUnitConversion
import com.culinary.core.domain.model.KitchenRelatedRecord
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.KitchenRelationRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.transaction.TransactionRunner
import com.culinary.core.domain.usecase.ingredient.ArchiveIngredientUseCase
import com.culinary.core.domain.usecase.ingredient.GetIngredientDetailUseCase
import com.culinary.core.domain.usecase.ingredient.ListIngredientsUseCase
import com.culinary.core.domain.usecase.ingredient.SaveIngredientUseCase
import com.culinary.core.domain.usecase.kitchen.ObserveKitchenRelationsUseCase
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

/** 食材 ViewModel：列表单位标签、详情换算、编辑分类与归档。 */
@OptIn(ExperimentalCoroutinesApi::class)
class IngredientsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val ingredients = FakeIngredientRepository()
    private val units = FakeUnitRepository()
    private val tx = ImmediateTransactionRunner
    private var counter = 0

    private val saveIngredient = SaveIngredientUseCase(ingredients, IdGenerator { "id-${counter++}" }, tx)
    private val archiveIngredient = ArchiveIngredientUseCase(ingredients, tx)
    private val detail = GetIngredientDetailUseCase(ingredients, units)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `list resolves the default unit label for each row`() =
        runTest {
            ingredients.items += ingredient()
            val viewModel =
                IngredientsListViewModel(ListIngredientsUseCase(ingredients), ListUnitsUseCase(units))

            advanceUntilIdle()

            val row =
                viewModel.uiState.value.rows
                    .single()
            assertThat(row.name).isEqualTo("低筋面粉")
            assertThat(row.category).isEqualTo("粉类")
            assertThat(row.unitLabel).isEqualTo("g")
        }

    @Test
    fun `detail exposes conversions and default unit`() =
        runTest {
            ingredients.items += ingredient()
            units.conversions +=
                IngredientUnitConversion(UnitId("g"), UnitId("kg"), 0.001, "测试换算")
            val viewModel =
                IngredientDetailViewModel(
                    savedStateHandle = SavedStateHandle(mapOf(IngredientsRoutes.ARG_INGREDIENT_ID to ID)),
                    getIngredientDetail = detail,
                    archiveIngredient = archiveIngredient,
                    getRelations = emptyRelations(),
                )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.detail?.ingredient?.name).isEqualTo("低筋面粉")
            assertThat(state.detail?.defaultUnit?.symbol).isEqualTo("g")
            assertThat(state.detail?.conversions).hasSize(1)
            assertThat(
                state.detail
                    ?.conversions
                    ?.single()
                    ?.note,
            ).isEqualTo("测试换算")
        }

    @Test
    fun `editor saves category description and default unit`() =
        runTest {
            val viewModel = editorViewModel(null)

            advanceUntilIdle()
            viewModel.onNameChange("  低筋面粉 ")
            viewModel.onCategoryChange(" 粉类 ")
            viewModel.onDescriptionChange("蛋白质 8.5%")
            viewModel.onUnitChange(units.items.first())
            viewModel.save()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.saved).isTrue()
            val saved = ingredients.items.single()
            assertThat(saved.name).isEqualTo("低筋面粉")
            assertThat(saved.category).isEqualTo("粉类")
            assertThat(saved.description).isEqualTo("蛋白质 8.5%")
            assertThat(saved.defaultUnitId).isEqualTo(UnitId("g"))
        }

    @Test
    fun `editor rejects a blank name without writing`() =
        runTest {
            val viewModel = editorViewModel(null)

            advanceUntilIdle()
            viewModel.onNameChange("   ")
            viewModel.save()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.error).isEqualTo("名称不能为空。")
            assertThat(ingredients.items).isEmpty()
        }

    @Test
    fun `archive marks the detail as archived`() =
        runTest {
            ingredients.items += ingredient()
            val viewModel =
                IngredientDetailViewModel(
                    savedStateHandle = SavedStateHandle(mapOf(IngredientsRoutes.ARG_INGREDIENT_ID to ID)),
                    getIngredientDetail = detail,
                    archiveIngredient = archiveIngredient,
                    getRelations = emptyRelations(),
                )
            advanceUntilIdle()

            viewModel.archive()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.archived).isTrue()
            assertThat(ingredients.archived).containsExactly(IngredientId(ID))
        }

    private fun editorViewModel(id: String?): IngredientEditorViewModel =
        IngredientEditorViewModel(
            savedStateHandle =
                SavedStateHandle(
                    mapOf(IngredientsRoutes.ARG_INGREDIENT_ID to (id ?: IngredientsRoutes.NEW_ID)),
                ),
            getIngredientDetail = detail,
            saveIngredient = saveIngredient,
            listUnits = ListUnitsUseCase(units),
        )

    private fun ingredient(): Ingredient =
        Ingredient(
            id = IngredientId(ID),
            name = "低筋面粉",
            normalizedName = "低筋面粉",
            defaultUnitId = UnitId("g"),
            status = EntityStatus.ACTIVE,
            category = "粉类",
        )

    private class FakeIngredientRepository : IngredientRepository {
        val items = mutableListOf<Ingredient>()
        val archived = mutableListOf<IngredientId>()

        override fun observeActive(): Flow<List<Ingredient>> = flowOf(items.filter { it.isActive })

        override suspend fun get(id: IngredientId): Outcome<Ingredient> =
            items.firstOrNull { it.id == id }?.let { Outcome.success(it) }
                ?: Outcome.failure(CulinaryError.NotFound("ingredient", id.value))

        override suspend fun save(ingredient: Ingredient): Outcome<IngredientId> {
            items.removeAll { it.id == ingredient.id }
            items += ingredient
            return Outcome.success(ingredient.id)
        }

        override suspend fun archive(id: IngredientId): Outcome<Unit> {
            archived += id
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) {
                items[index] = items[index].copy(status = EntityStatus.ARCHIVED)
            }
            return Outcome.success(Unit)
        }
    }

    private class FakeUnitRepository : UnitRepository {
        val items =
            listOf(
                UnitModel(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 0),
                UnitModel(UnitId("kg"), "kg", "千克", "kg", UnitDimension.MASS, 1000.0, false, false, 0),
            )
        val conversions = mutableListOf<IngredientUnitConversion>()

        override suspend fun all(): List<UnitModel> = items

        override suspend fun find(id: UnitId): UnitModel? = items.firstOrNull { it.id == id }

        override suspend fun conversionsFor(ingredientId: IngredientId): List<IngredientUnitConversion> = conversions
    }

    private object ImmediateTransactionRunner : TransactionRunner {
        override suspend fun <T> write(block: suspend () -> T): Outcome<T> = Outcome.success(block())
    }

    private companion object {
        const val ID = "ingredient-flour"
    }
}

private fun emptyRelations(): ObserveKitchenRelationsUseCase =
    ObserveKitchenRelationsUseCase(
        object : KitchenRelationRepository {
            override fun observeForRecord(recordId: String): Flow<List<KitchenRelatedRecord>> = flowOf(emptyList())
        },
    )
