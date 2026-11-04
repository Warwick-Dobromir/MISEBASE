package com.culinary.core.data.backup

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import com.culinary.core.common.Clock
import com.culinary.core.common.DispatcherProvider
import com.culinary.core.common.IdGenerator
import com.culinary.core.data.logging.TimberAppLogger
import com.culinary.core.data.transaction.RoomTransactionRunner
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.IngredientEntity
import com.culinary.core.domain.platform.AutoBackupRequest
import com.culinary.core.domain.platform.AutoBackupRunResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
import java.time.Instant
import java.util.zip.ZipInputStream
import javax.inject.Provider

/** 并发写 + 备份：快照是单一逻辑时点，事务不会半包含。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupConcurrencyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var database: CulinaryDatabase
    private lateinit var barrier: WriteBarrier

    private val dispatchers =
        object : DispatcherProvider {
            override val main = Dispatchers.Default
            override val default = Dispatchers.Default
            override val io = Dispatchers.Default
        }
    private val logger = TimberAppLogger()

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `concurrent writes are captured as a consistent logical point`() =
        runBlocking {
            openWalDatabase()
            val transactionRunner = RoomTransactionRunner(database, dispatchers, barrier)
            val runner =
                AutoBackupRunnerImpl(
                    context = context,
                    snapshotter =
                        QuiescedDatabaseSnapshotter(
                            databaseProvider = Provider { database },
                            writeBarrier = barrier,
                            snapshotDirectory = File(temporaryFolder.root, "snapshots"),
                            dispatchers = dispatchers,
                            logger = logger,
                            barrierTimeoutMillis = 5_000,
                        ),
                    backupRecordDao = database.backupRecordDao(),
                    transactionRunner = transactionRunner,
                    dispatchers = dispatchers,
                    clock = Clock { Instant.parse("2026-11-04T10:00:00Z") },
                    idGenerator =
                        object : IdGenerator {
                            private var sequence = 0

                            override fun newId(): String = "concurrency-${sequence++}"
                        },
                    logger = logger,
                ).apply {
                    targetDirectoryOverride = File(temporaryFolder.root, "backups")
                    freeSpaceProvider = { Long.MAX_VALUE }
                }

            val backupResult =
                coroutineScope {
                    val writes =
                        (1..40).map { batch ->
                            launch(Dispatchers.Default) {
                                transactionRunner.write {
                                    insertBatch(batch)
                                }
                            }
                        }
                    val backup =
                        async(Dispatchers.Default) {
                            delay(10)
                            runner.run(AutoBackupRequest(backupId = "concurrent-backup"))
                        }
                    writes.forEach { it.join() }
                    backup.await()
                }

            assertThat(backupResult).isInstanceOf(AutoBackupRunResult.Success::class.java)
            val archive = File((backupResult as AutoBackupRunResult.Success).archivePath)
            val snapshotDatabase = extractToFile(archive)
            val batches = readBatches(snapshotDatabase)
            assertThat(batches.size % 2).isEqualTo(0)
            val grouped = batches.groupingBy { it.removeSuffix("-a").removeSuffix("-b") }.eachCount()
            assertThat(grouped.values.all { it == 2 }).isTrue()
            assertThat(database.ingredientDao().count()).isEqualTo(80)
            assertThat(database.backupRecordDao().count()).isEqualTo(1)
            assertThat(barrier.snapshot().barrierHeld).isFalse()
        }

    private suspend fun insertBatch(batch: Int) {
        database.ingredientDao().insert(
            IngredientEntity(
                id = "batch-$batch-a",
                name = "批次$batch-A",
                normalizedName = "批次$batch-A",
                createdAt = 1_760_000_000_000L,
                updatedAt = 1_760_000_000_000L,
            ),
        )
        database.ingredientDao().insert(
            IngredientEntity(
                id = "batch-$batch-b",
                name = "批次$batch-B",
                normalizedName = "批次$batch-B",
                createdAt = 1_760_000_000_000L,
                updatedAt = 1_760_000_000_000L,
            ),
        )
    }

    private fun openWalDatabase() {
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
    }

    private fun extractToFile(archive: File): File {
        val bytes =
            ZipInputStream(ByteArrayInputStream(archive.readBytes())).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == BackupLayout.DATABASE_FILE) return@use zip.readBytes()
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                null
            }
        val target = File(temporaryFolder.root, "snapshot-check.db")
        target.writeBytes(requireNotNull(bytes))
        return target
    }

    private fun readBatches(file: File): List<String> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            sqlite.rawQuery("SELECT id FROM ingredient ORDER BY id", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
        }

    private companion object {
        const val DB_NAME = "concurrency-source.db"
    }
}
