package com.culinary.core.domain.model

/**
 * 酱汁 / 菜谱共用的制作步骤模型。
 *
 * [timeSeconds] 为零表示"没有有效计时"，与"未设置"在制作流程中等价；
 * 步骤本身仍然完整展示，不允许因为无时间而删除。
 */
data class KitchenStep(
    val id: String,
    val title: String = "",
    val body: String = "",
    val timeSeconds: Int = 0,
) {
    val hasTime: Boolean get() = timeSeconds > 0

    val hasText: Boolean get() = title.isNotBlank() || body.isNotBlank()

    val hasContent: Boolean get() = hasText || hasTime
}

/**
 * `steps_json` 编解码：v1 只有 id/title/body，v2 增加 `timeSeconds`。
 *
 * 兼容策略：读取 v1 时时间按 0 处理，并允许再次以 v2 保存；
 * 图片不进入 JSON，仍由 `image_asset.step_no` 关联，避免跨表引用失效。
 */
object KitchenStepCodec {
    private const val SCHEMA_VERSION = 2
    private val schemaVersionPattern = Regex("\"schemaVersion\"\\s*:\\s*(\\d+)")
    private val stepsArrayPattern = Regex("\"steps\"\\s*:\\s*\\[(.*)]", RegexOption.DOT_MATCHES_ALL)
    private val timePattern = Regex("\"timeSeconds\"\\s*:\\s*(\\d+)")

    fun encode(steps: List<KitchenStep>): String =
        buildString {
            append("{\"schemaVersion\":").append(SCHEMA_VERSION).append(",\"steps\":[")
            steps.forEachIndexed { index, step ->
                if (index > 0) append(',')
                append("{\"id\":\"")
                    .append(escape(step.id))
                    .append("\",\"title\":\"")
                    .append(escape(step.title.trim()))
                    .append("\",\"body\":\"")
                    .append(escape(step.body.trim()))
                    .append("\",\"timeSeconds\":")
                    .append(step.timeSeconds.coerceAtLeast(0))
                    .append('}')
            }
            append("]}")
        }

    /**
     * 解码步骤；JSON 为空或不可解析时退回旧的整段说明文本（单步骤）。
     */
    fun decode(
        json: String?,
        fallbackInstructions: String?,
        idFactory: () -> String,
    ): List<KitchenStep> {
        val parsed = runCatching { parse(json.orEmpty(), idFactory) }.getOrNull()
        if (!parsed.isNullOrEmpty()) return parsed
        val fallback = fallbackInstructions?.trim().orEmpty()
        return if (fallback.isEmpty()) {
            emptyList()
        } else {
            listOf(KitchenStep(id = idFactory(), body = fallback))
        }
    }

    /** 兼容旧导出/旧详情：标题与说明合并为可读文本；步骤 JSON 仍是权威来源。 */
    fun toInstructions(steps: List<KitchenStep>): String? =
        steps
            .mapIndexed { index, step ->
                val title = step.title.trim()
                val body = step.body.trim()
                when {
                    title.isNotEmpty() && body.isNotEmpty() -> "${index + 1}. $title\n$body"
                    title.isNotEmpty() -> "${index + 1}. $title"
                    body.isNotEmpty() -> "${index + 1}. $body"
                    else -> ""
                }
            }.filter { it.isNotBlank() }
            .joinToString("\n\n")
            .takeIf { it.isNotBlank() }

    private fun parse(
        json: String,
        idFactory: () -> String,
    ): List<KitchenStep> {
        if (json.isBlank()) return emptyList()
        val version =
            schemaVersionPattern
                .find(json)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: 0
        if (version != 1 && version != SCHEMA_VERSION) return emptyList()
        val arrayText = stepsArrayPattern.find(json)?.groupValues?.get(1) ?: return emptyList()
        return extractObjects(arrayText).mapNotNull { objectText ->
            val id = stringField(objectText, "id")
            val title = stringField(objectText, "title")
            val body = stringField(objectText, "body")
            val time =
                timePattern
                    .find(objectText)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull() ?: 0
            if (id == null && title == null && body == null && time <= 0) {
                null
            } else {
                KitchenStep(
                    id = id?.takeIf { it.isNotBlank() } ?: idFactory(),
                    title = title.orEmpty(),
                    body = body.orEmpty(),
                    timeSeconds = time.coerceAtLeast(0),
                )
            }
        }
    }

    /** 提取数组中的顶层对象，尊重字符串内的引号与转义。 */
    @Suppress("NestedBlockDepth") // 扫描器必须同时跟踪字符串、转义和括号层级
    private fun extractObjects(arrayText: String): List<String> {
        val objects = mutableListOf<String>()
        var index = 0
        while (index < arrayText.length) {
            if (arrayText[index] == '{') {
                val start = index
                var depth = 0
                var inString = false
                var escaped = false
                while (index < arrayText.length) {
                    val char = arrayText[index]
                    if (inString) {
                        when {
                            escaped -> escaped = false
                            char == '\\' -> escaped = true
                            char == '"' -> inString = false
                        }
                    } else {
                        when (char) {
                            '"' -> inString = true
                            '{' -> depth++
                            '}' -> {
                                depth--
                                if (depth == 0) {
                                    objects += arrayText.substring(start, index + 1)
                                    break
                                }
                            }
                        }
                    }
                    index++
                }
            }
            index++
        }
        return objects
    }

    private fun stringField(
        objectText: String,
        name: String,
    ): String? {
        val pattern = Regex("\"$name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val raw = pattern.find(objectText)?.groupValues?.get(1) ?: return null
        return unescape(raw)
    }

    private fun escape(value: String): String =
        buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (char.code < 0x20) {
                            append("\\u").append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
        }

    private fun unescape(value: String): String =
        buildString {
            var index = 0
            while (index < value.length) {
                val char = value[index]
                if (char != '\\' || index + 1 >= value.length) {
                    append(char)
                    index++
                    continue
                }
                when (val next = value[index + 1]) {
                    '\\' -> append('\\')
                    '"' -> append('"')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    'u' -> {
                        val hex = value.substring(index + 2, (index + 6).coerceAtMost(value.length))
                        hex.toIntOrNull(16)?.let { append(it.toChar()) }
                        index += 4
                    }

                    else -> append(next)
                }
                index += 2
            }
        }
}
