package com.culinary.core.domain.snapshot

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.CalculationRecord
import com.culinary.core.domain.model.CalculationRecordId
import com.culinary.core.domain.model.CalculationRecordStatus
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.RequirementLine
import com.culinary.core.domain.model.Servings
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.repository.CalculationRecordRepository
import com.culinary.core.domain.transaction.TransactionRunner
import com.culinary.core.domain.usecase.calculation.ConfirmCalculationRecordUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant

/** 计算记录快照（MB-D-017）：payload_hash 稳定、确认后不可变、只接受已确认记录。 */
class CalculationRecordTest {
    private val factory = CalculationRecordFactory()

    @Test
    fun `payload hash is stable for identical inputs regardless of time and id`() {
        val first = factory.preview(ID_1, VERSION_ID, Servings(2.0), LINES, createdAt = NOW)
        val second = factory.preview(ID_2, VERSION_ID, Servings(2.0), LINES, createdAt = NOW.plusSeconds(3600))

        assertThat(first.payloadHash).isEqualTo(second.payloadHash)
        assertThat(first.payloadHash).hasLength(64)
    }

    @Test
    fun `payload hash changes when servings or engine version change`() {
        val base = factory.preview(ID_1, VERSION_ID, Servings(2.0), LINES, createdAt = NOW)
        val moreServings = factory.preview(ID_1, VERSION_ID, Servings(3.0), LINES, createdAt = NOW)
        val newEngine = factory.preview(ID_1, VERSION_ID, Servings(2.0), LINES, "stage2-2", NOW)

        assertThat(base.payloadHash).isNotEqualTo(moreServings.payloadHash)
        assertThat(base.payloadHash).isNotEqualTo(newEngine.payloadHash)
    }

    @Test
    fun `payload hash changes when requirement lines change`() {
        val base = factory.preview(ID_1, VERSION_ID, Servings(1.0), LINES, createdAt = NOW)
        val changed =
            factory.preview(
                ID_1,
                VERSION_ID,
                Servings(1.0),
                listOf(RequirementLine(Flour, Quantity(2.0, Gram))),
                createdAt = NOW,
            )

        assertThat(base.payloadHash).isNotEqualTo(changed.payloadHash)
    }

    @Test
    fun `confirmation persists only a confirmed immutable record`() =
        runBlocking {
            val repository = InMemoryCalculationRecordRepository()
            val useCase = ConfirmCalculationRecordUseCase(repository, ImmediateTransactionRunner)
            val preview = factory.preview(ID_1, VERSION_ID, Servings(2.0), LINES, createdAt = NOW)

            val confirmed = useCase(preview, NOW)

            assertThat(confirmed).isInstanceOf(Outcome.Success::class.java)
            val saved = (confirmed as Outcome.Success).value
            assertThat(saved.status).isEqualTo(CalculationRecordStatus.CONFIRMED)
            assertThat(saved.payloadHash).isEqualTo(preview.payloadHash)
            assertThat(repository.records).hasSize(1)
            assertThat(repository.records.single().isConfirmed).isTrue()

            val again = useCase(saved, NOW)
            assertThat((again as Outcome.Failure).error.code).isEqualTo(ErrorCode.INV_003)
            assertThat(repository.saveAttempts).isEqualTo(1)
        }

    @Test
    fun `repository rejects a preview record`() =
        runBlocking {
            val repository = InMemoryCalculationRecordRepository()
            val preview = factory.preview(ID_1, VERSION_ID, Servings(1.0), LINES, createdAt = NOW)

            val result = repository.saveConfirmed(preview)

            val error = (result as Outcome.Failure).error
            assertThat(error.code).isEqualTo(ErrorCode.INV_003)
            assertThat(error).isInstanceOf(CulinaryError.Domain::class.java)
            assertThat(repository.records).isEmpty()
        }

    @Test
    fun `repository never overwrites an existing record`() =
        runBlocking {
            val repository = InMemoryCalculationRecordRepository()
            val first = confirmedRecord(hashSeed = "a", createdAt = NOW)
            val second = confirmedRecord(hashSeed = "b", createdAt = NOW.plusSeconds(1))
            repository.saveConfirmed(first)

            val result = repository.saveConfirmed(second)

            assertThat((result as Outcome.Failure).error.code).isEqualTo(ErrorCode.CON_001)
            assertThat(repository.records.single().payloadHash).isEqualTo(first.payloadHash)
        }

    @Test
    fun `duplicate payload hash does not create a second record`() =
        runBlocking {
            val repository = InMemoryCalculationRecordRepository()
            val first = confirmedRecord(hashSeed = "a", createdAt = NOW)
            repository.saveConfirmed(first)
            val duplicate = first.copy(id = CalculationRecordId("calc-2"))

            val result = repository.saveConfirmed(duplicate)

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            assertThat(repository.records).hasSize(1)
        }

    private fun confirmedRecord(
        hashSeed: String,
        createdAt: Instant,
    ): CalculationRecord =
        factory
            .preview(
                id = ID_1,
                recipeVersionId = VERSION_ID,
                servings = Servings(1.0),
                lines = listOf(RequirementLine(Flour, Quantity(1.0, Gram))),
                createdAt = createdAt,
            ).copy(payloadHash = hashSeed.repeat(64))
            .confirm(createdAt)
            .let { (it as Outcome.Success).value }

    private class InMemoryCalculationRecordRepository : CalculationRecordRepository {
        val records = mutableListOf<CalculationRecord>()
        var saveAttempts = 0

        override suspend fun saveConfirmed(record: CalculationRecord): Outcome<CalculationRecord> {
            saveAttempts += 1
            if (!record.isConfirmed) {
                return Outcome.failure(
                    CulinaryError.Domain(
                        detail = DomainDetail.InvalidState("calculation_record", "CONFIRMED", record.status.name),
                        code = ErrorCode.INV_003,
                    ),
                )
            }
            records.firstOrNull { it.id == record.id }?.let {
                return Outcome.failure(CulinaryError.Conflict(entity = "calculation_record"))
            }
            records.firstOrNull { it.payloadHash == record.payloadHash }?.let {
                return Outcome.success(it)
            }
            records += record
            return Outcome.success(record)
        }

        override suspend fun find(id: CalculationRecordId): Outcome<CalculationRecord?> =
            Outcome.success(records.firstOrNull { it.id == id })

        override suspend fun findByRecipeVersion(recipeVersionId: RecipeVersionId): Outcome<List<CalculationRecord>> =
            Outcome.success(records.filter { it.recipeVersionId == recipeVersionId })

        override suspend fun findByPayloadHash(payloadHash: String): Outcome<CalculationRecord?> =
            Outcome.success(records.firstOrNull { it.payloadHash == payloadHash })
    }

    private object ImmediateTransactionRunner : TransactionRunner {
        override suspend fun <T> write(block: suspend () -> T): Outcome<T> = Outcome.success(block())
    }

    private companion object {
        val NOW: Instant = Instant.ofEpochMilli(1_760_000_000_000L)
        val ID_1 = CalculationRecordId("calc-1")
        val ID_2 = CalculationRecordId("calc-2")
        val VERSION_ID = RecipeVersionId("recipe-v1")
        val Flour = IngredientId("flour")
        val Gram = Unit(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 1)
        val LINES = listOf(RequirementLine(Flour, Quantity(100.0, Gram)))
    }
}
