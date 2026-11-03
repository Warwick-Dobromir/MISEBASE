package com.culinary.core.platform.storage

import android.net.Uri
import com.culinary.core.common.Outcome
import com.culinary.core.domain.platform.StorageHandle
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** SAF 适配：句柄映射、流读写与取消路径（A03A §8.1）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafBackupStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `writes bytes through a created document handle`() =
        runBlocking {
            val target = temporaryFolder.newFile("backup.zip")
            val storage = SafBackupStorage(context, FakeLauncher(createUri = Uri.fromFile(target)))

            val handle = (storage.createDocument("backup.zip", "application/zip") as Outcome.Success).value
            val output = (storage.openOutput(handle) as Outcome.Success).value
            output.use { it.write(byteArrayOf(1, 2, 3)) }

            assertThat(target.readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
        }

    @Test
    fun `reads bytes back through the same handle`() =
        runBlocking {
            val source = temporaryFolder.newFile("read.zip").apply { writeBytes(byteArrayOf(7, 8, 9)) }
            val storage = SafBackupStorage(context, FakeLauncher(pickUri = Uri.fromFile(source)))

            val handle = (storage.pickDocument() as Outcome.Success).value
            val bytes = (storage.openInput(handle) as Outcome.Success).value.use { it.readBytes() }

            assertThat(bytes).isEqualTo(byteArrayOf(7, 8, 9))
        }

    @Test
    fun `maps a cancelled picker to a predictable failure`() =
        runBlocking {
            val storage = SafBackupStorage(context, FakeLauncher(createUri = null, pickUri = null))

            val created = storage.createDocument("backup.zip", "application/zip")
            val picked = storage.pickDocument()

            assertThat((created as Outcome.Failure).error.code.value).isEqualTo("CUL-FILE-001")
            assertThat((picked as Outcome.Failure).error.retryable).isFalse()
        }

    @Test
    fun `rejects a blank handle at construction`() {
        val failure = runCatching { StorageHandle("") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
    }

    private class FakeLauncher(
        private val createUri: Uri? = null,
        private val pickUri: Uri? = null,
    ) : DocumentLauncher {
        override suspend fun createDocument(
            displayName: String,
            mimeType: String,
        ): Uri? = createUri

        override suspend fun pickDocument(mimeTypes: Array<String>): Uri? = pickUri
    }
}
