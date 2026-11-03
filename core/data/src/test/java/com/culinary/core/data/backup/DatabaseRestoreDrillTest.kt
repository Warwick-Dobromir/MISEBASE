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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 备份 → 校验 → 恢复演练（R01 阶段 9 退出条件）。
 *
 * 覆盖：真实 Room 库 → E05 归档 → 校验 → 取出 DB 字节 → [DatabaseRestorer]
 * 写到新位置 → 只读打开确认数据存在。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseRestoreDrillTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private var database: CulinaryDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(SOURCE_DB)
    }

    @Test
    fun `restores a backed up database with its data intact`() =
        runBlocking {
            val source = createDatabase()
            val archive = ByteArrayOutputStream()
            BackupArchiveWriter().write(
                output = archive,
                databaseFile = source,
                appVersionName = "0.1.0",
                appVersionCode = 100,
                schemaVersion = 1,
                installId = "install-1",
                device = BackupDevice("Google", "Pixel", 34),
                counts = mapOf("ingredient" to 1L),
            )

            val verified = BackupArchiveVerifier().verify(ByteArrayInputStream(archive.toByteArray()))
            assertThat(verified).isInstanceOf(Outcome.Success::class.java)

            val target = temporaryFolder.newFile("restored.db")
            val restorer =
                DatabaseRestorer(
                    databaseFile = target,
                    rollbackDirectory = temporaryFolder.newFolder("rollback"),
                )
            val result = restorer.restore(extractDatabase(archive.toByteArray()))

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            assertThat(countIngredients(target)).isEqualTo(1)
        }

    @Test
    fun `keeps the existing database when the payload is not SQLite`() {
        val target = temporaryFolder.newFile("existing.db").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val restorer =
            DatabaseRestorer(
                databaseFile = target,
                rollbackDirectory = temporaryFolder.newFolder("rollback"),
            )

        val result = restorer.restore(byteArrayOf(9, 9, 9, 9))

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat(target.readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
    }

    private fun createDatabase(): File {
        val built =
            Room
                .databaseBuilder(context, CulinaryDatabase::class.java, SOURCE_DB)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        database = built
        built.openHelper.writableDatabase
        runBlocking {
            built.ingredientDao().insert(
                IngredientEntity(
                    id = "ingredient-1",
                    name = "酱油",
                    normalizedName = "酱油",
                    createdAt = 1_760_000_000_000L,
                    updatedAt = 1_760_000_000_000L,
                ),
            )
        }
        built.close()
        database = null
        return context.getDatabasePath(SOURCE_DB)
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

    private fun countIngredients(file: File): Int =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            sqlite.rawQuery("SELECT COUNT(*) FROM ingredient", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }

    private companion object {
        const val SOURCE_DB = "drill-source.db"
    }
}
