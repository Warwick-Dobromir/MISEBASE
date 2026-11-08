package com.culinary.core.data.backup

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.culinary.core.common.Outcome
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.CalculationRecordEntity
import com.culinary.core.database.entity.CalculationRecordLineEntity
import com.culinary.core.database.entity.IngredientEntity
import com.culinary.core.database.entity.RecipeEntity
import com.culinary.core.database.entity.RecipeVersionEntity
import com.culinary.core.database.model.CalculationRecordStatusDb
import com.culinary.core.database.model.RecipeCategoryDb
import com.culinary.core.database.model.VersionStatusDb
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Schema v4 备份恢复验证。
 *
 * 只调用既有 [BackupArchiveWriter]、[BackupArchiveVerifier] 与 [DatabaseRestorer]，
 * 不修改任何备份/恢复实现。验证 v4 备份 → 校验 → 恢复到 v4 → `servings` 与
 * 计算记录（含需求行）完整可读。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestoreV4Test {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private var database: CulinaryDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(SOURCE_DB)
        context.deleteDatabase(RESTORED_DB)
    }

    @Test
    fun `backs up v4 and restores servings and calculation records intact`() =
        runBlocking {
            val source = createV4Database()
            val archive = ByteArrayOutputStream()
            val manifest =
                BackupArchiveWriter().write(
                    output = archive,
                    databaseFile = source,
                    appVersionName = "0.1.0",
                    appVersionCode = 100,
                    schemaVersion = 4,
                    installId = "install-1",
                    device = BackupDevice("Google", "Pixel", 34),
                    counts = mapOf("calculation_record" to 1L, "recipe_version" to 1L),
                )

            assertThat(manifest.app.schemaVersion).isEqualTo(4)
            val verified = BackupArchiveVerifier().verify(ByteArrayInputStream(archive.toByteArray()))
            assertThat(verified).isInstanceOf(Outcome.Success::class.java)

            val target = context.getDatabasePath(RESTORED_DB)
            val restore =
                DatabaseRestorer(
                    databaseFile = target,
                    rollbackDirectory = temporaryFolder.newFolder("rollback"),
                ).restore(extractDatabase(archive.toByteArray()))
            assertThat(restore).isInstanceOf(Outcome.Success::class.java)

            val restored = openRestored()
            val sqlite = restored.openHelper.writableDatabase
            assertThat(userVersion(sqlite)).isEqualTo(5)
            assertThat(restored.recipeVersionDao().findById(VERSION_ID)?.servings).isEqualTo(3)

            val record = requireNotNull(restored.calculationRecordDao().findById(CALC_ID))
            assertThat(record.recipeVersionId).isEqualTo(VERSION_ID)
            assertThat(record.servings).isWithin(1e-9).of(3.0)
            assertThat(record.engineVersion).isEqualTo("stage2-1")
            assertThat(record.payloadHash).isEqualTo("c".repeat(64))
            assertThat(record.status).isEqualTo(CalculationRecordStatusDb.CONFIRMED)
            assertThat(restored.calculationRecordDao().findLines(CALC_ID)).hasSize(2)
            assertThat(
                restored
                    .calculationRecordDao()
                    .findLines(CALC_ID)
                    .first()
                    .quantity,
            ).isWithin(1e-9)
                .of(200.0)

            sqlite.query("PRAGMA foreign_key_check").use { cursor ->
                assertThat(cursor.count).isEqualTo(0)
            }
            sqlite.query("PRAGMA quick_check").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("ok")
            }
        }

    @Suppress("LongMethod") // 测试夹具需要在同一处构造完整 v2 数据，便于对照恢复结果
    private suspend fun createV4Database(): java.io.File {
        context.deleteDatabase(SOURCE_DB)
        val built =
            Room
                .databaseBuilder(context, CulinaryDatabase::class.java, SOURCE_DB)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        database = built
        built.openHelper.writableDatabase
        val unit = requireNotNull(built.unitDao().findByCode("g"))
        built.ingredientDao().insert(
            IngredientEntity(
                id = INGREDIENT_ID,
                name = "面粉",
                normalizedName = "面粉",
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        built.ingredientDao().insert(
            IngredientEntity(
                id = INGREDIENT_ID_2,
                name = "水",
                normalizedName = "水",
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        built.recipeDao().insert(
            RecipeEntity(
                id = RECIPE_ID,
                name = "备份菜谱",
                normalizedName = "备份菜谱",
                category = RecipeCategoryDb.DISH,
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        built.recipeVersionDao().insert(
            RecipeVersionEntity(
                id = VERSION_ID,
                recipeId = RECIPE_ID,
                versionNo = 1,
                status = VersionStatusDb.PUBLISHED,
                yieldQty = 1000.0,
                yieldUnitId = unit.id,
                isCurrent = true,
                servings = 3,
                createdAt = NOW,
                publishedAt = NOW,
            ),
        )
        built.calculationRecordDao().insertConfirmed(
            CalculationRecordEntity(
                id = CALC_ID,
                recipeVersionId = VERSION_ID,
                servings = 3.0,
                engineVersion = "stage2-1",
                payloadHash = "c".repeat(64),
                status = CalculationRecordStatusDb.CONFIRMED,
                createdAt = NOW,
                confirmedAt = NOW,
            ),
            listOf(
                CalculationRecordLineEntity(
                    id = "calc-line-1",
                    calculationRecordId = CALC_ID,
                    lineNo = 1,
                    ingredientId = INGREDIENT_ID,
                    quantity = 200.0,
                    unitId = unit.id,
                    isOptional = false,
                ),
                CalculationRecordLineEntity(
                    id = "calc-line-2",
                    calculationRecordId = CALC_ID,
                    lineNo = 2,
                    ingredientId = INGREDIENT_ID_2,
                    quantity = 50.0,
                    unitId = unit.id,
                    isOptional = true,
                ),
            ),
        )
        built.close()
        database = null
        return context.getDatabasePath(SOURCE_DB)
    }

    private fun openRestored(): CulinaryDatabase {
        val opened =
            Room
                .databaseBuilder(context, CulinaryDatabase::class.java, RESTORED_DB)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        database = opened
        opened.openHelper.writableDatabase
        return opened
    }

    private fun extractDatabase(archive: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == BackupLayout.DATABASE_FILE) {
                    return zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        error("database entry missing from archive")
    }

    private fun userVersion(sqlite: SupportSQLiteDatabase): Int =
        sqlite.query("PRAGMA user_version").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val SOURCE_DB = "schema-v2-source.db"
        const val RESTORED_DB = "schema-v2-restored.db"
        const val NOW = 1_760_000_000_000L
        const val INGREDIENT_ID = "ingredient-flour"
        const val INGREDIENT_ID_2 = "ingredient-water"
        const val RECIPE_ID = "recipe-backup"
        const val VERSION_ID = "recipe-backup-v1"
        const val CALC_ID = "calc-backup"
    }
}
