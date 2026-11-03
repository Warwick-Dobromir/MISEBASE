package com.culinary.core.data.backup

import com.culinary.core.common.Outcome
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 附件恢复：路径安全、内容落盘与失败保护（G3-1）。 */
class AttachmentRestorerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val images by lazy { File(temporaryFolder.root, "images") }
    private val rollback by lazy { File(temporaryFolder.root, "rollback") }

    @Test
    fun `installs image entries under the images directory`() {
        val restorer = AttachmentRestorer(images, rollback)

        val result =
            restorer.install(
                mapOf(
                    "images/ab/first.jpg" to byteArrayOf(1, 2, 3),
                    "images/cd/second.jpg" to byteArrayOf(4, 5, 6),
                    "database/culinary.db" to byteArrayOf(9),
                ),
            )

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        assertThat((result as Outcome.Success).value).isEqualTo(2)
        assertThat(File(temporaryFolder.root, "images/ab/first.jpg").readBytes())
            .isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(File(temporaryFolder.root, "images/cd/second.jpg").readBytes())
            .isEqualTo(byteArrayOf(4, 5, 6))
    }

    @Test
    fun `rejects an unsafe path without writing anything`() {
        val restorer = AttachmentRestorer(images, rollback)

        val result = restorer.install(mapOf("images/../escape.jpg" to byteArrayOf(1)))

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat(File(temporaryFolder.root, "escape.jpg").exists()).isFalse()
    }

    @Test
    fun `keeps the previous file when the same attachment is replaced`() {
        val target = File(temporaryFolder.root, "images/ab/first.jpg")
        target.parentFile?.mkdirs()
        target.writeBytes(byteArrayOf(7))
        val restorer = AttachmentRestorer(images, rollback)

        val result = restorer.install(mapOf("images/ab/first.jpg" to byteArrayOf(8, 8)))
        assertThat(result).isInstanceOf(Outcome.Success::class.java)

        assertThat(target.readBytes()).isEqualTo(byteArrayOf(8, 8))
        assertThat(File(rollback, "images_ab_first.jpg").readBytes()).isEqualTo(byteArrayOf(7))
    }
}
