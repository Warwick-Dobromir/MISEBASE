package com.culinary.core.data.transaction

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** BUSY 重试边界：只重试 BUSY/LOCKED，次数有限，其他异常直接抛出。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BusyRetryPolicyTest {
    @Test
    fun `retries busy errors up to the limit and then succeeds`() =
        runBlocking {
            var attempts = 0

            val value =
                BusyRetryPolicy(maxAttempts = 3, backoffMillis = listOf(0L)).retry {
                    attempts++
                    if (attempts < 3) throw SQLiteException("database is locked") else "ok"
                }

            assertThat(value).isEqualTo("ok")
            assertThat(attempts).isEqualTo(3)
        }

    @Test
    fun `gives up after the configured attempts`() {
        var attempts = 0

        assertThrows(SQLiteException::class.java) {
            runBlocking {
                BusyRetryPolicy(maxAttempts = 3, backoffMillis = listOf(0L)).retry {
                    attempts++
                    throw SQLiteException("database is locked")
                }
            }
        }

        assertThat(attempts).isEqualTo(3)
    }

    @Test
    fun `does not retry non busy errors`() {
        var attempts = 0

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                BusyRetryPolicy(maxAttempts = 3, backoffMillis = listOf(0L)).retry {
                    attempts++
                    throw SQLiteConstraintException("UNIQUE constraint failed")
                }
            }
        }

        assertThat(attempts).isEqualTo(1)
    }
}
