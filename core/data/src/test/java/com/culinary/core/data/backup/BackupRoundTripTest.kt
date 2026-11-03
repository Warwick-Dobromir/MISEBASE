package com.culinary.core.data.backup

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import com.culinary.core.common.Outcome
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.IngredientEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 真实 Room 数据库的备份往返：生成归档 → 解包 → 校验哈希/manifest →
 * 只读打开备份 DB 确认数据存在。
 *
 * **不包含恢复执行器**（文件替换/回滚）——那部分尚未实现，本测试不声称覆盖。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRoundTripTest {
    private val context = RuntimeEnvironment.getApplication()
    private var database: CulinaryDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(EMPTY_DB)
        context.deleteDatabase(DATA_DB)
    }

    @Test
    fun `backs up and verifies an empty database archive`() {
        val file = createDatabase(EMPTY_DB, ingredients = 0)
        val output = ByteArrayOutputStream()

        val manifest =
            BackupArchiveWriter().write(
                output = output,
                databaseFile = file,
                appVersionName = "0.1.0",
                appVersionCode = 100,
                schemaVersion = 1,
                installId = "install-1",
                device = BackupDevice("Google", "Pixel", 34),
                counts = mapOf("ingredient" to 0L),
            )
        val verified = BackupArchiveVerifier().verify(ByteArrayInputStream(output.toByteArray()))

        assertThat(verified).isInstanceOf(Outcome.Success::class.java)
        assertThat((verified as Outcome.Success).value.database).hasSize(1)
        assertThat(manifest.counts["ingredient"]).isEqualTo(0L)

        val extracted = extractDatabase(output.toByteArray())
        assertThat(countIngredients(extracted)).isEqualTo(0)
    }

    @Test
    fun `backs up and verifies an archive with ingredient data`() {
        val file = createDatabase(DATA_DB, ingredients = 2)
        val output = ByteArrayOutputStream()

        val manifest =
            BackupArchiveWriter().write(
                output = output,
                databaseFile = file,
                appVersionName = "0.1.0",
                appVersionCode = 100,
                schemaVersion = 1,
                installId = "install-1",
                device = BackupDevice("Google", "Pixel", 34),
                counts = mapOf("ingredient" to 2L),
            )
        val verified = BackupArchiveVerifier().verify(ByteArrayInputStream(output.toByteArray()))

        assertThat(verified).isInstanceOf(Outcome.Success::class.java)
        assertThat(manifest.counts["ingredient"]).isEqualTo(2L)
        assertThat(manifest.totals.fileCount).isEqualTo(1)

        val extracted = extractDatabase(output.toByteArray())
        assertThat(countIngredients(extracted)).isEqualTo(2)
    }

    private fun createDatabase(
        name: String,
        ingredients: Int,
    ): File {
        context.deleteDatabase(name)
        val built =
            Room
                .databaseBuilder(context, CulinaryDatabase::class.java, name)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        database = built
        // Room 懒打开：先触碰连接，确保空库也会建表并写入种子。
        built.openHelper.writableDatabase
        runBlocking {
            repeat(ingredients) { index ->
                built.ingredientDao().insert(
                    IngredientEntity(
                        id = "ingredient-$index",
                        name = "食材$index",
                        normalizedName = "食材$index",
                        createdAt = 1_760_000_000_000L,
                        updatedAt = 1_760_000_000_000L,
                    ),
                )
            }
        }
        built.close()
        database = null
        return context.getDatabasePath(name)
    }

    private fun extractDatabase(archive: ByteArray): File {
        var bytes: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == BackupLayout.DATABASE_FILE) {
                    bytes = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val file = File(context.cacheDir, "restored-check.db")
        file.writeBytes(requireNotNull(bytes) { "database entry missing" })
        return file
    }

    private fun countIngredients(file: File): Int =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            sqlite.rawQuery("SELECT COUNT(*) FROM ingredient", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }

    private companion object {
        const val EMPTY_DB = "backup-empty.db"
        const val DATA_DB = "backup-data.db"
    }
}
