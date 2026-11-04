package com.culinary.core.data.repository

import androidx.room.Room
import com.culinary.core.common.Clock
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.UnitEntity
import com.culinary.core.database.model.UnitDimensionDb
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeCategory
import com.culinary.core.domain.model.RecipeComponent
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
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
 * 菜谱 Repository 的 Stage 3 契约测试。
 *
 * 覆盖：版本列表/当前指针、servings、子菜谱固定版本映射、发布切换与不可变。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecipeRepositoryTest {
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
    fun `versions are listed newest first and servings round trips`() =
        runBlocking {
            seed()
            val repository = repository()
            repository.save(recipe(PARENT_ID, "主菜"))
            repository.saveDraft(version(PARENT_ID, VERSION_ID, versionNo = 1, servings = 2))
            repository.saveDraft(version(PARENT_ID, VERSION_ID_2, versionNo = 2, servings = 4))

            val versions = repository.listVersions(RecipeId(PARENT_ID))

            assertThat(versions.map { it.versionNo }).containsExactly(2, 1).inOrder()
            assertThat(versions.first().servings).isEqualTo(4)
            assertThat(repository.nextVersionNo(RecipeId(PARENT_ID))).isEqualTo(3)
            assertThat(repository.getCurrentVersion(RecipeId(PARENT_ID))).isNull()
        }

    @Test
    fun `publishing a stored draft keeps one row and makes it current`() =
        runBlocking {
            seed()
            val repository = repository()
            repository.save(recipe(PARENT_ID, "主菜"))
            repository.saveDraft(version(PARENT_ID, VERSION_ID, versionNo = 1, servings = 2))
            val draft = repository.getVersion(RecipeVersionId(VERSION_ID))!!

            val result = repository.savePublished(draft.copy(status = VersionStatus.PUBLISHED))

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            assertThat(database.recipeVersionDao().count()).isEqualTo(1)
            assertThat(repository.getCurrentVersion(RecipeId(PARENT_ID))?.id).isEqualTo(RecipeVersionId(VERSION_ID))
            val thrown =
                runCatching { database.recipeVersionDao().updateYield(VERSION_ID, 999.0) }.exceptionOrNull()
            assertThat(thrown).isNotNull()
            assertThat(database.recipeVersionDao().findById(VERSION_ID)?.yieldQty).isWithin(1e-9).of(1000.0)
        }

    @Test
    fun `sub recipe component keeps the pinned version id`() =
        runBlocking {
            seed()
            val repository = repository()
            repository.save(recipe(CHILD_ID, "高汤"))
            repository.save(recipe(PARENT_ID, "主菜"))
            repository.saveDraft(version(CHILD_ID, CHILD_VERSION_ID, versionNo = 1, servings = 1))
            repository.saveDraft(
                version(
                    recipeId = PARENT_ID,
                    versionId = VERSION_ID,
                    versionNo = 1,
                    servings = 2,
                    components =
                        listOf(
                            RecipeComponent(
                                lineNo = 1,
                                target = ComponentTarget.Recipe(RecipeId(CHILD_ID), RecipeVersionId(CHILD_VERSION_ID)),
                                quantity = Quantity(200.0, gram()),
                            ),
                        ),
                ),
            )

            val loaded = repository.getVersion(RecipeVersionId(VERSION_ID))!!
            val target = loaded.components.single().target as ComponentTarget.Recipe

            assertThat(target.recipeId).isEqualTo(RecipeId(CHILD_ID))
            assertThat(target.versionId).isEqualTo(RecipeVersionId(CHILD_VERSION_ID))
        }

    private fun repository(): RecipeRepositoryImpl =
        RecipeRepositoryImpl(
            recipeDao = database.recipeDao(),
            versionDao = database.recipeVersionDao(),
            componentDao = database.recipeComponentDao(),
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
    }

    private fun recipe(
        id: String,
        name: String,
    ): Recipe =
        Recipe(
            id = RecipeId(id),
            name = name,
            normalizedName = name,
            category = RecipeCategory.DISH,
            status = EntityStatus.ACTIVE,
        )

    private fun version(
        recipeId: String,
        versionId: String,
        versionNo: Int,
        servings: Int,
        components: List<RecipeComponent> = emptyList(),
    ): RecipeVersion =
        RecipeVersion(
            id = RecipeVersionId(versionId),
            recipeId = RecipeId(recipeId),
            versionNo = versionNo,
            status = VersionStatus.DRAFT,
            yield = Quantity(1000.0, gram()),
            components = components,
            servings = servings,
        )

    private fun gram(): Unit =
        Unit(UnitId(UNIT_ID), "tg", "测试克", "tg", UnitDimension.MASS, 1.0, false, false, 1)

    private companion object {
        const val NOW = 1_760_000_000_000L
        const val UNIT_ID = "unit-test-g"
        const val PARENT_ID = "recipe-parent"
        const val CHILD_ID = "recipe-child"
        const val VERSION_ID = "recipe-parent-v1"
        const val VERSION_ID_2 = "recipe-parent-v2"
        const val CHILD_VERSION_ID = "recipe-child-v1"
    }
}
