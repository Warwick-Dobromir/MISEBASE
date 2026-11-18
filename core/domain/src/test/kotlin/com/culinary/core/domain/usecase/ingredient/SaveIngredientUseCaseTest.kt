package com.culinary.core.domain.usecase.ingredient

import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.transaction.TransactionRunner
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** 保存/归档食材：校验、规范化与事务边界（阶段 7）。 */
class SaveIngredientUseCaseTest {
    private val repository = FakeIngredientRepository()
    private val useCase =
        SaveIngredientUseCase(
            repository = repository,
            idGenerator = IdGenerator { "id-1" },
            transactionRunner = ImmediateTransactionRunner,
        )

    @Test
    fun `rejects a blank name`() =
        runBlocking<Unit> {
            val result = useCase(SaveIngredientInput(name = "   "))

            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
            val error = (result as Outcome.Failure).error
            assertThat(error.code.value).isEqualTo("CUL-VAL-001")
            assertThat(repository.saved).isEmpty()
        }

    @Test
    fun `rejects a name over the length limit`() =
        runBlocking<Unit> {
            val result = useCase(SaveIngredientInput(name = "a".repeat(121)))

            assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-VAL-001")
            assertThat(repository.saved).isEmpty()
        }

    @Test
    fun `creates an active ingredient with a normalized name`() =
        runBlocking<Unit> {
            val result = useCase(SaveIngredientInput(name = "  Soy   Sauce "))

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            assertThat((result as Outcome.Success).value.value).isEqualTo("id-1")
            val saved = repository.saved.single()
            assertThat(saved.name).isEqualTo("Soy   Sauce")
            assertThat(saved.normalizedName).isEqualTo("soy sauce")
            assertThat(saved.status).isEqualTo(EntityStatus.ACTIVE)
        }

    @Test
    fun `keeps the id when editing an existing ingredient`() =
        runBlocking<Unit> {
            // 编辑前仓储里必须已有该食材：SaveIngredientUseCase 对未知 id 返回 NotFound。
            repository.saved +=
                Ingredient(
                    id = IngredientId("existing"),
                    name = "酱油",
                    normalizedName = "酱油",
                    defaultUnitId = null,
                    status = EntityStatus.ACTIVE,
                )

            useCase(SaveIngredientInput(name = "酱油", id = IngredientId("existing")))

            assertThat(
                repository.saved
                    .single()
                    .id.value,
            ).isEqualTo("existing")
        }

    @Test
    fun `saves category description and default unit`() =
        runBlocking<Unit> {
            useCase(
                SaveIngredientInput(
                    name = "低筋面粉",
                    defaultUnitId =
                        com.culinary.core.domain.model
                            .UnitId("g"),
                    category = " 粉类 ",
                    description = "蛋白质 8.5%",
                ),
            )

            val saved = repository.saved.single()
            assertThat(saved.category).isEqualTo("粉类")
            assertThat(saved.description).isEqualTo("蛋白质 8.5%")
            assertThat(saved.defaultUnitId?.value).isEqualTo("g")
        }

    @Test
    fun `saves the complete ingredient knowledge fields`() =
        runBlocking<Unit> {
            useCase(
                SaveIngredientInput(
                    name = "百里香",
                    englishName = "Thyme",
                    scientificName = "Thymus vulgaris",
                    flavorProfile = "草本、清新",
                    seasonality = "春夏",
                    origin = "地中海",
                    selectionNotes = "叶片完整、香气明显",
                    prepNotes = "适合煎烤前加入",
                    storageNotes = "冷藏并保持干燥",
                    allergenNotes = "无已知常见过敏原",
                    exampleProducts = "鲜枝、干叶",
                ),
            )

            val saved = repository.saved.single()
            assertThat(saved.englishName).isEqualTo("Thyme")
            assertThat(saved.scientificName).isEqualTo("Thymus vulgaris")
            assertThat(saved.flavorProfile).isEqualTo("草本、清新")
            assertThat(saved.seasonality).isEqualTo("春夏")
            assertThat(saved.origin).isEqualTo("地中海")
            assertThat(saved.selectionNotes).isEqualTo("叶片完整、香气明显")
            assertThat(saved.prepNotes).isEqualTo("适合煎烤前加入")
            assertThat(saved.storageNotes).isEqualTo("冷藏并保持干燥")
            assertThat(saved.allergenNotes).isEqualTo("无已知常见过敏原")
            assertThat(saved.exampleProducts).isEqualTo("鲜枝、干叶")
        }

    @Test
    fun `clears optional fields when an edit submits them empty`() =
        runBlocking<Unit> {
            repository.saved +=
                Ingredient(
                    id = IngredientId("existing-fields"),
                    name = "百里香",
                    normalizedName = "百里香",
                    defaultUnitId = null,
                    status = EntityStatus.ACTIVE,
                    flavorProfile = "旧风味",
                    prepNotes = "旧用途",
                )

            useCase(SaveIngredientInput(name = "百里香", id = IngredientId("existing-fields")))

            val saved = repository.saved.single()
            assertThat(saved.flavorProfile).isNull()
            assertThat(saved.prepNotes).isNull()
        }

    @Test
    fun `rejects over long category and description`() =
        runBlocking<Unit> {
            val category =
                useCase(
                    SaveIngredientInput(
                        name = "低筋面粉",
                        category = "c".repeat(61),
                    ),
                )
            val description =
                useCase(
                    SaveIngredientInput(
                        name = "低筋面粉",
                        description = "d".repeat(501),
                    ),
                )

            assertThat((category as Outcome.Failure).error.code.value).isEqualTo("CUL-VAL-001")
            assertThat((description as Outcome.Failure).error.code.value).isEqualTo("CUL-VAL-001")
            assertThat(repository.saved).isEmpty()
        }

    @Test
    fun `archives through the repository inside a transaction`() =
        runBlocking<Unit> {
            val archive = ArchiveIngredientUseCase(repository, ImmediateTransactionRunner)

            val result = archive(IngredientId("id-1"))

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            assertThat(repository.archived).containsExactly(IngredientId("id-1"))
        }

    private class FakeIngredientRepository : IngredientRepository {
        val saved = mutableListOf<Ingredient>()
        val archived = mutableListOf<IngredientId>()

        override fun observeActive(): Flow<List<Ingredient>> = flowOf(saved.filter { it.status == EntityStatus.ACTIVE })

        override suspend fun get(id: IngredientId): Outcome<Ingredient> =
            saved
                .firstOrNull { it.id == id }
                ?.let { Outcome.success(it) }
                ?: Outcome.failure(
                    com.culinary.core.common.CulinaryError
                        .NotFound("ingredient", id.value),
                )

        override suspend fun save(ingredient: Ingredient): Outcome<IngredientId> {
            // 真实仓储是 upsert：同 id 覆盖，不追加第二条。
            saved.removeAll { it.id == ingredient.id }
            saved += ingredient
            return Outcome.success(ingredient.id)
        }

        override suspend fun archive(id: IngredientId): Outcome<Unit> {
            archived += id
            return Outcome.success(Unit)
        }
    }

    private object ImmediateTransactionRunner : TransactionRunner {
        override suspend fun <T> write(block: suspend () -> T): Outcome<T> = Outcome.success(block())
    }
}
