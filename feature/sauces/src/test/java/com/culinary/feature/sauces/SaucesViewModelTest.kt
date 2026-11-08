package com.culinary.feature.sauces

import androidx.lifecycle.SavedStateHandle
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.KitchenRelatedRecord
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceComponent
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceImage
import com.culinary.core.domain.model.SauceImageOwner
import com.culinary.core.domain.model.SauceImageRole
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.KitchenRelationRepository
import com.culinary.core.domain.repository.SauceImageRepository
import com.culinary.core.domain.repository.SauceRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.transaction.TransactionRunner
import com.culinary.core.domain.usecase.ingredient.ListIngredientsUseCase
import com.culinary.core.domain.usecase.kitchen.ObserveKitchenRelationsUseCase
import com.culinary.core.domain.usecase.sauce.ArchiveSauceUseCase
import com.culinary.core.domain.usecase.sauce.GetSauceDetailUseCase
import com.culinary.core.domain.usecase.sauce.ListSaucesUseCase
import com.culinary.core.domain.usecase.sauce.PublishSauceVersionUseCase
import com.culinary.core.domain.usecase.sauce.SauceImageUseCase
import com.culinary.core.domain.usecase.sauce.SaveSauceDraftUseCase
import com.culinary.core.domain.usecase.sauce.SaveSauceRelationsUseCase
import com.culinary.core.domain.usecase.sauce.SaveSauceUseCase
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

/** 酱汁 ViewModel：列表、详情与编辑器状态（Stage 3）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaucesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sauces = FakeSauceRepository()
    private val ingredients = FakeIngredientRepository()
    private val units = FakeUnitRepository()
    private val ids = IdGenerator { "id-${counter++}" }
    private var counter = 0
    private val tx = ImmediateTransactionRunner

    private val saveSauce = SaveSauceUseCase(sauces, ids, tx)
    private val saveDraft = SaveSauceDraftUseCase(sauces, ids, tx)
    private val publish = PublishSauceVersionUseCase(sauces, tx)
    private val detail = GetSauceDetailUseCase(sauces, ingredients)
    private val relations = FakeKitchenRelationRepository()
    private val images = FakeSauceImageRepository()
    private val archiveSauce = ArchiveSauceUseCase(sauces, tx)
    private val saveRelations = SaveSauceRelationsUseCase(relations)
    private val imageUseCase = SauceImageUseCase(images)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `list view model exposes repository items`() =
        runTest {
            sauces.saved += sauce()
            val viewModel = SaucesListViewModel(ListSaucesUseCase(sauces))

            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isLoading).isFalse()
            assertThat(
                viewModel.uiState.value.items
                    .map { it.name },
            ).containsExactly("基础酱")
        }

    @Test
    fun `detail view model loads current version and lines`() =
        runTest {
            seedPublishedSauce()
            val viewModel =
                SauceDetailViewModel(
                    savedStateHandle = SavedStateHandle(mapOf(SaucesRoutes.ARG_SAUCE_ID to SAUCE_ID)),
                    getSauceDetail = detail,
                    publishSauceVersion = publish,
                    getRelations = emptyRelations(),
                )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isLoading).isFalse()
            assertThat(state.detail?.sauce?.name).isEqualTo("基础酱")
            assertThat(
                state.detail
                    ?.lines
                    ?.single()
                    ?.label,
            ).isEqualTo("低筋面粉")
        }

    @Test
    fun `editor creates a sauce and publishes its first version`() =
        runTest {
            ingredients.items += ingredient()
            val viewModel = editorViewModel(null)

            advanceUntilIdle()
            viewModel.onNameChange("  新酱汁 ")
            viewModel.onEnglishNameChange("New Sauce")
            viewModel.onFamilyChange("乳化酱")
            viewModel.onYieldAmountChange("500")
            viewModel.addIngredient(ingredients.items.single())
            viewModel.onLineAmountChange(
                viewModel.uiState.value.lines
                    .single()
                    .key,
                "120",
            )
            viewModel.save()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.error).isNull()
            assertThat(state.savedSauceId).isNotNull()
            val saved = sauces.saved.single()
            assertThat(saved.name).isEqualTo("新酱汁")
            assertThat(saved.englishName).isEqualTo("New Sauce")
            assertThat(saved.family).isEqualTo("乳化酱")
            val version = sauces.versions.values.single()
            assertThat(version.status).isEqualTo(VersionStatus.PUBLISHED)
            assertThat(sauces.current[version.sauceId.value]).isEqualTo(version.id)
            assertThat(version.yield.amount).isWithin(1e-9).of(500.0)
            assertThat(
                version.components
                    .single()
                    .quantity.amount,
            ).isWithin(1e-9).of(120.0)
        }

    @Suppress("LongMethod") // 单测需要一次性铺三类目标并核对保存结果。
    @Test
    fun `editor keeps imported recipe and sauce rows read only on save`() =
        runTest {
            ingredients.items += ingredient()
            sauces.saved += sauce()
            val recipeTarget =
                ComponentTarget.Recipe(RecipeId("recipe-base"), RecipeVersionId("recipe-v3"))
            val sauceTarget =
                ComponentTarget.Sauce(SauceId("sauce-base"), SauceVersionId("sauce-v2"))
            val version =
                SauceVersion(
                    id = SauceVersionId(VERSION_ID),
                    sauceId = SauceId(SAUCE_ID),
                    versionNo = 1,
                    status = VersionStatus.PUBLISHED,
                    yield = Quantity(500.0, gram()),
                    components =
                        listOf(
                            SauceComponent(
                                lineNo = 1,
                                target = ComponentTarget.Ingredient(IngredientId(INGREDIENT_ID)),
                                quantity = Quantity(100.0, gram()),
                            ),
                            SauceComponent(
                                lineNo = 2,
                                target = recipeTarget,
                                quantity = Quantity(50.0, gram()),
                            ),
                            SauceComponent(
                                lineNo = 3,
                                target = sauceTarget,
                                quantity = Quantity(20.0, gram()),
                            ),
                        ),
                    isCurrent = true,
                )
            sauces.versions[version.id] = version
            sauces.current[SAUCE_ID] = version.id
            val viewModel = editorViewModel(SAUCE_ID)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.lines).hasSize(3)
            assertThat(state.lines.map { it.isReadOnly }).containsExactly(false, true, true).inOrder()
            val loadedTargets = state.lines.map { it.target }
            assertThat(loadedTargets)
                .containsExactly(
                    ComponentTarget.Ingredient(IngredientId(INGREDIENT_ID)),
                    recipeTarget,
                    sauceTarget,
                ).inOrder()

            viewModel.onEnglishNameChange("Base Sauce")
            viewModel.save()
            advanceUntilIdle()

            val latest = sauces.versions.values.maxByOrNull { it.versionNo }
            assertThat(latest).isNotNull()
            val savedTargets = requireNotNull(latest).components.map { it.target }
            assertThat(savedTargets)
                .containsExactly(
                    ComponentTarget.Ingredient(IngredientId(INGREDIENT_ID)),
                    recipeTarget,
                    sauceTarget,
                ).inOrder()
            assertThat(viewModel.uiState.value.error).isNull()
        }

    @Test
    fun `editor save and publish marks the version published`() =
        runTest {
            val viewModel = editorViewModel(null)

            advanceUntilIdle()
            viewModel.onNameChange("发布酱")
            viewModel.onEnglishNameChange("Publish Sauce")
            viewModel.onYieldAmountChange("250")
            viewModel.save()
            advanceUntilIdle()

            val version = sauces.versions.values.single()
            assertThat(version.status).isEqualTo(VersionStatus.PUBLISHED)
            assertThat(sauces.current[version.sauceId.value]).isEqualTo(version.id)
            assertThat(viewModel.uiState.value.savedSauceId).isNotNull()
        }

    @Test
    fun `editor reports a blank name without writing`() =
        runTest {
            val viewModel = editorViewModel(null)

            advanceUntilIdle()
            viewModel.onNameChange("  ")
            viewModel.onEnglishNameChange("Blank")
            viewModel.onYieldAmountChange("100")
            viewModel.save()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.error).isEqualTo("中文名称不能为空。")
            assertThat(sauces.saved).isEmpty()
        }

    @Test
    fun `detail view model rejects publishing an already published version`() =
        runTest {
            seedPublishedSauce()
            val versionId = sauces.versions.keys.single()
            val viewModel =
                SauceDetailViewModel(
                    savedStateHandle = SavedStateHandle(mapOf(SaucesRoutes.ARG_SAUCE_ID to SAUCE_ID)),
                    getSauceDetail = detail,
                    publishSauceVersion = publish,
                    getRelations = emptyRelations(),
                )
            advanceUntilIdle()

            viewModel.publish(versionId)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.error).contains("CUL-CON-001")
        }

    @Test
    fun `editing a published sauce reuses the pending draft on subsequent saves`() =
        runTest {
            seedPublishedSauce()
            val viewModel = editorViewModel(SAUCE_ID)
            advanceUntilIdle()

            viewModel.onEnglishNameChange("Base Sauce")
            viewModel.onDescriptionChange("first draft")
            viewModel.save()
            advanceUntilIdle()
            viewModel.onDescriptionChange("second draft")
            viewModel.save()
            advanceUntilIdle()

            val drafts = sauces.versions.values.filter { it.status == VersionStatus.DRAFT }
            assertThat(drafts).hasSize(1)
            assertThat(sauces.versions.values).hasSize(2)
        }

    @Test
    fun `editor persists ingredient name overrides and step order`() =
        runTest {
            ingredients.items += ingredient()
            val viewModel = editorViewModel(null)
            advanceUntilIdle()

            viewModel.onNameChange("覆盖测试酱")
            viewModel.onEnglishNameChange("Override Sauce")
            viewModel.onYieldAmountChange("200")
            viewModel.addIngredient(ingredients.items.single())
            val lineKey =
                viewModel.uiState.value.lines
                    .single()
                    .key
            viewModel.onLineNameChange(lineKey, "黄油")
            viewModel.onLineNameEnChange(lineKey, "Butter")
            viewModel.onLineAmountChange(lineKey, "25")

            viewModel.addStep()
            val firstStep =
                viewModel.uiState.value.steps
                    .single()
                    .key
            viewModel.onStepTitleChange(firstStep, "First")
            viewModel.onStepBodyChange(firstStep, "First body")
            viewModel.addStep()
            val secondStep =
                viewModel.uiState.value.steps
                    .last()
                    .key
            viewModel.onStepTitleChange(secondStep, "Second")
            viewModel.onStepBodyChange(secondStep, "Second body")
            viewModel.moveStep(1, 0)

            viewModel.save()
            advanceUntilIdle()

            val version = sauces.versions.values.maxByOrNull { it.versionNo }
            requireNotNull(version)
            val component = version.components.single()
            assertThat(component.labelOverride).isEqualTo("黄油")
            assertThat(component.labelOverrideEn).isEqualTo("Butter")
            val decoded = SauceStepCodec.decode(version.stepsJson, null) { "fallback" }
            assertThat(decoded.map { it.title }).containsExactly("Second", "First").inOrder()
        }

    private fun editorViewModel(sauceId: String?): SauceEditorViewModel =
        SauceEditorViewModel(
            savedStateHandle =
                SavedStateHandle(
                    mapOf(SaucesRoutes.ARG_SAUCE_ID to (sauceId ?: SaucesRoutes.NEW_ID)),
                ),
            getSauceDetail = detail,
            saveSauce = saveSauce,
            saveSauceDraft = saveDraft,
            publishSauceVersion = publish,
            archiveSauce = archiveSauce,
            listUnits = ListUnitsUseCase(units),
            listIngredients = ListIngredientsUseCase(ingredients),
            listSauces = ListSaucesUseCase(sauces),
            observeRelations = ObserveKitchenRelationsUseCase(relations),
            saveSauceRelations = saveRelations,
            sauceImages = imageUseCase,
        )

    private suspend fun seedPublishedSauce() {
        ingredients.items += ingredient()
        sauces.saved += sauce()
        val version =
            SauceVersion(
                id = SauceVersionId(VERSION_ID),
                sauceId = SauceId(SAUCE_ID),
                versionNo = 1,
                status = VersionStatus.PUBLISHED,
                yield = Quantity(500.0, gram()),
                components =
                    listOf(
                        SauceComponent(
                            lineNo = 1,
                            target =
                                com.culinary.core.domain.model.ComponentTarget.Ingredient(
                                    IngredientId(INGREDIENT_ID),
                                ),
                            quantity = Quantity(120.0, gram()),
                        ),
                    ),
                isCurrent = true,
            )
        sauces.versions[version.id] = version
        sauces.current[SAUCE_ID] = version.id
    }

    private fun sauce(): Sauce = Sauce(SauceId(SAUCE_ID), "基础酱", "基础酱", EntityStatus.ACTIVE)

    private fun ingredient(): Ingredient =
        Ingredient(
            id = IngredientId(INGREDIENT_ID),
            name = "低筋面粉",
            normalizedName = "低筋面粉",
            defaultUnitId = UnitId(UNIT_ID),
            status = EntityStatus.ACTIVE,
        )

    private fun gram(): UnitModel = UnitModel(UnitId(UNIT_ID), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 0)

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

        override suspend fun archive(id: SauceId): Outcome<Unit> {
            val existing =
                saved.firstOrNull { it.id == id }
                    ?: return Outcome.failure(CulinaryError.NotFound("sauce", id.value))
            saved.remove(existing)
            saved += existing.copy(status = EntityStatus.ARCHIVED)
            return Outcome.success(Unit)
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

    private class FakeUnitRepository : UnitRepository {
        private val items =
            listOf(
                UnitModel(UnitId(UNIT_ID), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 0),
            )

        override suspend fun all(): List<UnitModel> = items

        override suspend fun find(id: UnitId): UnitModel? = items.firstOrNull { it.id == id }

        override suspend fun conversionsFor(ingredientId: IngredientId): List<Nothing> = emptyList()
    }

    private object ImmediateTransactionRunner : TransactionRunner {
        override suspend fun <T> write(block: suspend () -> T): Outcome<T> = Outcome.success(block())
    }

    private companion object {
        const val SAUCE_ID = "sauce-1"
        const val VERSION_ID = "sauce-v1"
        const val INGREDIENT_ID = "ingredient-flour"
        const val UNIT_ID = "unit-g"
    }
}

private class FakeKitchenRelationRepository : KitchenRelationRepository {
    val relations = mutableListOf<KitchenRelatedRecord>()

    override fun observeForRecord(recordId: String): Flow<List<KitchenRelatedRecord>> =
        flowOf(relations.filter { it.sourceRecordId == recordId })

    override suspend fun replaceRelations(
        sourceRecordId: String,
        sourceKind: com.culinary.core.domain.model.KitchenRecordKind,
        targetKind: com.culinary.core.domain.model.KitchenRecordKind,
        relations: List<com.culinary.core.domain.model.KitchenRelationDraft>,
    ): Outcome<Unit> {
        this.relations.removeAll { it.sourceRecordId == sourceRecordId && it.targetKind == targetKind }
        this.relations +=
            relations.map {
                KitchenRelatedRecord(
                    sourceRecordId = sourceRecordId,
                    sourceKind = sourceKind,
                    targetRecordId = it.targetRecordId,
                    targetKind = it.targetKind,
                    targetLabel = it.targetLabel,
                    relationLabel = it.relationLabel,
                    sortOrder = it.sortOrder,
                )
            }
        return Outcome.success(Unit)
    }
}

private class FakeSauceImageRepository : SauceImageRepository {
    override suspend fun listForSauce(sauceId: String): List<SauceImage> = emptyList()

    override suspend fun listForVersion(versionId: String): List<SauceImage> = emptyList()

    override suspend fun add(
        owner: SauceImageOwner,
        ownerId: String,
        role: SauceImageRole,
        stepNo: Int?,
        bytes: ByteArray,
        mimeType: String,
    ): Outcome<SauceImage> =
        Outcome.success(
            SauceImage(
                id = "image-$ownerId-${role.name}-${stepNo ?: 0}",
                owner = owner,
                ownerId = ownerId,
                role = role,
                stepNo = stepNo,
                relativePath = "images/test",
                mimeType = mimeType,
                byteSize = bytes.size.toLong(),
            ),
        )

    override suspend fun delete(imageId: String): Outcome<Unit> = Outcome.success(Unit)

    override suspend fun read(imageId: String): ByteArray? = null

    override suspend fun updateStepNumbers(images: List<Pair<String, Int>>): Outcome<Unit> = Outcome.success(Unit)
}

private fun emptyRelations(): ObserveKitchenRelationsUseCase =
    ObserveKitchenRelationsUseCase(
        object : KitchenRelationRepository {
            override fun observeForRecord(recordId: String): Flow<List<KitchenRelatedRecord>> = flowOf(emptyList())

            override suspend fun replaceRelations(
                sourceRecordId: String,
                sourceKind: com.culinary.core.domain.model.KitchenRecordKind,
                targetKind: com.culinary.core.domain.model.KitchenRecordKind,
                relations: List<com.culinary.core.domain.model.KitchenRelationDraft>,
            ): Outcome<Unit> = Outcome.success(Unit)
        },
    )
