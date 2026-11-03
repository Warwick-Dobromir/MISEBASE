package com.culinary.core.data.repository

import androidx.room.Room
import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IngredientRepositoryTest {
    private lateinit var database: CulinaryDatabase
    private lateinit var repository: IngredientRepositoryImpl

    private val clock = Clock { Instant.ofEpochMilli(NOW) }

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
        repository = IngredientRepositoryImpl(database.ingredientDao(), clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saves a new ingredient and reads it back`() =
        runBlocking {
            val saved = repository.save(ingredient())

            assertThat(saved).isInstanceOf(Outcome.Success::class.java)
            val loaded = repository.get(IngredientId(ID))
            assertThat((loaded as Outcome.Success).value.name).isEqualTo("黄油")
            assertThat(loaded.value.status).isEqualTo(EntityStatus.ACTIVE)
        }

    @Test
    fun `updates an existing ingredient without resetting createdAt`() =
        runBlocking {
            repository.save(ingredient())
            repository.save(ingredient(name = "发酵黄油"))

            val row = database.ingredientDao().findById(ID)
            assertThat(row?.name).isEqualTo("发酵黄油")
            assertThat(row?.createdAt).isEqualTo(NOW)
            assertThat(row?.revision).isEqualTo(2)
        }

    @Test
    fun `archives an ingredient and hides it from the active list`(): Unit =
        runBlocking {
            repository.save(ingredient())
            repository.save(ingredient(id = SECOND_ID, name = "面粉", normalizedName = "面粉"))

            val archived = repository.archive(IngredientId(ID))

            assertThat(archived).isInstanceOf(Outcome.Success::class.java)
            val active = repository.observeActive().first()
            assertThat(active.map { it.id.value }).containsExactly(SECOND_ID)
        }

    @Test
    fun `returns not found for a missing ingredient`() =
        runBlocking {
            val result = repository.get(IngredientId(MISSING_ID))

            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
            assertThat((result as Outcome.Failure).error.code).isEqualTo(ErrorCode.NF_001)
            assertThat(result.error).isInstanceOf(CulinaryError.NotFound::class.java)
        }

    @Test
    fun `returns not found when archiving a missing ingredient`() =
        runBlocking {
            val result = repository.archive(IngredientId(MISSING_ID))

            assertThat((result as Outcome.Failure).error.code).isEqualTo(ErrorCode.NF_001)
        }

    private fun ingredient(
        id: String = ID,
        name: String = "黄油",
        normalizedName: String = name,
    ) = Ingredient(
        id = IngredientId(id),
        name = name,
        normalizedName = normalizedName,
        defaultUnitId = null,
        status = EntityStatus.ACTIVE,
    )

    private companion object {
        const val NOW = 1_760_000_000_000L
        const val ID = "11111111-1111-4111-8111-111111111111"
        const val SECOND_ID = "22222222-2222-4222-8222-222222222222"
        const val MISSING_ID = "33333333-3333-4333-8333-333333333333"
    }
}
