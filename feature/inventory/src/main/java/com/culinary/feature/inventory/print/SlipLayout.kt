package com.culinary.feature.inventory.print

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 打印纸宽：热敏小票常见的 58mm 与 80mm 两种规格。
 *
 * 本文件不依赖任何 Android API：折行、行高与高度都是纯 Kotlin，可在 JVM 单测里直接断言。
 */
enum class SlipWidth(
    val millimetres: Int,
) {
    MM58(58),
    MM80(80),
}

/**
 * 一页小票的排版计划。
 *
 * @property widthMm 纸宽（mm）
 * @property heightMm 纸高（mm）：由 [SlipLayout.plan] 按内容估算，clamp 到 40..1200
 * @property lines 逻辑行；折行由 [SlipLayout.wrap] 在绘制时按纸宽完成
 */
data class SlipRenderPlan(
    val widthMm: Int,
    val heightMm: Int,
    val lines: List<SlipLine>,
)

/**
 * 逻辑行：一段文本 + 字重 + 字号 + 对齐方式。
 *
 * 一行只承载一段文本，所以"名称 / 数量"用两行表达：名称左对齐加粗，数量单独一行右对齐
 * （[alignEnd]）。比例字体下用空格拼两列必然错位，因此不做空格对齐。
 */
data class SlipLine(
    val text: String,
    val bold: Boolean = false,
    val sizeSp: Float = 10f,
    val alignEnd: Boolean = false,
)

/**
 * 小票文本构造器：只产出逻辑行，不关心纸宽与折行（见 [SlipLayout]）。
 *
 * 全部是纯函数，同一次输入永远得到同一组行，便于单测与重打比对。
 */
object SlipTextBuilder {
    /** 标题字号（sp）。 */
    const val TITLE_SIZE_SP: Float = 14f

    /** 正文字号（sp）。 */
    const val BODY_SIZE_SP: Float = 10f

    /** 明细与注释字号（sp）。 */
    const val DETAIL_SIZE_SP: Float = 9f

    /** 分隔线长度：58mm 每行约 24 个半角字符，取 24 保证不折行。 */
    const val DIVIDER_CHARS: Int = 24

    /** 明细缩进：两个空格，视觉上从属于上一行。 */
    private const val DETAIL_INDENT = "  "

    /**
     * 表头：标题、副标题、单据号与元信息。
     *
     * @param meta 键值对（例如"日期" to "2026-11-10"），按 `标签：值` 逐行输出
     */
    fun header(
        title: String,
        subtitle: String? = null,
        code: String? = null,
        meta: List<Pair<String, String>> = emptyList(),
    ): List<SlipLine> =
        buildList {
            add(SlipLine(title.trim().ifEmpty { "库存单据" }, bold = true, sizeSp = TITLE_SIZE_SP))
            subtitle?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(SlipLine(it, sizeSp = BODY_SIZE_SP))
            }
            code?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(SlipLine("单号：$it", sizeSp = DETAIL_SIZE_SP))
            }
            if (meta.isNotEmpty()) {
                addAll(divider())
                meta.forEach { (label, value) ->
                    add(SlipLine("${label.trim()}：${value.trim()}", sizeSp = DETAIL_SIZE_SP))
                }
            }
        }

    /** 分隔线；`count` 按半角字符数计。 */
    fun divider(
        char: Char = '-',
        count: Int = DIVIDER_CHARS,
    ): List<SlipLine> =
        listOf(
            SlipLine(
                char.toString().repeat(count.coerceAtLeast(1)),
                sizeSp = DETAIL_SIZE_SP,
            ),
        )

    /**
     * 一条明细：名称、数量（含单位）与可选的附加说明。
     *
     * @param qtyWithUnit 已经拼好单位的数量，例如 `3.5 kg`
     * @param detailLines 批次号、库位、到期日等附加行
     */
    fun item(
        name: String,
        qtyWithUnit: String,
        detailLines: List<String> = emptyList(),
    ): List<SlipLine> =
        buildList {
            add(SlipLine(name.trim().ifEmpty { "（未命名）" }, bold = true, sizeSp = BODY_SIZE_SP))
            qtyWithUnit.trim().takeIf { it.isNotEmpty() }?.let {
                add(SlipLine(it, sizeSp = BODY_SIZE_SP, alignEnd = true))
            }
            detailLines
                .map(String::trim)
                .filter(String::isNotEmpty)
                .forEach { add(SlipLine("$DETAIL_INDENT$it", sizeSp = DETAIL_SIZE_SP)) }
        }

    /** 合计行：加粗单行，标签与数值之间留两个空格。 */
    fun total(
        label: String,
        value: String,
    ): List<SlipLine> =
        listOf(
            SlipLine(
                "${label.trim()}  ${value.trim()}",
                bold = true,
                sizeSp = BODY_SIZE_SP,
            ),
        )

    /** 页脚：分隔线 + 备注（备注为空时只有分隔线）。 */
    fun footer(note: String? = null): List<SlipLine> =
        buildList {
            addAll(divider())
            note?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(SlipLine(it, sizeSp = DETAIL_SIZE_SP))
            }
        }

    /** 空行，用于分段留白。 */
    fun blank(count: Int = 1): List<SlipLine> = List(count.coerceAtLeast(1)) { SlipLine("", sizeSp = DETAIL_SIZE_SP) }
}

/**
 * 排版计算：折行、行高与整页高度。
 *
 * 纯计算，不依赖 Android；[InventoryDocumentAdapter] 复用同一套列宽/行高常量绘制，
 * 保证"算出来的高度"与"画出来的位置"一致。
 */
object SlipLayout {
    /** 上下各留 6mm 边距。 */
    const val MARGIN_MM: Float = 6f

    /** 总高度下限（mm）。 */
    const val MIN_HEIGHT_MM: Int = 40

    /** 总高度上限（mm）：热敏纸整卷很长，1.2m 足够一张单据。 */
    const val MAX_HEIGHT_MM: Int = 1200

    /** 纸宽的合理范围（mm），防止异常值让 MediaSize 构造失败。 */
    const val MIN_WIDTH_MM: Int = 20
    const val MAX_WIDTH_MM: Int = 200

    /** 行高相对字号的倍数。 */
    const val LINE_HEIGHT_FACTOR: Float = 1.35f

    /** 基线相对行顶的偏移（字号倍数），与 [LINE_HEIGHT_FACTOR] 配合保证字不出行。 */
    const val BASELINE_FACTOR: Float = 0.8f

    /** 58mm 纸每行约 24 个半角字符。 */
    const val COLUMNS_AT_58MM: Int = 24

    /** 80mm 纸每行约 33 个半角字符。 */
    const val COLUMNS_AT_80MM: Int = 33

    /** 1mm = 1000/25.4 mil（`PrintAttributes.MediaSize` 的单位是 1/1000 英寸）。 */
    const val MILS_PER_MM: Double = 1000.0 / 25.4

    /** PDF 画布单位是点（1/72 英寸）：1mm = 72/25.4 点。 */
    const val POINTS_PER_MM: Float = 72f / 25.4f

    /** 点 → mm。 */
    const val MM_PER_POINT: Float = 25.4f / 72f

    /** 页边距（点），绘制时使用。 */
    const val MARGIN_POINTS: Float = MARGIN_MM * POINTS_PER_MM

    /** 纸宽对应的列数（半角字符当量）：58mm→24，80mm→33，其余按比例换算。 */
    fun columns(widthMm: Int): Int {
        val width = widthMm.coerceAtLeast(1)
        return max(1, (width * COLUMNS_AT_58MM / SlipWidth.MM58.millimetres.toFloat()).toInt())
    }

    /** 行高（点）：估算与绘制共用。 */
    fun rowHeightPoints(sizeSp: Float): Float = sizeSp.coerceAtLeast(1f) * LINE_HEIGHT_FACTOR

    /** 行高（mm）。 */
    fun rowHeightMm(sizeSp: Float): Float = rowHeightPoints(sizeSp) * MM_PER_POINT

    /** 行内基线偏移（点）。 */
    fun baselinePoints(sizeSp: Float): Float = sizeSp.coerceAtLeast(1f) * BASELINE_FACTOR

    /** mm → mil，供 `PrintAttributes.MediaSize` 使用；1mm = 1000/25.4 mil。 */
    fun milsFromMm(mm: Int): Int = (mm * MILS_PER_MM).roundToInt()

    /** mm → 点，供 PDF 画布使用。 */
    fun pointsFromMm(mm: Int): Float = mm * POINTS_PER_MM

    /**
     * 按列宽把文本折成若干行。
     *
     * 中文/全角字符按 2 个半角宽度估算，其余按 1；`\n` 强制换行，空文本算一个空行。
     */
    fun wrap(
        text: String,
        columns: Int,
    ): List<String> {
        val limit = columns.coerceAtLeast(1)
        val rows = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            if (paragraph.isEmpty()) {
                rows += ""
            } else {
                var current = StringBuilder()
                var width = 0
                paragraph.forEach { ch ->
                    val charWidth = charWidth(ch)
                    if (width + charWidth > limit && current.isNotEmpty()) {
                        rows += current.toString()
                        current = StringBuilder()
                        width = 0
                    }
                    current.append(ch)
                    width += charWidth
                }
                if (current.isNotEmpty()) {
                    rows += current.toString()
                }
            }
        }
        return rows.ifEmpty { listOf("") }
    }

    /** 逻辑行在给定列宽下实际占用的行数。 */
    fun rowCount(
        lines: List<SlipLine>,
        columns: Int,
    ): Int {
        val limit = columns.coerceAtLeast(1)
        return lines.sumOf { wrap(it.text, limit).size }
    }

    /** 估算纸高（mm）：折行后行高之和 + 上下边距，clamp 到 40..1200。 */
    fun heightMm(
        widthMm: Int,
        lines: List<SlipLine>,
    ): Int {
        val limit = columns(widthMm)
        val content =
            lines.sumOf { line ->
                wrap(line.text, limit).size * rowHeightMm(line.sizeSp).toDouble()
            }
        val total = ceil(content + 2 * MARGIN_MM.toDouble()).toInt()
        return total.coerceIn(MIN_HEIGHT_MM, MAX_HEIGHT_MM)
    }

    /** 生成排版计划；纸宽收敛到 20..200mm。 */
    fun plan(
        widthMm: Int,
        lines: List<SlipLine>,
    ): SlipRenderPlan {
        val width = widthMm.coerceIn(MIN_WIDTH_MM, MAX_WIDTH_MM)
        return SlipRenderPlan(width, heightMm(width, lines), lines)
    }

    /** 生成排版计划（按 [SlipWidth]）。 */
    fun plan(
        width: SlipWidth,
        lines: List<SlipLine>,
    ): SlipRenderPlan = plan(width.millimetres, lines)

    /** 半角字符宽度当量：中日韩与全角字符算 2，其余算 1。 */
    fun charWidth(ch: Char): Int = if (isWide(ch)) 2 else 1

    private fun isWide(ch: Char): Boolean {
        val code = ch.code
        return code in 0x1100..0x115F ||
            code in 0x2E80..0xA4CF ||
            code in 0xAC00..0xD7A3 ||
            code in 0xF900..0xFAFF ||
            code in 0xFE30..0xFE4F ||
            code in 0xFF00..0xFF60 ||
            code in 0xFFE0..0xFFE6
    }
}

/**
 * 纯 JVM 可测的小票指纹，用于单测断言与重打比对（不参与绘制）。
 *
 * [lineCount] 是逻辑行数，[rowCount] 是按纸宽折行后的实际行数。
 */
object SlipChecksum {
    private const val FNV_OFFSET_BASIS: Int = -2128831035 // 0x811C9DC5
    private const val FNV_PRIME: Int = 0x01000193

    fun lineCount(plan: SlipRenderPlan): Int = plan.lines.size

    fun rowCount(plan: SlipRenderPlan): Int = SlipLayout.rowCount(plan.lines, SlipLayout.columns(plan.widthMm))

    /** FNV-1a 32 位指纹：含纸宽、纸高与每行的字重/字号/文本。 */
    fun fingerprint(plan: SlipRenderPlan): String {
        val payload =
            buildString {
                append(plan.widthMm).append('x').append(plan.heightMm)
                plan.lines.forEach { line ->
                    append('|').append(if (line.bold) '1' else '0')
                    append(line.sizeSp).append('|').append(line.text)
                }
            }
        var hash = FNV_OFFSET_BASIS
        payload.forEach { ch ->
            hash = hash xor ch.code
            hash *= FNV_PRIME
        }
        return hash.toUInt().toString(16).padStart(8, '0')
    }
}
