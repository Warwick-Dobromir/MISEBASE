package com.culinary.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ErrorCodeTest {
    @Test
    fun `all error codes follow the documented format`() {
        ErrorCode.entries.forEach { code ->
            assertThat(code.value).matches("CUL-[A-Z]+-[0-9]{3}")
        }
    }

    @Test
    fun `error code values are unique`() {
        val values = ErrorCode.entries.map { it.value }

        assertThat(values).containsNoDuplicates()
    }

    @Test
    fun `retryable flags follow the E03 table`() {
        val expectedRetryable =
            mapOf(
                ErrorCode.VAL_001 to false,
                ErrorCode.CON_001 to true,
                ErrorCode.DB_001 to true,
                ErrorCode.BACKUP_001 to false,
                ErrorCode.BACKUP_003 to true,
                ErrorCode.UNKNOWN_001 to false,
            )

        expectedRetryable.forEach { (code, retryable) ->
            assertThat(code.retryable).isEqualTo(retryable)
        }
    }
}
