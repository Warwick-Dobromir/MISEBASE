package com.culinary.core.data.kitchen

import androidx.room.Room
import com.culinary.core.common.AppLogger
import com.culinary.core.common.Clock
import com.culinary.core.common.DefaultDispatcherProvider
import com.culinary.core.common.Outcome
import com.culinary.core.common.UuidV4Generator
import com.culinary.core.data.backup.WriteBarrier
import com.culinary.core.data.repository.IngredientRepositoryImpl
import com.culinary.core.data.repository.KitchenReferenceRepositoryImpl
import com.culinary.core.data.repository.RecipeRepositoryImpl
import com.culinary.core.data.repository.SauceRepositoryImpl
import com.culinary.core.data.repository.UnitRepositoryImpl
import com.culinary.core.data.transaction.RoomTransactionRunner
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KitchenSeedImporterTest {
    private val context = RuntimeEnvironment.getApplication()
    private val database =
        Room
            .inMemoryDatabaseBuilder(context, CulinaryDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(CulinaryDatabaseCallback())
            .build()

    private val clock = Clock { Instant.ofEpochMilli(NOW) }
    private val idGenerator = UuidV4Generator()
    private val transactionRunner = RoomTransactionRunner(database, DefaultDispatcherProvider(), WriteBarrier())
    private val importer =
        KitchenSeedImporter(
            seedReader = KitchenSeedReader { File("src/main/assets/kitchen_seed_v1.json").readText() },
            ingredientRepository = IngredientRepositoryImpl(database.ingredientDao(), clock),
            sauceRepository =
                SauceRepositoryImpl(
                    database.sauceDao(),
                    database.sauceVersionDao(),
                    database.sauceComponentDao(),
                    database.unitDao(),
                    clock,
                    idGenerator,
                ),
            recipeRepository =
                RecipeRepositoryImpl(
                    database.recipeDao(),
                    database.recipeVersionDao(),
                    database.recipeComponentDao(),
                    database.unitDao(),
                    clock,
                    idGenerator,
                ),
            referenceRepository =
                KitchenReferenceRepositoryImpl(
                    database.kitchenReferenceDao(),
                    database.kitchenReferenceContentDao(),
                    clock,
                    idGenerator,
                ),
            unitRepository = UnitRepositoryImpl(database.unitDao(), database.ingredientUnitConversionDao()),
            transactionRunner = transactionRunner,
            metadataDao = database.appMetadataDao(),
            relationDao = database.kitchenRelationDao(),
            sauceDao = database.sauceDao(),
            ingredientDao = database.ingredientDao(),
            recipeDao = database.recipeDao(),
            referenceDao = database.kitchenReferenceDao(),
            clock = clock,
            logger = NoopLogger,
        )

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `imports v1 once and does not duplicate on second start`() =
        runBlocking {
            val first = importer.ensureImported()
            assertThat(first).isInstanceOf(Outcome.Success::class.java)
            assertThat((first as Outcome.Success).value.imported).isTrue()
            assertThat(first.value.counts)
                .isEqualTo(
                    KitchenSeedCounts(
                        ingredients = 18,
                        sauces = 10,
                        recipes = 10,
                        references = 30,
                        relations = 260,
                    ),
                )

            val second = importer.ensureImported()
            assertThat((second as Outcome.Success).value.imported).isFalse()
            assertThat(database.ingredientDao().countSeed("v1")).isEqualTo(18)
            assertThat(database.sauceDao().countSeed("v1")).isEqualTo(10)
            assertThat(database.recipeDao().countSeed("v1")).isEqualTo(10)
            assertThat(database.kitchenReferenceDao().countSeed("v1")).isEqualTo(30)
            assertThat(database.kitchenRelationDao().countSeed()).isEqualTo(260)
        }

    @Test
    fun `clear disables automatic refill and reimport restores seed`() =
        runBlocking {
            importer.ensureImported()
            val cleared = importer.clear()

            assertThat(cleared).isInstanceOf(Outcome.Success::class.java)
            assertThat(importer.status().disabled).isTrue()
            assertThat(importer.status().counts.ingredients).isEqualTo(0)
            assertThat(database.ingredientDao().countSeed("v1")).isEqualTo(0)
            assertThat(database.kitchenRelationDao().countSeed()).isEqualTo(0)

            val afterClear = importer.ensureImported()
            assertThat((afterClear as Outcome.Success).value.imported).isFalse()
            assertThat(database.ingredientDao().countSeed("v1")).isEqualTo(0)

            val reimported = importer.reimport()
            assertThat((reimported as Outcome.Success).value.imported).isTrue()
            assertThat(importer.status().counts.ingredients).isEqualTo(18)
            assertThat(database.kitchenRelationDao().countSeed()).isEqualTo(260)
        }

    private object NoopLogger : AppLogger {
        override fun debug(
            event: String,
            fields: Map<String, Any?>,
        ) = Unit

        override fun info(
            event: String,
            fields: Map<String, Any?>,
        ) = Unit

        override fun warn(
            event: String,
            error: Throwable?,
            fields: Map<String, Any?>,
        ) = Unit

        override fun error(
            event: String,
            error: Throwable?,
            fields: Map<String, Any?>,
        ) = Unit
    }

    private companion object {
        const val NOW = 1_760_000_000_000L
    }
}
