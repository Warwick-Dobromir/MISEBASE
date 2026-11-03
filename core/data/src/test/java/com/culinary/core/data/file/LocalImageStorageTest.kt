package com.culinary.core.data.file

import com.culinary.core.common.Outcome
import com.culinary.core.data.util.Sha256
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 图片存储：原子写、哈希、路径越界拒绝与缺失降级（D-015 / E03 §8.2）。 */
class LocalImageStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val root: File by lazy { temporaryFolder.newFolder("files") }
    private val storage: LocalImageStorage by lazy { LocalImageStorage(root) }

    @Test
    fun `stores a webp image atomically with its hash`() =
        runBlocking {
            val bytes = byteArrayOf(1, 2, 3, 4, 5)

            val result = storage.store(bytes, "image/webp")

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val stored = (result as Outcome.Success).value
            assertThat(stored.relativePath).matches("images/[0-9a-f]{2}/[0-9a-f-]{36}\\.webp")
            assertThat(stored.sizeBytes).isEqualTo(5)
            assertThat(storage.exists(stored.relativePath)).isTrue()
            assertThat(storage.readOrNull(stored.relativePath)).isEqualTo(bytes)
            assertThat(stored.sha256).isEqualTo(Sha256.of(bytes))
            assertNoTemporaryFiles()
        }

    @Test
    fun `returns null for a missing file instead of failing`() {
        assertThat(storage.exists("images/00/missing.webp")).isFalse()
        assertThat(storage.readOrNull("images/00/missing.webp")).isNull()
    }

    @Test
    fun `rejects path traversal and absolute paths`() {
        assertThat(storage.resolve("../escape.webp")).isNull()
        assertThat(storage.resolve("/etc/passwd")).isNull()
        assertThat(storage.resolve("images/../../escape.webp")).isNull()
        assertThat(storage.readOrNull("../escape.webp")).isNull()
    }

    @Test
    fun `deletes idempotently`() =
        runBlocking {
            val stored = (storage.store(byteArrayOf(9), "image/png") as Outcome.Success).value

            val first = storage.delete(stored.relativePath)
            val second = storage.delete(stored.relativePath)

            assertThat(first).isInstanceOf(Outcome.Success::class.java)
            assertThat(second).isInstanceOf(Outcome.Success::class.java)
            assertThat(storage.exists(stored.relativePath)).isFalse()
        }

    private fun assertNoTemporaryFiles() {
        val leftovers = root.walkTopDown().filter { it.isFile && it.name.endsWith(".part") }.toList()
        assertThat(leftovers).isEmpty()
    }
}
