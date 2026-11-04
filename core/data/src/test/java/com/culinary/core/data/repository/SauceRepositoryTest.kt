package com.culinary.core.data.repository

import androidx.room.Room
import com.culinary.core.common.Clock
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.IngredientEntity
import com.culinary.core.database.entity.UnitEntity
import com.culinary.core.database.model.UnitDimensionDb
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceComponent
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.model.VersionStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * 酱汁 Repository 的 Room 契约测试（Stage 3）。
 *
 * 覆盖：档案/草稿持久化、组成行映射、版本号递增、发布切换当前版本、
 * 已发布内容不可修改（数据库触发器）。不修改 Schema/Entity。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SauceRepositoryTest {
    private lateinit var database: CulinaryDatabase
    private val clock = Clock { Instant.ofEpochMilli(NOW) }
    private var idCounter = 0
    private val ids = IdGenerator { "generated-${idCounter++}" }

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CulinaryDatabase::class.java)
                .addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `draft version round trips with components and optional flag`() =
        runBlocking {
            seed()
            val repository = repository()
            repository.save(sauce())

            val saved = repository.saveDraft(draftVersion(versionId = VERSION_ID, versionNo = 1))

            assertThat(saved).isInstanceOf(Outcome.Success::class.java)
            val loaded = (repository.getVersion(SauceVersionId(VERSION_ID)) as Outcome.Success).value
            assertThat(loaded.sauceId).isEqualTo(SauceId(SAUCE_ID))
            assertThat(loaded.versionNo).isEqualTo(1)
            assertThat(loaded.status).isEqualTo(VersionStatus.DRAFT)
            assertThat(loaded.yield.amount).isWithin(1e-9).of(500.0)
            assertThat(loaded.components).hasSize(2)
            val first = loaded.components.first()
            assertThat((first.target as ComponentTarget.Ingredient).id).isEqualTo(IngredientId(INGREDIENT_ID))
            assertThat(first.quantity.amount).isWithin(1e-9).of(120.0)
            assertThat(first.isOptional).isFalse()
            assertThat(loaded.components[1].isOptional).isTrue()
        }

    @Test
    fun `version numbers increase and are never reused`() =
        runBlocking {
            seed()
            val repository = repository()
            repository.save(sauce())

            val first = repository.nextVersionNo(SauceId(SAUCE_ID))
            repository.saveDraft(draftVersion(versionId = VERSION_ID, versionNo = first))
            val second = repository.nextVersionNo(SauceId(SAUCE_ID))
            repository.saveDraft(draftVersion(versionId = VERSION_ID_2, versionNo = second))

            assertThat(first).isEqualTo(1)
            assertThat(second).isEqualTo(2)
            assertThat(repository.listVersions(SauceId(SAUCE_ID)).map { it.versionNo })
                .containsExactly(2, 1)
                .inOrder()
        }

    @Test
    fun `publishing a draft makes it current and keeps it immutable`() =
        runBlocking {
            seed()
            val repository = repository()
            repository.save(sauce())
            repository.saveDraft(draftVersion(versionId = VERSION_ID, versionNo = 1))
            val draft = (repository.getVersion(SauceVersionId(VERSION_ID)) as Outcome.Success).value

            val published = repository.savePublished(draft.copy(status = VersionStatus.PUBLISHED))

            assertThat(published).isInstanceOf(Outcome.Success::class.java)
            val current = repository.findCurrentVersion(SauceId(SAUCE_ID))
            assertThat(current?.id).isEqualTo(SauceVersionId(VERSION_ID))
            assertThat(current?.isPublished).isTrue()
            assertThat(database.sauceVersionDao().count()).isEqualTo(1)

            val thrown =
                runCatching {
                    database.sauceVersionDao().updateYield(VERSION_ID, 999.0)
                }.exceptionOrNull()
            assertThat(thrown).isNotNull()
            assertThat(thrown!!.message).contains("immutable")
            assertThat(database.sauceVersionDao().findById(VERSION_ID)?.yieldQty).isWithin(1e-9).of(500.0)
        }

    @Test
    fun `publishing a second version moves the current pointer without touching the old content`() =
        runBlocking {
            seed()
            val repository = repository()
            repository.save(sauce())
            repository.saveDraft(draftVersion(versionId = VERSION_ID, versionNo = 1))
            repository.saveDraft(draftVersion(versionId = VERSION_ID_2, versionNo = 2))
            val first = (repository.getVersion(SauceVersionId(VERSION_ID)) as Outcome.Success).value
            val second = (repository.getVersion(SauceVersionId(VERSION_ID_2)) as Outcome.Success).value

            repository.savePublished(first.copy(status = VersionStatus.PUBLISHED))
            repository.savePublished(second.copy(status = VersionStatus.PUBLISHED))

            assertThat(database.sauceVersionDao().findCurrent(SAUCE_ID)?.id).isEqualTo(VERSION_ID_2)
            assertThat(database.sauceVersionDao().findById(VERSION_ID)?.isCurrent).isFalse()
            assertThat(
                database
                    .sauceVersionDao()
                    .findById(VERSION_ID)
                    ?.status
                    ?.name,
            ).isEqualTo("PUBLISHED")
            assertThat(database.sauceVersionDao().findById(VERSION_ID)?.yieldQty).isWithin(1e-9).of(500.0)
        }

    @Test
    fun `missing sauce and missing version return typed not found errors`() =
        runBlocking {
            val repository = repository()

            val sauce = repository.get(SauceId("missing"))
            val version = repository.getVersion(SauceVersionId("missing"))

            assertThat((sauce as Outcome.Failure).error).isInstanceOf(
                com.culinary.core.common.CulinaryError.NotFound::class.java,
            )
            assertThat((version as Outcome.Failure).error).isInstanceOf(
                com.culinary.core.common.CulinaryError.NotFound::class.java,
            )
        }

    private fun repository(): SauceRepositoryImpl =
        SauceRepositoryImpl(
            sauceDao = database.sauceDao(),
            versionDao = database.sauceVersionDao(),
            componentDao = database.sauceComponentDao(),
            unitDao = database.unitDao(),
            clock = clock,
            idGenerator = ids,
        )

    private suspend fun seed() {
        database.unitDao().insert(
            UnitEntity(
                id = UNIT_ID,
                code = "tg",
                name = "测试克",
                symbol = "tg",
                dimension = UnitDimensionDb.MASS,
                baseFactor = 1.0,
                isBase = false,
                isApproximate = false,
                displayPrecision = 1,
                isActive = true,
                sortOrder = 0,
                createdAt = NOW,
            ),
        )
        database.ingredientDao().insert(
            IngredientEntity(
                id = INGREDIENT_ID,
                name = "黄油",
                normalizedName = "黄油",
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
    }

    private fun sauce(): Sauce =
        Sauce(
            id = SauceId(SAUCE_ID),
            name = "基础酱",
            normalizedName = "基础酱",
            status = EntityStatus.ACTIVE,
            family = "乳化酱",
        )

    private fun draftVersion(
        versionId: String,
        versionNo: Int,
    ): SauceVersion =
        SauceVersion(
            id = SauceVersionId(versionId),
            sauceId = SauceId(SAUCE_ID),
            versionNo = versionNo,
            status = VersionStatus.DRAFT,
            yield = Quantity(500.0, gram()),
            components =
                listOf(
                    SauceComponent(
                        lineNo = 1,
                        target = ComponentTarget.Ingredient(IngredientId(INGREDIENT_ID)),
                        quantity = Quantity(120.0, gram()),
                    ),
                    SauceComponent(
                        lineNo = 2,
                        target = ComponentTarget.Ingredient(IngredientId(INGREDIENT_ID)),
                        quantity = Quantity(10.0, gram()),
                        isOptional = true,
                    ),
                ),
        )

    private fun gram(): Unit = Unit(UnitId(UNIT_ID), "tg", "测试克", "tg", UnitDimension.MASS, 1.0, false, false, 1)

    private companion object {
        const val NOW = 1_760_000_000_000L
        const val UNIT_ID = "unit-test-g"
        const val INGREDIENT_ID = "ingredient-butter"
        const val SAUCE_ID = "sauce-1"
        const val VERSION_ID = "sauce-v1"
        const val VERSION_ID_2 = "sauce-v2"
    }
}
