package com.culinary.core.database.dao

import androidx.room.Room
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.IngredientEntity
import com.culinary.core.database.entity.RecipeComponentEntity
import com.culinary.core.database.entity.RecipeEntity
import com.culinary.core.database.entity.RecipeVersionEntity
import com.culinary.core.database.entity.SauceComponentEntity
import com.culinary.core.database.entity.SauceEntity
import com.culinary.core.database.entity.SauceVersionEntity
import com.culinary.core.database.model.ComponentTypeDb
import com.culinary.core.database.model.RecipeCategoryDb
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 酱汁/菜谱：版本唯一、当前版本唯一、组成行唯一、三选一目标、发布后不可改。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KnowledgeConstraintsTest {
    private lateinit var database: CulinaryDatabase
    private lateinit var sauceDao: SauceDao
    private lateinit var sauceVersions: SauceVersionDao
    private lateinit var sauceComponents: SauceComponentDao
    private lateinit var recipeDao: RecipeDao
    private lateinit var recipeVersions: RecipeVersionDao
    private lateinit var recipeComponents: RecipeComponentDao

    private val gramId = "00000000-0000-4000-8000-000000000002"

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    RuntimeEnvironment.getApplication(),
                    CulinaryDatabase::class.java,
                ).addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        sauceDao = database.sauceDao()
        sauceVersions = database.sauceVersionDao()
        sauceComponents = database.sauceComponentDao()
        recipeDao = database.recipeDao()
        recipeVersions = database.recipeVersionDao()
        recipeComponents = database.recipeComponentDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `rejects a duplicate version number for the same sauce`(): Unit =
        runBlocking {
            sauceDao.insert(sauce())
            sauceVersions.insert(version(id = VERSION_1, versionNo = 1))

            assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                runBlocking { sauceVersions.insert(version(id = VERSION_2, versionNo = 1)) }
            }
        }

    @Test
    fun `allows exactly one current version per sauce`(): Unit =
        runBlocking {
            sauceDao.insert(sauce())
            sauceVersions.insert(version(id = VERSION_1, versionNo = 1, isCurrent = true))

            assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                runBlocking { sauceVersions.insert(version(id = VERSION_2, versionNo = 2, isCurrent = true)) }
            }
        }

    @Test
    fun `rejects a duplicate component line in one version`(): Unit =
        runBlocking {
            sauceDao.insert(sauce())
            sauceVersions.insert(version(id = VERSION_1, versionNo = 1))
            insertIngredient()
            sauceComponents.insert(component(id = COMPONENT_1, lineNo = 1))

            assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                runBlocking { sauceComponents.insert(component(id = COMPONENT_2, lineNo = 1)) }
            }
        }

    @Test
    fun `rejects an ingredient component that also references a sauce`(): Unit =
        runBlocking {
            sauceDao.insert(sauce())
            sauceVersions.insert(version(id = VERSION_1, versionNo = 1))
            insertIngredient()

            assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                runBlocking {
                    sauceComponents.insert(
                        component(id = COMPONENT_1, lineNo = 1).copy(referencedSauceId = SAUCE_ID),
                    )
                }
            }
        }

    @Test
    fun `rejects a non positive component quantity`(): Unit =
        runBlocking {
            sauceDao.insert(sauce())
            sauceVersions.insert(version(id = VERSION_1, versionNo = 1))
            insertIngredient()

            assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                runBlocking { sauceComponents.insert(component(id = COMPONENT_1, lineNo = 1, quantity = 0.0)) }
            }
        }

    @Test
    fun `prevents editing the yield of a published version`(): Unit =
        runBlocking {
            sauceDao.insert(sauce())
            sauceVersions.insert(version(id = VERSION_1, versionNo = 1))
            sauceVersions.publish(VERSION_1, at = 1_760_000_000_200L)

            assertThrows(android.database.sqlite.SQLiteException::class.java) {
                runBlocking { sauceVersions.updateYield(VERSION_1, 2.0) }
            }
        }

    @Test
    fun `allows marking a published version as current`(): Unit =
        runBlocking {
            sauceDao.insert(sauce())
            sauceVersions.insert(version(id = VERSION_1, versionNo = 1))
            sauceVersions.publish(VERSION_1, at = 1_760_000_000_200L)

            sauceVersions.setCurrent(VERSION_1, true)

            assertThat(sauceVersions.findCurrent(SAUCE_ID)?.id).isEqualTo(VERSION_1)
        }

    @Test
    fun `cascades versions and components when a sauce is deleted`(): Unit =
        runBlocking {
            sauceDao.insert(sauce())
            sauceVersions.insert(version(id = VERSION_1, versionNo = 1))
            insertIngredient()
            sauceComponents.insert(component(id = COMPONENT_1, lineNo = 1))

            sauceDao.deleteById(SAUCE_ID)

            assertThat(sauceVersions.count()).isEqualTo(0)
            assertThat(sauceComponents.count()).isEqualTo(0)
        }

    @Test
    fun `rejects two active sauces with the same name`() {
        runBlocking { sauceDao.insert(sauce()) }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { sauceDao.insert(sauce(id = SECOND_SAUCE_ID)) }
        }
    }

    @Test
    fun `rejects a duplicate recipe version number`(): Unit =
        runBlocking {
            recipeDao.insert(recipe())
            recipeVersions.insert(recipeVersion(id = RECIPE_VERSION_1, versionNo = 1))

            assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                runBlocking { recipeVersions.insert(recipeVersion(id = RECIPE_VERSION_2, versionNo = 1)) }
            }
        }

    @Test
    fun `rejects a recipe component without a target`(): Unit =
        runBlocking {
            recipeDao.insert(recipe())
            recipeVersions.insert(recipeVersion(id = RECIPE_VERSION_1, versionNo = 1))

            assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                runBlocking {
                    recipeComponents.insert(
                        RecipeComponentEntity(
                            id = RECIPE_COMPONENT_1,
                            recipeVersionId = RECIPE_VERSION_1,
                            lineNo = 1,
                            componentType = ComponentTypeDb.INGREDIENT,
                            quantity = 1.0,
                            unitId = gramId,
                            createdAt = NOW,
                        ),
                    )
                }
            }
        }

    private suspend fun insertIngredient() {
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

    private fun sauce(id: String = SAUCE_ID) =
        SauceEntity(
            id = id,
            name = "基础酱",
            normalizedName = "基础酱",
            createdAt = NOW,
            updatedAt = NOW,
        )

    private fun version(
        id: String,
        versionNo: Int,
        isCurrent: Boolean = false,
    ) = SauceVersionEntity(
        id = id,
        sauceId = SAUCE_ID,
        versionNo = versionNo,
        yieldQty = 1.0,
        yieldUnitId = gramId,
        isCurrent = isCurrent,
        createdAt = NOW,
    )

    private fun component(
        id: String,
        lineNo: Int,
        quantity: Double = 10.0,
    ) = SauceComponentEntity(
        id = id,
        sauceVersionId = VERSION_1,
        lineNo = lineNo,
        componentType = ComponentTypeDb.INGREDIENT,
        ingredientId = INGREDIENT_ID,
        quantity = quantity,
        unitId = gramId,
        createdAt = NOW,
    )

    private fun recipe() =
        RecipeEntity(
            id = RECIPE_ID,
            name = "基础高汤",
            normalizedName = "基础高汤",
            category = RecipeCategoryDb.STOCK,
            createdAt = NOW,
            updatedAt = NOW,
        )

    private fun recipeVersion(
        id: String,
        versionNo: Int,
    ) = RecipeVersionEntity(
        id = id,
        recipeId = RECIPE_ID,
        versionNo = versionNo,
        yieldQty = 1.0,
        yieldUnitId = gramId,
        createdAt = NOW,
    )

    private companion object {
        const val NOW = 1_760_000_000_000L
        const val SAUCE_ID = "11111111-1111-4111-8111-111111111111"
        const val SECOND_SAUCE_ID = "12121212-1212-4212-8212-121212121212"
        const val VERSION_1 = "22222222-2222-4222-8222-222222222222"
        const val VERSION_2 = "23232323-2323-4232-8232-232323232323"
        const val COMPONENT_1 = "33333333-3333-4333-8333-333333333333"
        const val COMPONENT_2 = "34343434-3434-4343-8343-343434343434"
        const val INGREDIENT_ID = "44444444-4444-4444-8444-444444444444"
        const val RECIPE_ID = "55555555-5555-4555-8555-555555555555"
        const val RECIPE_VERSION_1 = "66666666-6666-4666-8666-666666666666"
        const val RECIPE_VERSION_2 = "67676767-6767-4767-8767-676767676767"
        const val RECIPE_COMPONENT_1 = "77777777-7777-4777-8777-777777777777"
    }
}
