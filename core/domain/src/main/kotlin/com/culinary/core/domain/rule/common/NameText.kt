package com.culinary.core.domain.rule.common

import java.text.Normalizer
import java.util.Locale

/**
 * 档案名称规范化（食材 / 酱汁 / 菜谱共用）。
 *
 * 顺序固定：NFKC → trim → 连续空白折叠 → `Locale.ROOT` 小写。
 * 结果只用于查重/匹配，展示使用原始输入。
 */
object NameText {
    private val WHITESPACE = Regex("\\s+")

    fun normalize(raw: String): String =
        Normalizer
            .normalize(raw, Normalizer.Form.NFKC)
            .trim()
            .replace(WHITESPACE, " ")
            .lowercase(Locale.ROOT)
}
