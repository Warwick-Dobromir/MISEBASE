package com.culinary.core.domain.snapshot

import com.culinary.core.domain.model.RequirementLine
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Servings
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * 计算 payload 的稳定哈希（MB-D-017）。
 *
 * 规范串**不包含**生成时间与记录 ID：同一配方版本、份数、需求行与引擎版本
 * 必须得到同一哈希，便于跨设备/跨次运行比对"旧结果是否一致"。
 *
 * 规范格式（`\n` 分隔，行内 `|` 分隔）：
 * `engineVersion` / `recipeVersionId` / `servings` / 每行
 * `ingredientId|amount|unitCode|optional`。
 */
class CalculationPayloadHasher {
    fun hash(
        engineVersion: String,
        recipeVersionId: RecipeVersionId,
        servings: Servings,
        lines: List<RequirementLine>,
    ): String {
        val canonical =
            buildString {
                append(engineVersion).append('\n')
                append(recipeVersionId.value).append('\n')
                append(decimal(servings.count)).append('\n')
                lines.forEach { line ->
                    append(line.ingredientId.value).append('|')
                    append(decimal(line.quantity.amount)).append('|')
                    append(line.quantity.unit.code).append('|')
                    append(line.isOptional).append('\n')
                }
            }
        return sha256(canonical)
    }

    private fun decimal(value: Double): String =
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private fun sha256(text: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
