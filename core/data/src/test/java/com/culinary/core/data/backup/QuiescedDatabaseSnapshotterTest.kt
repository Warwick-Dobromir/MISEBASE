package com.culinary.core.data.backup

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import com.culinary.core.common.AppLogger
import com.culinary.core.common.DispatcherProvider
import com.culinary.core.common.Outcome
import com.culinary.core.data.logging.TimberAppLogger
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.IngredientEntity
import com.google.common.truth.Truth.assertThat
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
import java.io.File
import javax.inject.Provider

/** checkpoint 快照：WAL 已提交事务、busy 失败、屏障释放（E05 §6.3 / MB-D-007 事项 2）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuiescedDatabaseSnapshotterTest {
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
    private val logger: AppLogger = TimberAppLogger()

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `captures a snapshot that includes committed WAL transactions`() =
        runBlocking {
            val barrier = WriteBarrier()
            val database = openWalDatabase()
            insertIngredients(3)
            val wal = File("${requireNotNull(database.openHelper.writableDatabase.path)}-wal")
            assertThat(journalMode()).isEqualTo("wal")
            assertThat(wal.exists()).isTrue()

            val result = snapshotter(barrier).capture("snapshot-1")

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val snapshot = (result as Outcome.Success).value
            assertThat(snapshot.schemaVersion).isEqualTo(2)
            assertThat(snapshot.journalMode).isEqualTo("wal")
            assertThat(snapshot.file.exists()).isTrue()
            assertThat(countIn(snapshot.file)).isEqualTo(3)
            assertThat(barrier.snapshot().barrierHeld).isFalse()
            snapshot.delete()
            Unit
        }

    @Test
    fun `fails when the write barrier times out`() =
        runBlocking {
            val barrier = WriteBarrier()
            openWalDatabase()
            insertIngredients(1)
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

            val result = snapshotter(barrier).capture("snapshot-2")

            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
            assertThat((result as Outcome.Failure).error.context["reason"]).isEqualTo("write_barrier_timeout")
            releaseWriter.complete(Unit)
            writer.join()
        }

    @Test
    fun `fails when wal checkpoint reports busy and releases the barrier`() =
        runBlocking {
            val barrier = WriteBarrier()
            val database = openWalDatabase()
            insertIngredients(2)
            val path = requireNotNull(database.openHelper.writableDatabase.path)
            val reader = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                reader.beginTransactionNonExclusive()
                reader.rawQuery("SELECT COUNT(*) FROM ingredient", null).use { it.moveToFirst() }

                val result = snapshotter(barrier).capture("snapshot-3")

                assertThat(result).isInstanceOf(Outcome.Failure::class.java)
                assertThat((result as Outcome.Failure).error.context["reason"]).isEqualTo("checkpoint_busy")
                assertThat(barrier.snapshot().barrierHeld).isFalse()
            } finally {
                if (reader.inTransaction()) reader.endTransaction()
                reader.close()
            }
        }

    @Test
    fun `a failed snapshot does not block later writes`() =
        runBlocking {
            val barrier = WriteBarrier()
            openWalDatabase()
            insertIngredients(1)
            assertThat(barrier.acquireForBackup(50)).isEqualTo(BarrierAcquireResult.ACQUIRED)

            snapshotter(barrier).capture("snapshot-4")
            barrier.releaseForBackup()

            val written = barrier.withWrite { "after-failure" }

            assertThat(written).isEqualTo("after-failure")
        }

    private fun snapshotter(barrier: WriteBarrier): QuiescedDatabaseSnapshotter =
        QuiescedDatabaseSnapshotter(
            databaseProvider = Provider { requireNotNull(database) },
            writeBarrier = barrier,
            snapshotDirectory = File(temporaryFolder.root, "snapshots"),
            dispatchers = dispatchers,
            logger = logger,
            barrierTimeoutMillis = 100,
        )

    private fun openWalDatabase(): CulinaryDatabase {
        context.deleteDatabase(DB_NAME)
        val built =
            Room
                .databaseBuilder(context, CulinaryDatabase::class.java, DB_NAME)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        built.openHelper.writableDatabase
        database = built
        return built
    }

    private fun insertIngredients(count: Int) {
        val target = requireNotNull(database)
        runBlocking {
            repeat(count) { index ->
                target.ingredientDao().insert(
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

    private fun journalMode(): String =
        requireNotNull(database)
            .openHelper.writableDatabase
            .query("PRAGMA journal_mode")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }

    private fun countIn(file: File): Int =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            sqlite.rawQuery("SELECT COUNT(*) FROM ingredient", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }

    private companion object {
        const val DB_NAME = "snapshot-source.db"
    }
}
