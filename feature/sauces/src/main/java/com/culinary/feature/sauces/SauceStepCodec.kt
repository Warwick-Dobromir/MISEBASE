package com.culinary.feature.sauces

/**
 * 制作步骤结构：[steps_json] 采用带版本号的稳定 JSON，后续可扩展字段。
 *
 * ```json
 * {"schemaVersion":1,"steps":[{"id":"...","title":"...","body":"..."}]}
 * ```
 *
 * 步骤图片不写进 JSON，仍由 image_asset.step_no 关联，避免跨表引用失效。
 * 这里手写最小 JSON 编解码，避免把编辑器状态绑定到 Android JSON 实现的测试差异。
 */
object SauceStepCodec {
    private const val SCHEMA_VERSION = 1
    private val schemaVersionPattern = Regex("\"schemaVersion\"\\s*:\\s*(\\d+)")
    private val stepsArrayPattern = Regex("\"steps\"\\s*:\\s*\\[(.*)]", RegexOption.DOT_MATCHES_ALL)

    fun encode(steps: List<SauceStepEditor>): String =
        buildString {
            append("{\"schemaVersion\":").append(SCHEMA_VERSION).append(",\"steps\":[")
            steps.forEachIndexed { index, step ->
                if (index > 0) append(',')
                append("{\"id\":\"")
                    .append(escape(step.key))
                    .append("\",\"title\":\"")
                    .append(escape(step.title.trim()))
                    .append("\",\"body\":\"")
                    .append(escape(step.body.trim()))
                    .append("\"}")
            }
            append("]}")
        }

    fun decode(
        json: String?,
        fallbackInstructions: String?,
        keyFactory: () -> String,
    ): List<SauceStepEditor> {
        val parsed = runCatching { parse(json.orEmpty(), keyFactory) }.getOrNull()
        if (!parsed.isNullOrEmpty()) return parsed
        val fallback = fallbackInstructions?.trim().orEmpty()
        return if (fallback.isEmpty()) {
            emptyList()
        } else {
            listOf(SauceStepEditor(key = keyFactory(), body = fallback))
        }
    }

    private fun parse(
        json: String,
        keyFactory: () -> String,
    ): List<SauceStepEditor> {
        if (json.isBlank()) return emptyList()
        val version =
            schemaVersionPattern
                .find(json)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: 0
        if (version != SCHEMA_VERSION) return emptyList()
        val arrayText = stepsArrayPattern.find(json)?.groupValues?.get(1) ?: return emptyList()
        return extractObjects(arrayText).mapNotNull { objectText ->
            val id = field(objectText, "id")
            val title = field(objectText, "title")
            val body = field(objectText, "body")
            if (id == null && title == null && body == null) {
                null
            } else {
                SauceStepEditor(
                    key = id?.takeIf { it.isNotBlank() } ?: keyFactory(),
                    title = title.orEmpty(),
                    body = body.orEmpty(),
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

    private fun field(
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

    /** 供旧详情/导出的可读文本；步骤 JSON 仍是权威来源。 */
    fun toInstructions(steps: List<SauceStepEditor>): String? =
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
}
