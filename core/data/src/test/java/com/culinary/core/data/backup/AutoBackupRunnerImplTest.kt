package com.culinary.core.data.backup

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DispatcherProvider
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.data.logging.TimberAppLogger
import com.culinary.core.data.transaction.RoomTransactionRunner
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.IngredientEntity
import com.culinary.core.database.model.BackupStatusDb
import com.culinary.core.domain.platform.AutoBackupRequest
import com.culinary.core.domain.platform.AutoBackupRunResult
import com.culinary.core.domain.transaction.TransactionRunner
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import java.io.File
import java.nio.file.AccessDeniedException
import java.time.Instant
import java.util.zip.ZipInputStream
import javax.inject.Provider

/** 自动备份编排：成功、校验失败、空间不足、权限拒绝、屏障超时、取消、记录失败。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoBackupRunnerImplTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var database: CulinaryDatabase
    private lateinit var barrier: WriteBarrier
    private lateinit var roomTransactionRunner: TransactionRunner

    private val dispatchers =
        object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
            override val io = Dispatchers.Unconfined
        }
    private val logger = TimberAppLogger()
    private val clock = Clock { Instant.parse("2026-11-04T10:00:00Z") }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `writes a verified archive and a backup record on success`() =
        runBlocking {
            openWalDatabase(ingredients = 3)
            val runner = runner()

            val result = runner.run(AutoBackupRequest(backupId = "backup-success"))

            assertThat(result).isInstanceOf(AutoBackupRunResult.Success::class.java)
            val success = result as AutoBackupRunResult.Success
            val archive = File(success.archivePath)
            assertThat(archive.exists()).isTrue()
            assertThat(archive.name).endsWith(".zip")
            assertThat(BackupArchiveVerifier().verify(archive.inputStream())).isInstanceOf(Outcome.Success::class.java)
            assertThat(countIngredientsInArchive(archive)).isEqualTo(3)
            assertThat(database.backupRecordDao().count()).isEqualTo(1)
            val record = database.backupRecordDao().listRecent(1).single()
            assertThat(record.status).isEqualTo(BackupStatusDb.VERIFIED)
            assertThat(barrier.snapshot().barrierHeld).isFalse()
        }

    @Test
    fun `rejects an archive that fails verification and writes no record`() =
        runBlocking {
            openWalDatabase(ingredients = 1)
            val runner =
                runner().apply {
                    stageHook = { stage, file ->
                        if (stage == BackupStage.AFTER_ARCHIVE_WRITE) file?.writeBytes(byteArrayOf(1, 2, 3))
                    }
                }

            val result = runner.run(AutoBackupRequest(backupId = "backup-broken"))

            assertThat(result).isInstanceOf(AutoBackupRunResult.Failure::class.java)
            assertThat((result as AutoBackupRunResult.Failure).reason).isEqualTo("archive_verification_failed")
            assertThat(database.backupRecordDao().count()).isEqualTo(0)
            assertThat(backupFiles()).isEmpty()
            assertThat(barrier.snapshot().barrierHeld).isFalse()
        }

    @Test
    fun `returns retry when the target has insufficient space`() =
        runBlocking {
            openWalDatabase(ingredients = 1)
            val runner = runner().apply { freeSpaceProvider = { 0L } }

            val result = runner.run(AutoBackupRequest(backupId = "backup-space"))

            assertThat(result).isInstanceOf(AutoBackupRunResult.Retry::class.java)
            assertThat((result as AutoBackupRunResult.Retry).reason).isEqualTo("insufficient_storage")
            assertThat(database.backupRecordDao().count()).isEqualTo(0)
            assertThat(backupFiles()).isEmpty()
        }

    @Test
    fun `returns failure on permission denied`() =
        runBlocking {
            openWalDatabase(ingredients = 1)
            val runner =
                runner().apply {
                    stageHook = { stage, _ ->
                        if (stage == BackupStage.AFTER_ARCHIVE_WRITE) {
                            throw AccessDeniedException("denied")
                        }
                    }
                }

            val result = runner.run(AutoBackupRequest(backupId = "backup-permission"))

            assertThat(result).isInstanceOf(AutoBackupRunResult.Failure::class.java)
            assertThat((result as AutoBackupRunResult.Failure).reason).isEqualTo("permission_denied")
            assertThat(database.backupRecordDao().count()).isEqualTo(0)
            assertThat(backupFiles()).isEmpty()
            assertThat(barrier.snapshot().barrierHeld).isFalse()
        }

    @Test
    fun `returns failure when the write barrier times out`() =
        runBlocking {
            openWalDatabase(ingredients = 1)
            val writerStarted = CompletableDeferred<Unit>()
            val releaseWriter = CompletableDeferred<Unit>()
            val writer =
                launch {
                    barrier.withWrite {
                        writerStarted.complete(Unit)
                        releaseWriter.await()
                    }
                }
            writerStarted.await()

            val result = runner().run(AutoBackupRequest(backupId = "backup-barrier"))

            assertThat(result).isInstanceOf(AutoBackupRunResult.Failure::class.java)
            assertThat((result as AutoBackupRunResult.Failure).reason).isEqualTo("write_barrier_timeout")
            assertThat(database.backupRecordDao().count()).isEqualTo(0)
            releaseWriter.complete(Unit)
            writer.join()
        }

    @Test
    fun `cleans temporary files and writes no record when cancelled`() =
        runBlocking {
            openWalDatabase(ingredients = 1)
            val runner =
                runner().apply {
                    stageHook = { stage, _ ->
                        if (stage == BackupStage.AFTER_ARCHIVE_WRITE) throw CancellationException("cancelled")
                    }
                }

            val thrown =
                runCatching {
                    runner.run(AutoBackupRequest(backupId = "backup-cancel"))
                }

            assertThat(thrown.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
            assertThat(database.backupRecordDao().count()).isEqualTo(0)
            assertThat(backupFiles()).isEmpty()
            assertThat(snapshotFiles()).isEmpty()
            assertThat(countIngredientsFromLiveDatabase()).isEqualTo(1)
        }

    @Test
    fun `deletes the archive when the backup record cannot be written`() =
        runBlocking {
            openWalDatabase(ingredients = 1)
            val failingRunner =
                object : TransactionRunner {
                    override suspend fun <T> write(block: suspend () -> T): Outcome<T> =
                        Outcome.failure(CulinaryError.Data(message = "record write failed"))
                }

            val result = runner(failingRunner).run(AutoBackupRequest(backupId = "backup-record-fail"))

            assertThat(result).isInstanceOf(AutoBackupRunResult.Retry::class.java)
            assertThat((result as AutoBackupRunResult.Retry).reason).isEqualTo("backup_record_write_failed")
            assertThat(database.backupRecordDao().count()).isEqualTo(0)
            assertThat(backupFiles()).isEmpty()
        }

    private fun runner(transactionRunner: TransactionRunner = roomTransactionRunner): AutoBackupRunnerImpl =
        AutoBackupRunnerImpl(
            context = context,
            snapshotter =
                QuiescedDatabaseSnapshotter(
                    databaseProvider = Provider { database },
                    writeBarrier = barrier,
                    snapshotDirectory = File(temporaryFolder.root, "snapshots"),
                    dispatchers = dispatchers,
                    logger = logger,
                    barrierTimeoutMillis = 100,
                ),
            backupRecordDao = database.backupRecordDao(),
            transactionRunner = transactionRunner,
            dispatchers = dispatchers,
            clock = clock,
            idGenerator =
                object : IdGenerator {
                    private var sequence = 0

                    override fun newId(): String = "generated-${sequence++}"
                },
            logger = logger,
        ).apply {
            targetDirectoryOverride = File(temporaryFolder.root, "backups")
            freeSpaceProvider = { Long.MAX_VALUE }
        }

    private fun openWalDatabase(ingredients: Int) {
        barrier = WriteBarrier()
        context.deleteDatabase(DB_NAME)
        database =
            Room
                .databaseBuilder(context, CulinaryDatabase::class.java, DB_NAME)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        database.openHelper.writableDatabase
        roomTransactionRunner = RoomTransactionRunner(database, dispatchers, barrier)
        runBlocking {
            repeat(ingredients) { index ->
                database.ingredientDao().insert(
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
    }

    private fun backupFiles(): List<File> =
        File(temporaryFolder.root, "backups").listFiles()?.filter { it.isFile }.orEmpty()

    private fun snapshotFiles(): List<File> =
        File(temporaryFolder.root, "snapshots").listFiles()?.filter { it.isFile }.orEmpty()

    private fun countIngredientsInArchive(archive: File): Int {
        val databaseBytes =
            ZipInputStream(ByteArrayInputStream(archive.readBytes())).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == BackupLayout.DATABASE_FILE) return@use zip.readBytes()
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                null
            }
        val restored = File(temporaryFolder.root, "archive-check.db")
        restored.writeBytes(requireNotNull(databaseBytes))
        return SQLiteDatabase.openDatabase(restored.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            sqlite.rawQuery("SELECT COUNT(*) FROM ingredient", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }
    }

    private fun countIngredientsFromLiveDatabase(): Int =
        database.ingredientDao().let { dao ->
            runBlocking { dao.count() }
        }

    private companion object {
        const val DB_NAME = "auto-backup-source.db"
    }
}
