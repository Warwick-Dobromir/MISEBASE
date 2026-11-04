package com.culinary.core.data.repository

import android.database.sqlite.SQLiteException
import androidx.room.Room
import com.culinary.core.common.DefaultDispatcherProvider
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.data.backup.WriteBarrier
import com.culinary.core.data.transaction.RoomTransactionRunner
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.IngredientEntity
import com.culinary.core.database.entity.RecipeEntity
import com.culinary.core.database.entity.RecipeVersionEntity
import com.culinary.core.database.model.RecipeCategoryDb
import com.culinary.core.database.model.VersionStatusDb
import com.culinary.core.domain.model.CalculationRecord
import com.culinary.core.domain.model.CalculationRecordId
import com.culinary.core.domain.model.CalculationRecordStatus
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.RequirementLine
import com.culinary.core.domain.model.Servings
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.snapshot.CalculationRecordFactory
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
 * 计算记录 Room Repository 契约测试（MB-SC-001）。
 *
 * 与 fake 保持同一语义：只接受 `CONFIRMED`、同 ID 不覆盖、同 `payload_hash`
 * 返回既有记录；同时验证持久化后的不可变触发器。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalculationRecordRepositoryTest {
    private lateinit var database: CulinaryDatabase
    private lateinit var repository: CalculationRecordRepositoryImpl
    private val clock = Instant.ofEpochMilli(NOW)
    private var idCounter = 0

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CulinaryDatabase::class.java)
                .addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        repository =
            CalculationRecordRepositoryImpl(
                dao = database.calculationRecordDao(),
                unitDao = database.unitDao(),
                idGenerator = IdGenerator { "generated-${idCounter++}" },
            )
        runBlocking { seedKnowledge() }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saves and reads a confirmed record with lines servings and hash`() =
        runBlocking {
            val confirmed = confirmedRecord()

            val saved = repository.saveConfirmed(confirmed)
            val loaded = repository.find(CalculationRecordId(CALC_ID))

            assertThat(saved).isInstanceOf(Outcome.Success::class.java)
            assertThat((loaded as Outcome.Success).value).isNotNull()
            val record = requireNotNull(loaded.value)
            assertThat(record.id).isEqualTo(CalculationRecordId(CALC_ID))
            assertThat(record.recipeVersionId).isEqualTo(RecipeVersionId(VERSION_ID))
            assertThat(record.servings).isEqualTo(Servings(2.5))
            assertThat(record.engineVersion).isEqualTo("stage4-1")
            assertThat(record.payloadHash).isEqualTo(confirmed.payloadHash)
            assertThat(record.status).isEqualTo(CalculationRecordStatus.CONFIRMED)
            assertThat(record.lines).hasSize(2)
            assertThat(
                record.lines
                    .first()
                    .quantity.amount,
            ).isWithin(1e-9).of(200.0)
            assertThat(
                record.lines
                    .first()
                    .quantity.unit.code,
            ).isEqualTo("g")
            assertThat(repository.findByRecipeVersion(RecipeVersionId(VERSION_ID)))
                .isInstanceOf(Outcome.Success::class.java)
            assertThat((repository.findByPayloadHash(record.payloadHash) as Outcome.Success).value?.id)
                .isEqualTo(CalculationRecordId(CALC_ID))
        }

    @Test
    fun `rejects preview and duplicate id and returns the existing record for duplicate hash`() =
        runBlocking {
            val preview = previewRecord()
            val confirmed = (preview.confirm(clock) as Outcome.Success).value

            val previewRejected = repository.saveConfirmed(preview)
            val first = repository.saveConfirmed(confirmed)
            val duplicateHash =
                repository.saveConfirmed(confirmed.copy(id = CalculationRecordId("calc-duplicate")))
            val duplicateId = repository.saveConfirmed(confirmed.copy(payloadHash = "b".repeat(64)))

            assertThat((previewRejected as Outcome.Failure).error.code).isEqualTo(
                com.culinary.core.common.ErrorCode.INV_003,
            )
            assertThat(first).isInstanceOf(Outcome.Success::class.java)
            assertThat((duplicateHash as Outcome.Success).value.id).isEqualTo(CalculationRecordId(CALC_ID))
            assertThat((duplicateId as Outcome.Failure).error.code).isEqualTo(
                com.culinary.core.common.ErrorCode.CON_001,
            )
            assertThat(database.calculationRecordDao().count()).isEqualTo(1)
            assertThat(database.calculationRecordDao().countLines()).isEqualTo(2)
        }

    @Test
    fun `confirmed snapshot can be written inside an outer transaction runner`() =
        runBlocking {
            val runner =
                RoomTransactionRunner(
                    database = database,
                    dispatchers = DefaultDispatcherProvider(),
                    writeBarrier = WriteBarrier(),
                )

            val result = runner.write { repository.saveConfirmed(confirmedRecord()) }

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val nested = (result as Outcome.Success).value
            assertThat(nested).isInstanceOf(Outcome.Success::class.java)
            assertThat(database.calculationRecordDao().count()).isEqualTo(1)
            assertThat(database.calculationRecordDao().countLines()).isEqualTo(2)
        }

    @Test
    fun `confirmed record and lines are immutable at the database level`() =
        runBlocking {
            repository.saveConfirmed(confirmedRecord())
            val sqlite = database.openHelper.writableDatabase

            assertThrowsSql {
                sqlite.execSQL("UPDATE calculation_record SET engine_version = 'changed' WHERE id = '$CALC_ID'")
            }
            assertThrowsSql {
                sqlite.execSQL(
                    "UPDATE calculation_record_line SET quantity = 1 " +
                        "WHERE calculation_record_id = '$CALC_ID'",
                )
            }
            assertThrowsSql {
                sqlite.execSQL("DELETE FROM calculation_record WHERE id = '$CALC_ID'")
            }
        }

    @Test
    fun `payload hash is stable for the same inputs and independent of record id`() {
        val factory = CalculationRecordFactory()
        val first = previewRecord(factory, CalculationRecordId("calc-a"))
        val second = previewRecord(factory, CalculationRecordId("calc-b"))

        assertThat(first.payloadHash).isEqualTo(second.payloadHash)
        assertThat(first.payloadHash).matches("^[0-9a-f]{64}$")
    }

    private suspend fun seedKnowledge() {
        val unit = requireNotNull(database.unitDao().findByCode("g"))
        database.ingredientDao().insert(ingredientEntity(INGREDIENT_ID, "面粉"))
        database.ingredientDao().insert(ingredientEntity(INGREDIENT_ID_2, "水"))
        database.recipeDao().insert(
            RecipeEntity(
                id = RECIPE_ID,
                name = "测试菜谱",
                normalizedName = "测试菜谱",
                category = RecipeCategoryDb.DISH,
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        database.recipeVersionDao().insert(
            RecipeVersionEntity(
                id = VERSION_ID,
                recipeId = RECIPE_ID,
                versionNo = 1,
                status = VersionStatusDb.PUBLISHED,
                yieldQty = 1000.0,
                yieldUnitId = unit.id,
                isCurrent = true,
                servings = 2,
                createdAt = NOW,
                publishedAt = NOW,
            ),
        )
    }

    private fun confirmedRecord(): CalculationRecord = (previewRecord().confirm(clock) as Outcome.Success).value

    private fun previewRecord(
        factory: CalculationRecordFactory = CalculationRecordFactory(),
        id: CalculationRecordId = CalculationRecordId(CALC_ID),
    ): CalculationRecord =
        factory.preview(
            id = id,
            recipeVersionId = RecipeVersionId(VERSION_ID),
            servings = Servings(2.5),
            lines =
                listOf(
                    RequirementLine(IngredientId(INGREDIENT_ID), quantity(200.0), isOptional = false),
                    RequirementLine(IngredientId(INGREDIENT_ID_2), quantity(50.0), isOptional = true),
                ),
            createdAt = clock,
        )

    private fun quantity(amount: Double): Quantity = Quantity(amount, gramUnit())

    private fun gramUnit(): Unit =
        Unit(
            id =
                com.culinary.core.domain.model
                    .UnitId(UNIT_ID),
            code = "g",
            name = "克",
            symbol = "g",
            dimension = com.culinary.core.domain.model.UnitDimension.MASS,
            baseFactor = 1.0,
            isBase = true,
            isApproximate = false,
            displayPrecision = 1,
        )

    private fun ingredientEntity(
        id: String,
        name: String,
    ): IngredientEntity =
        IngredientEntity(
            id = id,
            name = name,
            normalizedName = name,
            createdAt = NOW,
            updatedAt = NOW,
        )

    private fun assertThrowsSql(block: () -> kotlin.Unit) {
        try {
            block()
            error("expected SQLiteException")
        } catch (expected: SQLiteException) {
            assertThat(expected.message).isNotEmpty()
        }
    }

    private companion object {
        const val NOW = 1_760_000_000_000L
        const val UNIT_ID = "00000000-0000-4000-8000-000000000002"
        const val INGREDIENT_ID = "ingredient-flour"
        const val INGREDIENT_ID_2 = "ingredient-water"
        const val RECIPE_ID = "recipe-1"
        const val VERSION_ID = "recipe-v1"
        const val CALC_ID = "calc-1"
    }
}
