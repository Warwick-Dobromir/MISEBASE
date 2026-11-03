package com.culinary.core.domain.snapshot

import com.culinary.core.common.Outcome
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 快照 payload：稳定字段、往返与错误路径（R01 阶段 8）。 */
class SnapshotPayloadCodecTest {
    private val payload =
        RequirementSnapshotPayload(
            generatedAt = "2026-11-04T00:00:00Z",
            lines =
                listOf(
                    RequirementSnapshotLine("flour", 150.0, "g", optional = false),
                    RequirementSnapshotLine("egg", 2.0, "piece", optional = true),
                ),
        )

    @Test
    fun `round trips a payload`() {
        val encoded = SnapshotPayloadCodec.encode(payload)
        val decoded = SnapshotPayloadCodec.decode(encoded)

        assertThat(decoded).isInstanceOf(Outcome.Success::class.java)
        assertThat((decoded as Outcome.Success).value).isEqualTo(payload)
    }

    @Test
    fun `ignores unknown fields for forward compatibility`() {
        val json =
            """
            {"generatedAt":"2026-11-04T00:00:00Z","futureField":1,"lines":[]}
            """.trimIndent()

        val decoded = SnapshotPayloadCodec.decode(json)

        assertThat(decoded).isInstanceOf(Outcome.Success::class.java)
        assertThat((decoded as Outcome.Success).value.lines).isEmpty()
    }

    @Test
    fun `rejects malformed json with a transfer error`() {
        val decoded = SnapshotPayloadCodec.decode("{not json")

        assertThat((decoded as Outcome.Failure).error.code.value).isEqualTo("CUL-BACKUP-003")
    }
}
