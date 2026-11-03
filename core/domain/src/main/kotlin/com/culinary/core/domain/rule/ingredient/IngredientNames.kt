package com.culinary.core.domain.rule.ingredient

import java.text.Normalizer
import java.util.Locale

/**
 * 食材名称规范化（A06 §3.2）。
 *
 * 顺序固定：NFKC → trim → 连续空白折叠为单个空格 → `Locale.ROOT` 小写。
 * 规范化结果只用于查重/匹配，展示仍使用用户输入的原始名称。
 */
object IngredientNames {
    private val WHITESPACE = Regex("\\s+")

    fun normalize(raw: String): String =
        Normalizer
            .normalize(raw, Normalizer.Form.NFKC)
            .trim()
            .replace(WHITESPACE, " ")
            .lowercase(Locale.ROOT)
}
