package com.culinary.core.database.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.IngredientAliasEntity
import com.culinary.core.database.entity.IngredientEntity
import com.culinary.core.database.entity.IngredientUnitConversionEntity
import com.culinary.core.database.model.EntityStatusDb
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

/**
 * 食材/别名/换算的约束行为：部分唯一索引、外键 RESTRICT/CASCADE、CHECK 触发器。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IngredientConstraintsTest {
    private lateinit var database: CulinaryDatabase
    private lateinit var ingredients: IngredientDao
    private lateinit var aliases: IngredientAliasDao
    private lateinit var conversions: IngredientUnitConversionDao

    private val gramId = "00000000-0000-4000-8000-000000000002"
    private val cupId = "00000000-0000-4000-8000-000000000008"

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
        ingredients = database.ingredientDao()
        aliases = database.ingredientAliasDao()
        conversions = database.ingredientUnitConversionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `round trips an ingredient with its default unit`(): Unit =
        runBlocking {
            ingredients.insert(flour())

            val loaded = ingredients.findById(FLOUR_ID)

            assertThat(loaded).isNotNull()
            assertThat(loaded?.normalizedName).isEqualTo("面粉")
            assertThat(loaded?.defaultUnitId).isEqualTo(gramId)
            assertThat(loaded?.status).isEqualTo(EntityStatusDb.ACTIVE)
        }

    @Test
    fun `rejects two active ingredients with the same normalized name`() {
        runBlocking { ingredients.insert(flour()) }

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { ingredients.insert(flour(id = SECOND_ID)) }
        }
    }

    @Test
    fun `allows reusing a name after the previous one is soft deleted`(): Unit =
        runBlocking {
            ingredients.insert(flour())
            ingredients.softDelete(FLOUR_ID, at = 1_760_000_000_100L)

            ingredients.insert(flour(id = SECOND_ID))

            assertThat(ingredients.count()).isEqualTo(2)
            assertThat(ingredients.findActiveByNormalizedName("面粉")?.id).isEqualTo(SECOND_ID)
        }

    @Test
    fun `restricts deleting a unit still used as default unit`(): Unit =
        runBlocking {
            ingredients.insert(flour())

            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking { database.unitDao().deleteById(gramId) }
            }
        }

    @Test
    fun `cascades aliases when an ingredient is hard deleted`(): Unit =
        runBlocking {
            ingredients.insert(flour())
            aliases.insert(alias(id = ALIAS_ID, ingredientId = FLOUR_ID, normalized = "plain flour"))

            ingredients.deleteById(FLOUR_ID)

            assertThat(aliases.count()).isEqualTo(0)
        }

    @Test
    fun `rejects a duplicate alias across ingredients`(): Unit =
        runBlocking {
            ingredients.insert(flour())
            ingredients.insert(flour(id = SECOND_ID, name = "白砂糖", normalizedName = "白砂糖"))
            aliases.insert(alias(id = ALIAS_ID, ingredientId = FLOUR_ID, normalized = "plain flour"))

            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking {
                    aliases.insert(alias(id = SECOND_ALIAS_ID, ingredientId = SECOND_ID, normalized = "plain flour"))
                }
            }
        }

    @Test
    fun `rejects a conversion with a non positive factor`(): Unit =
        runBlocking {
            ingredients.insert(flour())

            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking {
                    conversions.insert(conversion(id = CONVERSION_ID, factor = 0.0))
                }
            }
        }

    @Test
    fun `rejects a conversion mapping a unit onto itself`(): Unit =
        runBlocking {
            ingredients.insert(flour())

            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking {
                    conversions.insert(conversion(id = CONVERSION_ID, fromUnit = gramId, toUnit = gramId))
                }
            }
        }

    @Test
    fun `rejects a duplicate conversion for the same unit pair`(): Unit =
        runBlocking {
            ingredients.insert(flour())
            conversions.insert(conversion(id = CONVERSION_ID, factor = 120.0))

            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking {
                    conversions.insert(conversion(id = SECOND_CONVERSION_ID, factor = 125.0))
                }
            }
        }

    @Test
    fun `cascades conversions when an ingredient is hard deleted`(): Unit =
        runBlocking {
            ingredients.insert(flour())
            conversions.insert(conversion(id = CONVERSION_ID, factor = 120.0))

            ingredients.deleteById(FLOUR_ID)

            assertThat(conversions.count()).isEqualTo(0)
        }

    @Test
    fun `rejects an out of range ingredient status`() {
        val sqlite = database.openHelper.writableDatabase

        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            sqlite.execSQL(
                "INSERT INTO ingredient (id, name, normalized_name, status, created_at, updated_at) " +
                    "VALUES ('bad-status', 'x', 'x', 'DELETED', 1, 1)",
            )
        }
    }

    private fun flour(
        id: String = FLOUR_ID,
        name: String = "面粉",
        normalizedName: String = name,
    ) = IngredientEntity(
        id = id,
        name = name,
        normalizedName = normalizedName,
        defaultUnitId = gramId,
        createdAt = 1_760_000_000_000L,
        updatedAt = 1_760_000_000_000L,
    )

    private fun alias(
        id: String,
        ingredientId: String,
        normalized: String,
    ) = IngredientAliasEntity(
        id = id,
        ingredientId = ingredientId,
        alias = normalized,
        normalizedAlias = normalized,
        createdAt = 1_760_000_000_000L,
    )

    private fun conversion(
        id: String,
        factor: Double = 120.0,
        fromUnit: String = cupId,
        toUnit: String = gramId,
    ) = IngredientUnitConversionEntity(
        id = id,
        ingredientId = FLOUR_ID,
        fromUnitId = fromUnit,
        toUnitId = toUnit,
        factor = factor,
        note = "实测",
        createdAt = 1_760_000_000_000L,
    )

    private companion object {
        const val FLOUR_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        const val SECOND_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        const val ALIAS_ID = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
        const val SECOND_ALIAS_ID = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
        const val CONVERSION_ID = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
        const val SECOND_CONVERSION_ID = "ffffffff-ffff-4fff-8fff-ffffffffffff"
    }
}
