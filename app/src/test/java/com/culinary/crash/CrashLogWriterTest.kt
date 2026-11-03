package com.culinary.crash

import com.culinary.core.common.Clock
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/** 崩溃日志：内容、委托与同名冲突（E03 §8）。 */
class CrashLogWriterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val clock = Clock { Instant.ofEpochMilli(1_760_000_000_000L) }
    private val writer by lazy { CrashLogWriter(temporaryFolder.newFolder("crash-logs"), clock) }

    @Test
    fun `writes the thread exception and stack trace`() {
        val file = writer.write(Thread.currentThread(), IllegalStateException("boom"))

        val text = file.readText()
        assertThat(text).contains("exception=java.lang.IllegalStateException")
        assertThat(text).contains("message=boom")
        assertThat(text).contains("thread=${Thread.currentThread().name}")
        assertThat(text).contains("java.lang.IllegalStateException: boom")
    }

    @Test
    fun `delegates to the previous handler after writing`() {
        val delegated = AtomicReference<Throwable?>(null)
        val previous = Thread.UncaughtExceptionHandler { _, error -> delegated.set(error) }
        val handler = writer.install(previous)
        val error = IllegalStateException("boom")

        handler.uncaughtException(Thread.currentThread(), error)

        assertThat(delegated.get()).isSameInstanceAs(error)
        val files =
            temporaryFolder.root
                .walkTopDown()
                .filter { it.isFile && it.name.startsWith("crash-") }
                .toList()
        assertThat(files).hasSize(1)
    }

    @Test
    fun `does not overwrite a log written in the same millisecond`() {
        val first = writer.write(Thread.currentThread(), IllegalStateException("first"))
        val second = writer.write(Thread.currentThread(), IllegalStateException("second"))

        assertThat(first).isNotEqualTo(second)
        assertThat(first.exists()).isTrue()
        assertThat(second.exists()).isTrue()
        assertThat(second.readText()).contains("second")
    }
}
