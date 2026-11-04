package com.culinary.core.data.backup

import androidx.room.Room
import androidx.room.RoomDatabase
import com.culinary.core.common.Outcome
import com.culinary.core.common.SystemClock
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
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Provider

/**
 * 完整恢复流程（E05 §13）：关闭连接、WAL/SHM 处理、装库、校验、失败回滚、
 * 进程中断后的 journal 恢复。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestoreExecutorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private var live: CulinaryDatabase? = null

    @After
    fun tearDown() {
        live?.close()
        live = null
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `restores the database closes the connection and clears sidecars`() =
        runBlocking<Unit> {
            val originalFile = createDatabase(withIngredient = "恢复前")
            val archiveBytes = archiveOf(originalFile)
            val liveDb = openLiveDatabase(withIngredient = "恢复后")

            val executor = executor()
            val result = executor.restoreDatabase(extractDatabase(archiveBytes), "restore-1")

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            assertThat(liveDb.isOpen).isFalse()
            assertThat(File("${originalFile.path}-wal").exists()).isFalse()
            assertThat(File("${originalFile.path}-shm").exists()).isFalse()
            assertThat(journalPhase()).isEqualTo(RestorePhase.DONE)
            assertThat(reopenAndReadNames()).containsExactly("恢复前")
        }

    @Test
    fun `rolls back to the previous database when the restore fails`() =
        runBlocking<Unit> {
            val originalFile = createDatabase(withIngredient = "原始")
            val archiveBytes = archiveOf(originalFile)
            openLiveDatabase(withIngredient = "当前")
            val executor = executor(failingAfterInstall = true)

            val result = executor.restoreDatabase(extractDatabase(archiveBytes), "restore-2")

            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
            assertThat(journalPhase()).isEqualTo(RestorePhase.ROLLED_BACK)
            // 回滚保留的是"恢复前"的库，其中已包含原始与当前两条记录。
            assertThat(reopenAndReadNames()).containsExactly("原始", "当前")
        }

    @Test
    fun `recovers an interrupted restore from the journal and rollback copy`() =
        runBlocking<Unit> {
            val originalFile = createDatabase(withIngredient = "中断前")
            val rollback = File(temporaryFolder.newFolder("rollback"), "restore-3.db")
            originalFile.copyTo(rollback, overwrite = true)
            originalFile.writeBytes(byteArrayOf(1, 2, 3, 4))
            RestoreJournalStore(journalFile()).write(
                RestoreJournal(
                    restoreId = "restore-3",
                    phase = RestorePhase.DB_INSTALLED,
                    stagingDir = originalFile.parent,
                    rollbackDir = rollback.parent,
                    startedAt = "2026-11-04T00:00:00Z",
                    updatedAt = "2026-11-04T00:00:01Z",
                ),
            )

            val result = executor().recoverIfInterrupted()

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            assertThat((result as Outcome.Success).value)
                .isEqualTo(RecoveryOutcome.RolledBack("restore-3"))
            assertThat(RestoreJournalStore(journalFile()).read())
                .isInstanceOf(Outcome.Failure::class.java)
            assertThat(reopenAndReadNames()).containsExactly("中断前")
        }

    @Test
    fun `enters RECOVERY_REQUIRED when the rollback copy cannot be restored`() =
        runBlocking<Unit> {
            val originalFile = createDatabase(withIngredient = "原始")
            val archiveBytes = archiveOf(originalFile)
            openLiveDatabase(withIngredient = "当前")
            val executor =
                executor(
                    afterInstall = {
                        File(temporaryFolder.root, "rollback/restore-recovery.db").delete()
                        error("rollback copy destroyed")
                    },
                )

            val result = executor.restoreDatabase(extractDatabase(archiveBytes), "restore-recovery")

            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
            assertThat(journalPhase()).isEqualTo(RestorePhase.RECOVERY_REQUIRED)
            assertThat(File(temporaryFolder.root, "rollback/restore-recovery.db").exists()).isFalse()
            assertThat(context.getDatabasePath(DB_NAME).exists()).isTrue()
        }

    @Test
    fun `refuses to restore while another writer holds the write barrier`() =
        runBlocking<Unit> {
            val originalFile = createDatabase(withIngredient = "原始")
            val archiveBytes = archiveOf(originalFile)
            openLiveDatabase(withIngredient = "当前")
            val barrier = WriteBarrier()
            assertThat(barrier.acquireForBackup(50)).isEqualTo(BarrierAcquireResult.ACQUIRED)
            try {
                val result =
                    executor(writeBarrier = barrier)
                        .restoreDatabase(extractDatabase(archiveBytes), "restore-barrier")

                assertThat(result).isInstanceOf(Outcome.Failure::class.java)
                assertThat(journalPhase()).isNull()
                assertThat(reopenAndReadNames()).containsExactly("原始", "当前")
            } finally {
                barrier.releaseForBackup()
            }
        }

    private fun executor(
        failingAfterInstall: Boolean = false,
        writeBarrier: WriteBarrier = WriteBarrier(),
        afterInstall: (() -> Unit)? = null,
    ): RestoreExecutor =
        RestoreExecutor(
            databaseProvider = Provider { requireNotNull(live) },
            databaseFile = context.getDatabasePath(DB_NAME),
            journalStore = RestoreJournalStore(journalFile()),
            rollbackDirectory = File(temporaryFolder.root, "rollback"),
            clock = SystemClock,
            ioDispatcher = Dispatchers.Unconfined,
            writeBarrier = writeBarrier,
            barrierTimeoutMillis = 100,
            afterInstallHook =
                afterInstall ?: if (failingAfterInstall) {
                    { error("injected failure") }
                } else {
                    null
                },
        )

    private fun journalFile(): File = File(temporaryFolder.root, "restore.journal")

    private fun journalPhase(): RestorePhase? =
        (RestoreJournalStore(journalFile()).read() as? Outcome.Success)?.value?.phase

    private fun createDatabase(withIngredient: String): File {
        context.deleteDatabase(DB_NAME)
        val database =
            Room
                .databaseBuilder(context, CulinaryDatabase::class.java, DB_NAME)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        database.openHelper.writableDatabase
        runBlocking { insert(database, withIngredient) }
        database.close()
        val file = context.getDatabasePath(DB_NAME)
        File("${file.path}-wal").writeText("stale")
        File("${file.path}-shm").writeText("stale")
        return file
    }

    private fun openLiveDatabase(withIngredient: String): CulinaryDatabase {
        val database =
            Room
                .databaseBuilder(context, CulinaryDatabase::class.java, DB_NAME)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        database.openHelper.writableDatabase
        runBlocking { insert(database, withIngredient) }
        live = database
        return database
    }

    private fun insert(
        database: CulinaryDatabase,
        name: String,
    ) {
        runBlocking {
            database.ingredientDao().insert(
                IngredientEntity(
                    id = "ingredient-${name.hashCode()}",
                    name = name,
                    normalizedName = name,
                    createdAt = 1_760_000_000_000L,
                    updatedAt = 1_760_000_000_000L,
                ),
            )
        }
    }

    private fun reopenAndReadNames(): List<String> {
        context.deleteDatabase("reopen-$DB_NAME")
        context.getDatabasePath(DB_NAME).copyTo(context.getDatabasePath("reopen-$DB_NAME"), overwrite = true)
        val database =
            Room
                .databaseBuilder(context, CulinaryDatabase::class.java, "reopen-$DB_NAME")
                .allowMainThreadQueries()
                .build()
        return try {
            database.openHelper.writableDatabase
            runBlocking {
                database
                    .ingredientDao()
                    .allActive()
                    .map { it.name }
            }
        } finally {
            database.close()
            context.deleteDatabase("reopen-$DB_NAME")
        }
    }

    private fun archiveOf(databaseFile: File): ByteArray {
        val output = ByteArrayOutputStream()
        BackupArchiveWriter().write(
            output = output,
            databaseFile = databaseFile,
            appVersionName = "0.1.0",
            appVersionCode = 100,
            schemaVersion = 1,
            installId = "install-1",
            device = BackupDevice("Google", "Pixel", 34),
            counts = mapOf("ingredient" to 1L),
        )
        return output.toByteArray()
    }

    private fun extractDatabase(archive: ByteArray): ByteArray {
        java.util.zip.ZipInputStream(archive.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == BackupLayout.DATABASE_FILE) return zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        error("database missing")
    }

    private companion object {
        const val DB_NAME = "restore-executor.db"
    }
}
