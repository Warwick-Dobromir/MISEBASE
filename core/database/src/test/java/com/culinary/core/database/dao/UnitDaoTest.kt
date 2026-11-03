package com.culinary.core.database.dao

import androidx.room.Room
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.UnitEntity
import com.culinary.core.database.model.UnitDimensionDb
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnitDaoTest {
    private lateinit var database: CulinaryDatabase
    private lateinit var dao: UnitDao

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
        dao = database.unitDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `reads seeded units ordered by sort order`() =
        runBlocking {
            val units = dao.allActive()

            assertThat(units).hasSize(15)
            assertThat(units.first().code).isEqualTo("mg")
            assertThat(units[1].code).isEqualTo("g")
        }

    @Test
    fun `round trips a unit through the enum converter`() =
        runBlocking {
            dao.insert(
                UnitEntity(
                    id = "11111111-1111-4111-8111-111111111111",
                    code = "test_volume",
                    name = "测试体积",
                    symbol = "tv",
                    dimension = UnitDimensionDb.VOLUME,
                    baseFactor = 2.5,
                    isBase = false,
                    isApproximate = true,
                    displayPrecision = 2,
                    isActive = true,
                    sortOrder = 90,
                    createdAt = 1_760_000_000_001L,
                ),
            )

            val loaded = dao.findByCode("test_volume")

            assertThat(loaded).isNotNull()
            assertThat(loaded?.dimension).isEqualTo(UnitDimensionDb.VOLUME)
            assertThat(loaded?.baseFactor).isEqualTo(2.5)
            assertThat(loaded?.isApproximate).isTrue()
        }

    @Test
    fun `rejects a duplicate unit code`() {
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                dao.insert(
                    UnitEntity(
                        id = "22222222-2222-4222-8222-222222222222",
                        code = "g",
                        name = "重复克",
                        symbol = "g",
                        dimension = UnitDimensionDb.MASS,
                        baseFactor = 1.0,
                        isBase = false,
                        isApproximate = false,
                        displayPrecision = 1,
                        isActive = true,
                        sortOrder = 91,
                        createdAt = 1_760_000_000_002L,
                    ),
                )
            }
        }
    }

    @Test
    fun `finds a unit by id`() =
        runBlocking {
            val gram = dao.findByCode("g")

            assertThat(gram).isNotNull()
            assertThat(dao.findById(gram!!.id)?.code).isEqualTo("g")
        }
}
