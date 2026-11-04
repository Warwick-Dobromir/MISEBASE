package com.culinary.core.data.backup

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test

/** 写屏障：串行化、超时、嵌套拒绝、失败释放（E05 §6.3.1）。 */
class WriteBarrierTest {
    @Test
    fun `serializes concurrent writes`() =
        runTest {
            val barrier = WriteBarrier()
            var active = 0
            var maxActive = 0

            coroutineScope {
                (1..20)
                    .map {
                        async {
                            barrier.withWrite {
                                active += 1
                                maxActive = maxOf(maxActive, active)
                                yield()
                                active -= 1
                            }
                        }
                    }.awaitAll()
            }

            assertThat(maxActive).isEqualTo(1)
            assertThat(barrier.snapshot().activeWriters).isEqualTo(0)
            assertThat(barrier.snapshot().queuedWriters).isEqualTo(0)
        }

    @Test
    fun `times out when an active write holds the barrier`() =
        runTest {
            val barrier = WriteBarrier()
            val writerStarted = CompletableDeferred<Unit>()
            val releaseWriter = CompletableDeferred<Unit>()
            val writer =
                launch {
                    barrier.withWrite {
                        writerStarted.complete(Unit)
                        releaseWriter.await()
                    }
                }
            writerStarted.await()

            val result = barrier.acquireForBackup(timeoutMillis = 50)

            assertThat(result).isEqualTo(BarrierAcquireResult.TIMEOUT)
            assertThat(barrier.snapshot().barrierHeld).isFalse()
            releaseWriter.complete(Unit)
            writer.join()
        }

    @Test
    fun `releases the barrier after a failed backup and lets writes continue`() =
        runTest {
            val barrier = WriteBarrier()
            val acquired = barrier.acquireForBackup(timeoutMillis = 50)
            assertThat(acquired).isEqualTo(BarrierAcquireResult.ACQUIRED)

            runCatching { throw IllegalStateException("backup failed") }
            assertThat(barrier.releaseForBackup()).isTrue()

            val writeResult =
                runCatching {
                    barrier.withWrite { "written" }
                }
            assertThat(writeResult.getOrNull()).isEqualTo("written")
            assertThat(barrier.snapshot().barrierHeld).isFalse()
        }

    @Test
    fun `rejects a nested acquisition and unlocks exactly once`() =
        runTest {
            val barrier = WriteBarrier()
            assertThat(barrier.acquireForBackup(timeoutMillis = 50)).isEqualTo(BarrierAcquireResult.ACQUIRED)

            assertThat(barrier.acquireForBackup(timeoutMillis = 50)).isEqualTo(BarrierAcquireResult.ALREADY_HELD)

            assertThat(barrier.releaseForBackup()).isTrue()
            assertThat(barrier.releaseForBackup()).isFalse()
            assertThat(barrier.snapshot().barrierHeld).isFalse()
        }

    @Test
    fun `a queued write is served after the backup barrier is released`() =
        runTest {
            val barrier = WriteBarrier()
            assertThat(barrier.acquireForBackup(timeoutMillis = 50)).isEqualTo(BarrierAcquireResult.ACQUIRED)

            val queued = async { barrier.withWrite { "done" } }
            yield()
            assertThat(barrier.snapshot().queuedWriters).isEqualTo(1)

            assertThat(barrier.releaseForBackup()).isTrue()

            assertThat(queued.await()).isEqualTo("done")
        }
}
