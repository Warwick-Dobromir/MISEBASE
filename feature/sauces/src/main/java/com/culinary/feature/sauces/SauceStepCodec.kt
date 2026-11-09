package com.culinary.feature.sauces

import com.culinary.core.domain.model.KitchenStep
import com.culinary.core.domain.model.KitchenStepCodec

/**
 * 酱汁步骤编解码：结构定义在 `core:domain` 的 [KitchenStepCodec]，
 * 编辑器只负责把编辑态 [SauceStepEditor] 与领域步骤互换。
 *
 * ```json
 * {"schemaVersion":2,"steps":[{"id":"...","title":"...","body":"...","timeSeconds":0}]}
 * ```
 *
 * 步骤图片不写进 JSON，仍由 `image_asset.step_no` 关联，避免跨表引用失效。
 */
object SauceStepCodec {
    fun encode(steps: List<SauceStepEditor>): String = KitchenStepCodec.encode(steps.toSteps())

    fun decode(
        json: String?,
        fallbackInstructions: String?,
        keyFactory: () -> String,
    ): List<SauceStepEditor> =
        KitchenStepCodec
            .decode(json, fallbackInstructions, keyFactory)
            .map { step ->
                SauceStepEditor(
                    key = step.id,
                    title = step.title,
                    body = step.body,
                    timeSeconds = step.timeSeconds,
                )
            }

    /** 供旧详情/导出的可读文本；步骤 JSON 仍是权威来源。 */
    fun toInstructions(steps: List<SauceStepEditor>): String? = KitchenStepCodec.toInstructions(steps.toSteps())

    private fun List<SauceStepEditor>.toSteps(): List<KitchenStep> =
        map { step ->
            KitchenStep(
                id = step.key,
                title = step.title,
                body = step.body,
                timeSeconds = step.timeSeconds,
            )
        }
}
