package com.culinary.core.data.transaction

import androidx.room.Room
import com.culinary.core.common.DispatcherProvider
import com.culinary.core.common.Outcome
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.IngredientEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 事务协调器：提交、回滚、20 协程并发写与错误映射（E02 §4 / §15）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomTransactionRunnerTest {
    private lateinit var database: CulinaryDatabase
    private lateinit var runner: RoomTransactionRunner

    private val dispatchers =
        object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
            override val io = Dispatchers.IO
        }

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    RuntimeEnvironment.getApplication(),
                    CulinaryDatabase::class.java,
                ).addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        runner = RoomTransactionRunner(database, dispatchers, Mutex())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `commits a successful write`() =
        runBlocking {
            val result =
                runner.write {
                    database.ingredientDao().insert(ingredient("id-1", "黄油"))
                }

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            assertThat(database.ingredientDao().count()).isEqualTo(1)
        }

    @Test
    fun `rolls back when the block throws`() =
        runBlocking {
            val result =
                runner.write {
                    database.ingredientDao().insert(ingredient("id-1", "黄油"))
                    error("boom")
                }

            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
            assertThat(database.ingredientDao().count()).isEqualTo(0)
        }

    @Test
    fun `serializes twenty concurrent writes`() =
        runBlocking {
            val results =
                coroutineScope {
                    (1..20)
                        .map { index ->
                            async(Dispatchers.Default) {
                                runner.write {
                                    database.ingredientDao().insert(ingredient("id-$index", "食材$index"))
                                }
                            }
                        }.awaitAll()
                }

            assertThat(results.filterIsInstance<Outcome.Failure>()).isEmpty()
            assertThat(database.ingredientDao().count()).isEqualTo(20)
        }

    @Test
    fun `maps a unique constraint violation to a conflict error code`() =
        runBlocking {
            runner.write { database.ingredientDao().insert(ingredient("id-1", "黄油")) }

            val duplicate =
                runner.write {
                    database.ingredientDao().insert(ingredient("id-2", "黄油"))
                }

            val failure = duplicate as Outcome.Failure
            assertThat(failure.error.code.value).isEqualTo("CUL-CON-001")
            assertThat(failure.error.retryable).isFalse()
        }

    private fun ingredient(
        id: String,
        name: String,
    ) = IngredientEntity(
        id = id,
        name = name,
        normalizedName = name,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private companion object {
        const val NOW = 1_760_000_000_000L
    }
}
