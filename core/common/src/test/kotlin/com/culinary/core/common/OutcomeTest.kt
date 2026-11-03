package com.culinary.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class OutcomeTest {
    @Test
    fun `map transforms a success value`() {
        val result = Outcome.success(2).map { it * 3 }

        assertThat(result).isEqualTo(Outcome.success(6))
    }

    @Test
    fun `map keeps the original failure`() {
        val error = CulinaryError.Validation(field = "name", rule = "must not be blank")
        val result: Outcome<Int> = Outcome.failure(error)

        val mapped = result.map { it * 3 }

        assertThat(mapped).isInstanceOf(Outcome.Failure::class.java)
        assertThat((mapped as Outcome.Failure).error).isSameInstanceAs(error)
    }

    @Test
    fun `flatMap does not run the transform on failure`() {
        val called = AtomicBoolean(false)
        val result: Outcome<Int> =
            Outcome.failure(
                CulinaryError.NotFound(entity = "ingredient", id = "missing"),
            )

        result.flatMap {
            called.set(true)
            Outcome.success(it)
        }

        assertThat(called.get()).isFalse()
    }

    @Test
    fun `valueOrNull and errorOrNull expose the active side`() {
        val success = Outcome.success("ok")
        assertThat(success.valueOrNull()).isEqualTo("ok")
        assertThat(success.errorOrNull()).isNull()

        val failure: Outcome<String> = Outcome.failure(CulinaryError.NotFound("recipe", "r1"))
        assertThat(failure.valueOrNull()).isNull()
        assertThat(failure.errorOrNull()).isNotNull()
    }

    @Test
    fun `domain error keeps code retryable flag and detail`() {
        val error =
            CulinaryError.Domain(
                detail = DomainDetail.UnitIncompatible(fromUnit = "cup", toUnit = "g", ingredientId = null),
                code = ErrorCode.UNIT_001,
            )

        assertThat(error.code).isEqualTo(ErrorCode.UNIT_001)
        assertThat(error.retryable).isFalse()
        assertThat(error.code.value).isEqualTo("CUL-UNIT-001")
    }

    @Test
    fun `conflict is retryable by default`() {
        val error = CulinaryError.Conflict(entity = "ingredient")

        assertThat(error.retryable).isTrue()
        assertThat(error.code).isEqualTo(ErrorCode.CON_001)
    }
}
