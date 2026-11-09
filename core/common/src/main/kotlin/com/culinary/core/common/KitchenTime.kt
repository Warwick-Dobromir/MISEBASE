package com.culinary.core.common

import java.util.Locale

/**
 * 步骤时间的显示与输入规则（任务书第五阶段）：
 *
 * - 统一格式 `时/分/秒`，两位一组，共 `HH/MM/SS`；
 * - 连续输入从秒位向左填充：输入 `155` → `00/01/55`；
 * - 最多六位数字，超出部分保留最新的六位；
 * - 空串等价于 `00/00/00`（无有效时间）。
 *
 * 全部为纯函数，供编辑器输入组件、详情页与制作模式共用，避免三处各写一套。
 */
object KitchenTime {
    const val MAX_SECONDS: Int = 99 * 3600 + 59 * 60 + 59
    private const val MAX_DIGITS = 6

    /** 纯数字输入（最多六位，只保留最后六位）。 */
    fun digits(raw: String): String = raw.filter { it.isDigit() }.takeLast(MAX_DIGITS)

    /** 数字串 → 总秒数：从秒位向左填充。 */
    fun secondsOfDigits(raw: String): Int {
        val digits = digits(raw).padStart(MAX_DIGITS, '0')
        val hours = digits.substring(0, 2).toIntOrNull() ?: 0
        val minutes = digits.substring(2, 4).toIntOrNull() ?: 0
        val seconds = digits.substring(4, 6).toIntOrNull() ?: 0
        return hours * 3600 + minutes * 60 + seconds
    }

    /** 总秒数 → 六位数字串（用于输入组件回填）。 */
    fun digitsOf(seconds: Int): String {
        val safe = seconds.coerceIn(0, MAX_SECONDS)
        return String.format(Locale.ROOT, "%02d%02d%02d", safe / 3600, safe % 3600 / 60, safe % 60)
    }

    /** 总秒数 → `HH/MM/SS`；零显示为 `00/00/00`。 */
    fun format(seconds: Int): String {
        val safe = seconds.coerceIn(0, MAX_SECONDS)
        return String.format(Locale.ROOT, "%02d/%02d/%02d", safe / 3600, safe % 3600 / 60, safe % 60)
    }

    /** 档案步骤时间展示：`HH / MM / SS`（三段之间保留空格）。 */
    fun formatSpaced(seconds: Int): String {
        val safe = seconds.coerceIn(0, MAX_SECONDS)
        return String.format(Locale.ROOT, "%02d / %02d / %02d", safe / 3600, safe % 3600 / 60, safe % 60)
    }

    /** 三段数值 → 总秒数；分、秒钳制在 0..59。 */
    fun secondsOfParts(
        hours: Int,
        minutes: Int,
        seconds: Int,
    ): Int =
        (hours.coerceIn(0, 99) * 3600 + minutes.coerceIn(0, 59) * 60 + seconds.coerceIn(0, 59))
            .coerceIn(0, MAX_SECONDS)

    /** 预设时间的可读描述：`30分钟`、`1小时30分钟`、`45秒`。 */
    fun describe(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        if (safe == 0) return "0秒"
        val hours = safe / 3600
        val minutes = safe % 3600 / 60
        val secs = safe % 60
        return buildString {
            if (hours > 0) append(hours).append("小时")
            if (minutes > 0) append(minutes).append("分钟")
            if (secs > 0) append(secs).append("秒")
        }
    }

    /** 倒计时剩余时间：`MM:SS`（超过一小时为 `H:MM:SS`）。 */
    fun countdown(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = safe % 3600 / 60
        val secs = safe % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, secs)
        }
    }
}
