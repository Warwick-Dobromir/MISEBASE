package com.culinary.core.data.startup

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.inject.Provider

/** 启动协调：就绪、版本过高与损坏三条路径（R01 §9.2）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupCoordinatorTest {
    private val context = RuntimeEnvironment.getApplication()
    private var database: CulinaryDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `reports ready for a fresh database`() =
        runBlocking {
            val coordinator = coordinator()

            val state = coordinator.start()

            assertThat(state).isEqualTo(StartupState.Ready)
        }

    @Test
    fun `rejects a database created by a newer app`() =
        runBlocking {
            val file = context.getDatabasePath(DATABASE_NAME)
            file.parentFile?.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
                sqlite.version = 2
            }
            val coordinator = coordinator()

            val state = coordinator.start()

            assertThat(state).isInstanceOf(StartupState.Incompatible::class.java)
            val incompatible = state as StartupState.Incompatible
            assertThat(incompatible.databaseVersion).isEqualTo(2)
            assertThat(incompatible.appSchemaVersion).isEqualTo(1)
        }

    @Test
    fun `routes a corrupt database to recovery without deleting it`() =
        runBlocking {
            val file = context.getDatabasePath(DATABASE_NAME)
            file.parentFile?.mkdirs()
            file.writeBytes(ByteArray(256) { index -> (index * 7).toByte() })
            val coordinator = coordinator()

            val state = coordinator.start()

            assertThat(state).isInstanceOf(StartupState.Recovery::class.java)
            assertThat((state as StartupState.Recovery).reason).isEqualTo(RecoveryReason.DATABASE_CORRUPT)
            assertThat(file.exists()).isTrue()
        }

    private fun coordinator(): StartupCoordinator =
        StartupCoordinator(
            context = context,
            databaseProvider =
                Provider {
                    Room
                        .databaseBuilder(context, CulinaryDatabase::class.java, DATABASE_NAME)
                        .addCallback(CulinaryDatabaseCallback())
                        .allowMainThreadQueries()
                        .build()
                        .also { database = it }
                },
            expectedSchemaVersion = 1,
            databaseName = DATABASE_NAME,
            ioDispatcher = Dispatchers.Unconfined,
        )

    private companion object {
        const val DATABASE_NAME = "startup-test.db"
    }
}
