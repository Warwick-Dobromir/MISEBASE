package com.culinary.core.domain.usecase.sauce

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.SauceRepository
import com.culinary.core.domain.transaction.TransactionRunner
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import com.culinary.core.domain.model.Unit as UnitModel

/** 酱汁用例：名称校验、草稿版本、发布与详情投影（Stage 3）。 */
class SauceUseCasesTest {
    private val sauces = FakeSauceRepository()
    private val ingredients = FakeIngredientRepository()
    private val ids = IdGenerator { "id-${counter++}" }
    private var counter = 0

    private val saveSauce = SaveSauceUseCase(sauces, ids, ImmediateTransactionRunner)
    private val saveDraft = SaveSauceDraftUseCase(sauces, ids, ImmediateTransactionRunner)
    private val publish = PublishSauceVersionUseCase(sauces, ImmediateTransactionRunner)
    private val detail = GetSauceDetailUseCase(sauces, ingredients)

    @Test
    fun `rejects a blank sauce name`() =
        runBlocking<Unit> {
            val result = saveSauce(SaveSauceInput(name = "   "))

            assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-VAL-001")
            assertThat(sauces.saved).isEmpty()
        }

    @Test
    fun `creates an active sauce with normalized name and family`() =
        runBlocking<Unit> {
            val result = saveSauce(SaveSauceInput(name = "  Miso   Glaze ", family = " 乳化酱 "))

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val saved = sauces.saved.single()
            assertThat(saved.name).isEqualTo("Miso   Glaze")
            assertThat(saved.normalizedName).isEqualTo("miso glaze")
            assertThat(saved.family).isEqualTo("乳化酱")
            assertThat(saved.status).isEqualTo(EntityStatus.ACTIVE)
        }

    @Test
    fun `draft numbers component lines from one and keeps optional flags`() =
        runBlocking<Unit> {
            saveSauce(SaveSauceInput(name = "基础酱"))
            val sauceId = sauces.saved.single().id

            val result =
                saveDraft(
                    SaveSauceDraftInput(
                        sauceId = sauceId,
                        yieldAmount = 500.0,
                        yieldUnit = gram(),
                        components =
                            listOf(
                                SauceDraftLineInput(IngredientId("flour"), 120.0, gram()),
                                SauceDraftLineInput(IngredientId("salt"), 3.0, gram(), isOptional = true),
                            ),
                    ),
                )

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val version = sauces.versions.values.single()
            assertThat(version.versionNo).isEqualTo(1)
            assertThat(version.status).isEqualTo(VersionStatus.DRAFT)
            assertThat(version.components.map { it.lineNo }).containsExactly(1, 2).inOrder()
            assertThat(version.components[1].isOptional).isTrue()
        }

    @Test
    fun `rejects a non positive yield or component quantity`() =
        runBlocking<Unit> {
            saveSauce(SaveSauceInput(name = "基础酱"))
            val sauceId = sauces.saved.single().id

            val badYield =
                saveDraft(
                    SaveSauceDraftInput(sauceId, yieldAmount = 0.0, yieldUnit = gram(), components = emptyList()),
                )
            val badLine =
                saveDraft(
                    SaveSauceDraftInput(
                        sauceId,
                        yieldAmount = 10.0,
                        yieldUnit = gram(),
                        components = listOf(SauceDraftLineInput(IngredientId("flour"), -1.0, gram())),
                    ),
                )

            assertThat((badYield as Outcome.Failure).error).isInstanceOf(CulinaryError.Validation::class.java)
            assertThat((badLine as Outcome.Failure).error).isInstanceOf(CulinaryError.Validation::class.java)
            assertThat(sauces.versions).isEmpty()
        }

    @Test
    fun `publishing flips status and current pointer and rejects a second publish`() =
        runBlocking<Unit> {
            saveSauce(SaveSauceInput(name = "基础酱"))
            val sauceId = sauces.saved.single().id
            saveDraft(
                SaveSauceDraftInput(
                    sauceId,
                    yieldAmount = 500.0,
                    yieldUnit = gram(),
                    components = listOf(SauceDraftLineInput(IngredientId("flour"), 100.0, gram())),
                ),
            )
            val versionId = sauces.versions.keys.single()

            val first = publish(versionId)
            val second = publish(versionId)

            assertThat(first).isInstanceOf(Outcome.Success::class.java)
            assertThat(sauces.versions.getValue(versionId).status).isEqualTo(VersionStatus.PUBLISHED)
            assertThat(sauces.current[sauceId.value]).isEqualTo(versionId)
            assertThat((second as Outcome.Failure).error.code.value).isEqualTo("CUL-CON-001")
        }

    @Test
    fun `detail resolves ingredient labels and exposes the current version`() =
        runBlocking<Unit> {
            ingredients.items +=
                Ingredient(
                    id = IngredientId("flour"),
                    name = "低筋面粉",
                    normalizedName = "低筋面粉",
                    defaultUnitId = UnitId("g"),
                    status = EntityStatus.ACTIVE,
                )
            saveSauce(SaveSauceInput(name = "基础酱", family = "面糊"))
            val sauceId = sauces.saved.single().id
            saveDraft(
                SaveSauceDraftInput(
                    sauceId,
                    yieldAmount = 500.0,
                    yieldUnit = gram(),
                    components = listOf(SauceDraftLineInput(IngredientId("flour"), 120.0, gram())),
                ),
            )
            val versionId = sauces.versions.keys.single()
            publish(versionId)

            val result = detail(sauceId)

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val value = (result as Outcome.Success).value
            assertThat(value.sauce.name).isEqualTo("基础酱")
            assertThat(value.currentVersion?.id).isEqualTo(versionId)
            assertThat(value.lines.single().label).isEqualTo("低筋面粉")
            assertThat(
                value.lines
                    .single()
                    .quantity.amount,
            ).isWithin(1e-9).of(120.0)
            assertThat(value.hasPublishedVersion).isTrue()
        }

    private fun gram(): UnitModel = UnitModel(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 0)

    private class FakeSauceRepository : SauceRepository {
        val saved = mutableListOf<Sauce>()
        val versions = mutableMapOf<SauceVersionId, SauceVersion>()
        val current = mutableMapOf<String, SauceVersionId>()

        override fun observeActive(): Flow<List<Sauce>> = flowOf(saved.filter { it.isActive })

        override suspend fun get(id: SauceId): Outcome<Sauce> =
            saved.firstOrNull { it.id == id }?.let { Outcome.success(it) }
                ?: Outcome.failure(CulinaryError.NotFound("sauce", id.value))

        override suspend fun getVersion(id: SauceVersionId): Outcome<SauceVersion> =
            versions[id]?.let { Outcome.success(it) }
                ?: Outcome.failure(CulinaryError.NotFound("sauce_version", id.value))

        override suspend fun listVersions(sauceId: SauceId): List<SauceVersion> =
            versions.values.filter { it.sauceId == sauceId }.sortedByDescending { it.versionNo }

        override suspend fun findCurrentVersion(sauceId: SauceId): SauceVersion? =
            current[sauceId.value]?.let { versions[it] }

        override suspend fun nextVersionNo(sauceId: SauceId): Int =
            (versions.values.filter { it.sauceId == sauceId }.maxOfOrNull { it.versionNo } ?: 0) + 1

        override suspend fun save(sauce: Sauce): Outcome<SauceId> {
            saved.removeAll { it.id == sauce.id }
            saved += sauce
            return Outcome.success(sauce.id)
        }

        override suspend fun saveDraft(version: SauceVersion): Outcome<SauceVersionId> {
            versions[version.id] = version
            return Outcome.success(version.id)
        }

        override suspend fun savePublished(version: SauceVersion): Outcome<SauceVersionId> {
            versions.values.filter { it.sauceId == version.sauceId }.forEach { existing ->
                versions[existing.id] = existing.copy(isCurrent = false)
            }
            val published = version.copy(status = VersionStatus.PUBLISHED, isCurrent = true)
            versions[published.id] = published
            current[version.sauceId.value] = published.id
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
}
