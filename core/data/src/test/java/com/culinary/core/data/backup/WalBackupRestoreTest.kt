package com.culinary.core.data.backup

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.culinary.core.common.DispatcherProvider
import com.culinary.core.common.Outcome
import com.culinary.core.data.logging.TimberAppLogger
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.IngredientEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
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
import javax.inject.Provider

/**
 * WAL 恢复验证（MB-D-007 事项 6）：
 * 活跃 WAL 中未 checkpoint 的已提交事务 → 备份 → 校验 → 恢复 → 读取。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WalBackupRestoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private var database: CulinaryDatabase? = null

    private val dispatchers =
        object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
            override val io = Dispatchers.Unconfined
        }
    private val logger = TimberAppLogger()

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(SOURCE_DB)
    }

    @Test
    fun `backs up a committed WAL transaction and restores it intact`() =
        runBlocking {
            val source = openWalDatabase()
            insertIngredient("ingredient-1", "酱油")
            insertIngredient("ingredient-2", "最后一条已提交事务")
            val sourcePath = requireNotNull(source.openHelper.writableDatabase.path)
            val sourceWal = File("$sourcePath-wal")
            assertThat(sourceWal.length()).isGreaterThan(0L)

            val snapshot =
                QuiescedDatabaseSnapshotter(
                    databaseProvider = Provider { requireNotNull(database) },
                    writeBarrier = WriteBarrier(),
                    snapshotDirectory = File(temporaryFolder.root, "snapshots"),
                    dispatchers = dispatchers,
                    logger = logger,
                    barrierTimeoutMillis = 500,
                ).capture("wal-1")
            assertThat(snapshot).isInstanceOf(Outcome.Success::class.java)
            val snapshotFile = (snapshot as Outcome.Success).value.file
            assertThat(sourceWal.length()).isEqualTo(0L)

            val archive = archiveOf(snapshotFile)
            val verified = BackupArchiveVerifier().verify(ByteArrayInputStream(archive))
            assertThat(verified).isInstanceOf(Outcome.Success::class.java)

            val target = File(temporaryFolder.root, "restored.db")
            val restored =
                DatabaseRestorer(
                    databaseFile = target,
                    rollbackDirectory = File(temporaryFolder.root, "rollback"),
                ).restore(extractDatabase(archive))
            assertThat(restored).isInstanceOf(Outcome.Success::class.java)
            assertThat(File("${target.path}-wal").exists()).isFalse()
            assertThat(File("${target.path}-shm").exists()).isFalse()

            SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
                val names =
                    sqlite.rawQuery("SELECT name FROM ingredient ORDER BY name", null).use { cursor ->
                        buildList {
                            while (cursor.moveToNext()) add(cursor.getString(0))
                        }
                    }
                assertThat(names).contains("最后一条已提交事务")
                assertThat(quickCheck(sqlite)).isEqualTo("ok")
                assertThat(foreignKeyViolations(sqlite)).isEqualTo(0)
            }
            snapshotFile.delete()
            Unit
        }

    @Test
    fun `restored WAL snapshot keeps the last committed batch after a checkpoint`() =
        runBlocking {
            val source = openWalDatabase()
            runBlocking {
                source.withTransaction {
                    insertIngredient("batch-1-a", "批次1-A")
                    insertIngredient("batch-1-b", "批次1-B")
                }
            }
            assertThat(File("${requireNotNull(source.openHelper.writableDatabase.path)}-wal").length())
                .isGreaterThan(0L)

            val snapshot =
                QuiescedDatabaseSnapshotter(
                    databaseProvider = Provider { requireNotNull(database) },
                    writeBarrier = WriteBarrier(),
                    snapshotDirectory = File(temporaryFolder.root, "snapshots"),
                    dispatchers = dispatchers,
                    logger = logger,
                    barrierTimeoutMillis = 500,
                ).capture("wal-2")
            assertThat(snapshot).isInstanceOf(Outcome.Success::class.java)

            val databaseBytes = (snapshot as Outcome.Success).value.file.readBytes()
            val target = File(temporaryFolder.root, "restored-batch.db")
            val restored =
                DatabaseRestorer(
                    databaseFile = target,
                    rollbackDirectory = File(temporaryFolder.root, "rollback"),
                ).restore(databaseBytes)
            assertThat(restored).isInstanceOf(Outcome.Success::class.java)

            SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
                val count =
                    sqlite.rawQuery("SELECT COUNT(*) FROM ingredient WHERE name LIKE '批次1-%'", null).use { cursor ->
                        cursor.moveToFirst()
                        cursor.getInt(0)
                    }
                assertThat(count).isEqualTo(2)
                assertThat(quickCheck(sqlite)).isEqualTo("ok")
            }
        }

    private fun openWalDatabase(): CulinaryDatabase {
        context.deleteDatabase(SOURCE_DB)
        val built =
            Room
                .databaseBuilder(context, CulinaryDatabase::class.java, SOURCE_DB)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        built.openHelper.writableDatabase
        database = built
        return built
    }

    private suspend fun insertIngredient(
        id: String,
        name: String,
    ) {
        requireNotNull(database)
            .ingredientDao()
            .insert(
                IngredientEntity(
                    id = id,
                    name = name,
                    normalizedName = name,
                    createdAt = 1_760_000_000_000L,
                    updatedAt = 1_760_000_000_000L,
                ),
            )
    }

    private fun archiveOf(databaseFile: File): ByteArray {
        val output = ByteArrayOutputStream()
        BackupArchiveWriter().write(
            output = output,
            databaseFile = databaseFile,
            appVersionName = "0.1.0",
            appVersionCode = 100,
            schemaVersion = 2,
            installId = "install-1",
            device = BackupDevice("Google", "Pixel", 34),
        )
        return output.toByteArray()
    }

    private fun extractDatabase(archive: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == BackupLayout.DATABASE_FILE) return zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        error("database entry missing")
    }

    private fun quickCheck(database: SQLiteDatabase): String? =
        database.rawQuery("PRAGMA quick_check", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun foreignKeyViolations(database: SQLiteDatabase): Int =
        database.rawQuery("PRAGMA foreign_key_check", null).use { cursor -> cursor.count }

    private companion object {
        const val SOURCE_DB = "wal-source.db"
    }
}
