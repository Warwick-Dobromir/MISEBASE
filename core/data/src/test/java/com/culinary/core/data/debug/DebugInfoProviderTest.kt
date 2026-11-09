package com.culinary.core.data.debug

import androidx.room.Room
import com.culinary.core.common.Outcome
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import javax.inject.Provider

/** 调试信息：只读、包含版本/路径/崩溃计数（R01 阶段 6）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DebugInfoProviderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
    fun `reports database version path and crash log count`() =
        runBlocking {
            val crashDirectory = temporaryFolder.newFolder("crash-logs")
            File(crashDirectory, "crash-1.txt").writeText("one")
            File(crashDirectory, "crash-2.txt").writeText("two")
            val provider = provider(crashDirectory)

            val result = provider.read()

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val items = (result as Outcome.Success).value.associate { it.label to it.value }
            assertThat(items["user_version"]).isEqualTo("8")
            assertThat(items["数据库路径"]).contains(DATABASE_NAME)
            assertThat(items["崩溃日志数"]).isEqualTo("2")
            assertThat(items["journal_mode"]).isNotEmpty()
        }

    private fun provider(crashDirectory: File): DebugInfoProvider =
        DebugInfoProvider(
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
            crashLogDirectory = crashDirectory,
            ioDispatcher = Dispatchers.Unconfined,
        )

    private companion object {
        const val DATABASE_NAME = "culinary.db"
    }
}
