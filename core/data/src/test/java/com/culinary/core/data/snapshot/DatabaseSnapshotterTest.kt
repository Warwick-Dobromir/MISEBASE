package com.culinary.core.data.snapshot

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import com.culinary.core.common.Outcome
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.IngredientEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * 在线快照：WAL checkpoint + 写屏障，证明快照一致且不关闭数据库（G3-1）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseSnapshotterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private val writeMutex = Mutex()
    private var database: CulinaryDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `snapshots every committed row while the database stays open`() =
        runBlocking {
            val database = openWalDatabase()
            repeat(3) { insert(database, "食材$it") }
            val snapshot = File(temporaryFolder.root, "snapshot.db")

            val result = snapshotter(database).snapshot(snapshot)

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            assertThat(database.isOpen).isTrue()
            assertThat(database.ingredientDao().count()).isEqualTo(3)
            assertThat(countIn(snapshot)).isEqualTo(3)
            assertThat(integrityOf(snapshot)).isEqualTo("ok")
        }

    @Test
    fun `never captures a half written transaction during concurrent writes`() =
        runBlocking {
            val database = openWalDatabase()
            val snapshot = File(temporaryFolder.root, "concurrent.db")
            val expectedNames = mutableListOf<String>()

            val writer =
                launch(Dispatchers.Default) {
                    repeat(30) { index ->
                        writeMutex.withLock {
                            val name = "并发$index"
                            insert(database, name)
                            expectedNames += name
                        }
                    }
                }
            val snap =
                async(Dispatchers.Default) {
                    snapshotter(database).snapshot(snapshot)
                }
            writer.join()
            val result = snap.await()

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val count = countIn(snapshot)
            assertThat(count).isIn(0..30)
            assertThat(integrityOf(snapshot)).isEqualTo("ok")
            assertThat(database.ingredientDao().count()).isEqualTo(30)
        }

    private fun snapshotter(database: CulinaryDatabase): DatabaseSnapshotter =
        DatabaseSnapshotter(
            database = database,
            writeMutex = writeMutex,
            databaseFile = context.getDatabasePath(DB_NAME),
            ioDispatcher = Dispatchers.IO,
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

    private fun insert(
        database: CulinaryDatabase,
        name: String,
    ) {
        runBlocking {
            database.ingredientDao().insert(
                IngredientEntity(
                    id = "id-${name.hashCode()}",
                    name = name,
                    normalizedName = name,
                    createdAt = 1_760_000_000_000L,
                    updatedAt = 1_760_000_000_000L,
                ),
            )
        }
    }

    private fun countIn(file: File): Int =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            sqlite.rawQuery("SELECT COUNT(*) FROM ingredient", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }

    private fun integrityOf(file: File): String =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            sqlite.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
        }

    private companion object {
        const val DB_NAME = "snapshot-test.db"
    }
}
