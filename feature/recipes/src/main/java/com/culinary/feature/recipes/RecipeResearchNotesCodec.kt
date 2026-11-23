package com.culinary.feature.recipes

import java.util.Base64

/** 菜谱研发字段的兼容序列化：继续使用既有 notes 列，旧版纯文本作为调整记录读取。 */
internal data class RecipeResearchNotes(
    val productStandard: String = "",
    val texture: String = "",
    val mainIngredients: String = "",
    val preparationSummary: String = "",
    val keyParameters: String = "",
    val storageNotes: String = "",
    val adjustmentRecords: String = "",
    val testRecords: String = "",
)

internal object RecipeResearchNotesCodec {
    private const val PREFIX = "MISEBASE_RND_V1"

    fun decode(raw: String?): RecipeResearchNotes {
        if (raw.isNullOrBlank()) return RecipeResearchNotes()
        if (!raw.startsWith("$PREFIX\n")) {
            return RecipeResearchNotes(adjustmentRecords = raw)
        }
        val values = raw.removePrefix("$PREFIX\n").split('\n')
        fun value(index: Int): String =
            values.getOrNull(index)?.takeIf { it.isNotEmpty() }?.let {
                runCatching { String(Base64.getDecoder().decode(it), Charsets.UTF_8) }.getOrDefault("")
            }.orEmpty()
        return RecipeResearchNotes(
            productStandard = value(0),
            texture = value(1),
            mainIngredients = value(2),
            preparationSummary = value(3),
            keyParameters = value(4),
            storageNotes = value(5),
            adjustmentRecords = value(6),
            testRecords = value(7),
        )
    }

    fun encode(value: RecipeResearchNotes): String {
        val fields = listOf(
            value.productStandard,
            value.texture,
            value.mainIngredients,
            value.preparationSummary,
            value.keyParameters,
            value.storageNotes,
            value.adjustmentRecords,
            value.testRecords,
        )
        return "$PREFIX\n" + fields.joinToString("\n") {
            Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8))
        }
    }
}
