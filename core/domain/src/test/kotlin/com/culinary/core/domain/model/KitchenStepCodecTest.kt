package com.culinary.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 步骤 JSON 编解码：v1 兼容、v2 时间往返、旧整段说明回退。 */
class KitchenStepCodecTest {
    @Test
    fun `v2 round trip preserves title body and time`() {
        val steps =
            listOf(
                KitchenStep(id = "s1", title = "腌制鸭胸", body = "抹盐静置", timeSeconds = 1800),
                KitchenStep(id = "s2", title = "准备平底锅", body = "擦净", timeSeconds = 0),
            )
        val encoded = KitchenStepCodec.encode(steps)
        var key = 0
        val decoded = KitchenStepCodec.decode(encoded, null) { "k${key++}" }
        assertThat(decoded).isEqualTo(steps)
    }

    @Test
    fun `v1 payload without time is readable`() {
        val json =
            """{"schemaVersion":1,"steps":[{"id":"a","title":"老步骤","body":"旧说明"}]}"""
        val decoded = KitchenStepCodec.decode(json, null) { "k" }
        assertThat(decoded).hasSize(1)
        assertThat(decoded.single().title).isEqualTo("老步骤")
        assertThat(decoded.single().timeSeconds).isEqualTo(0)
    }

    @Test
    fun `blank json falls back to legacy instructions as one step`() {
        val decoded = KitchenStepCodec.decode(null, "第一步\n第二步") { "fallback" }
        assertThat(decoded).hasSize(1)
        assertThat(decoded.single().body).isEqualTo("第一步\n第二步")
    }

    @Test
    fun `missing json and instructions yields no steps`() {
        assertThat(KitchenStepCodec.decode(null, null) { "k" }).isEmpty()
    }

    @Test
    fun `instructions text keeps original step order and text`() {
        val steps =
            listOf(
                KitchenStep(id = "1", title = "A", body = "a"),
                KitchenStep(id = "2", title = "", body = "b"),
            )
        assertThat(KitchenStepCodec.toInstructions(steps)).isEqualTo("1. A\na\n\n2. b")
    }
}
