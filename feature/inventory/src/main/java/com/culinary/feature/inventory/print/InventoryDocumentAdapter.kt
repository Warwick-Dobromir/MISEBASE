package com.culinary.feature.inventory.print

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.util.Log
import kotlin.math.ceil

/**
 * 把小票排版计划渲染成一页 PDF 交给系统打印框架。
 *
 * 纸张：按 [InventoryDocumentAdapter.attributesFor] 构造自定义 `PrintAttributes.MediaSize`
 * （id = `misebase_<宽>mm`，宽高单位 mil = 1/1000 英寸，1mm = 1000/25.4 mil）作为默认纸张提示，
 * 与 `PrintManager.print` 传入的 attributes 同源。平台公开 API 的 `onLayoutFinished` 只接受
 * [PrintDocumentInfo]（页数与内容类型），纸张尺寸无法由 adapter 回传，因此 PDF 页尺寸严格等于
 * plan 的 mm 尺寸，两者始终一致。
 *
 * 渲染：白底黑字、单列；字体用系统 `Typeface.DEFAULT` / `DEFAULT_BOLD`（中文由系统字体回退）。
 * 只依赖 Android SDK，不申请任何权限，也不使用网络。
 *
 * @param context 只用于取包名做日志 tag；打印能力来自系统 `PrintManager`
 * @param plan 排版计划（纸宽、纸高、逻辑行）
 * @param jobName 打印任务名，同时作为 [PrintDocumentInfo] 的名称
 */
class InventoryDocumentAdapter(
    context: Context,
    private val plan: SlipRenderPlan,
    jobName: String,
) : PrintDocumentAdapter() {
    private val tag: String = "${context.packageName}.print"
    private val displayName: String = jobName.trim().ifEmpty { DEFAULT_JOB_NAME }
    private val paints = HashMap<PaintKey, Paint>()

    /**
     * 排版阶段：只描述文档（一页），纸张沿用 [attributesFor] 的自定义尺寸。
     *
     * `oldAttributes` 的纸张与 plan 不一致时把 `changed` 置为 true，让框架重新请求内容。
     */
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: PrintDocumentAdapter.LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        try {
            val attributes = attributesFor(plan)
            val info =
                PrintDocumentInfo
                    .Builder(displayName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PAGE_COUNT)
                    .build()
            // MediaSize.equals 只比较宽高（mil），正好是"版面是否变化"的判据。
            callback.onLayoutFinished(info, attributes.mediaSize != oldAttributes?.mediaSize)
        } catch (error: RuntimeException) {
            Log.w(tag, "[$displayName] 打印排版失败", error)
            callback.onLayoutFailed(error.message ?: "打印排版失败")
        }
    }

    /**
     * 写出阶段：一页 PDF，尺寸按 plan 的 mm 值换算成点（1/72 英寸）后作为页面大小。
     *
     * `pages` 只用于多页文档的分段写出；本单据永远只有一页，因此忽略并固定返回
     * [PageRange.ALL_PAGES]。直接把 PDF 写进框架给的 fd，不落地临时文件。
     */
    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: PrintDocumentAdapter.WriteResultCallback,
    ) {
        var document: PdfDocument? = null
        try {
            if (cancellationSignal?.isCanceled == true) {
                callback.onWriteCancelled()
                return
            }
            val pdf = PdfDocument()
            document = pdf
            val page = pdf.startPage(pageInfo())
            try {
                drawPage(page.canvas)
            } finally {
                pdf.finishPage(page)
            }
            if (cancellationSignal?.isCanceled == true) {
                callback.onWriteCancelled()
                return
            }
            // AutoCloseOutputStream 拥有这次写出的唯一一次 close；destination 不再重复关闭，
            // 也不额外创建临时文件，异常路径由 finally 统一释放 PdfDocument。
            ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                pdf.writeTo(output)
                output.flush()
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (error: Exception) {
            Log.w(tag, "[$displayName] 生成打印 PDF 失败", error)
            callback.onWriteFailed(error.message ?: "生成打印内容失败")
        } finally {
            document?.close()
        }
    }

    /** 无临时文件与后台线程，无需清理；保留覆写点方便将来扩展。 */
    override fun onFinish() {
        paints.clear()
    }

    private fun pageInfo(): PdfDocument.PageInfo =
        PdfDocument.PageInfo.Builder(pageWidthPoints(), pageHeightPoints(), PAGE_NUMBER).create()

    private fun pageWidthPoints(): Int = ceil(SlipLayout.pointsFromMm(plan.widthMm)).toInt()

    private fun pageHeightPoints(): Int = ceil(SlipLayout.pointsFromMm(plan.heightMm)).toInt()

    /** 逐行绘制：先画白底，再按折行结果逐行 drawText；`alignEnd` 用右边界对齐。 */
    private fun drawPage(canvas: Canvas) {
        val pageWidth = pageWidthPoints().toFloat()
        val pageHeight = pageHeightPoints().toFloat()
        canvas.drawColor(Color.WHITE)
        val margin = SlipLayout.MARGIN_POINTS
        val columns = SlipLayout.columns(plan.widthMm)
        canvas.save()
        // 裁剪到内容区：即使系统字体比估算更宽，也不会画到纸外。
        canvas.clipRect(margin, margin, pageWidth - margin, pageHeight - margin)
        var rowTop = margin
        plan.lines.forEach { line ->
            val paint = paintFor(line)
            SlipLayout.wrap(line.text, columns).forEach { row ->
                val baseline = rowTop + SlipLayout.baselinePoints(line.sizeSp)
                val x = if (line.alignEnd) pageWidth - margin - paint.measureText(row) else margin
                canvas.drawText(row, x, baseline, paint)
                rowTop += SlipLayout.rowHeightPoints(line.sizeSp)
            }
        }
        canvas.restore()
    }

    /** 同一（字重, 字号）复用一个 Paint；PDF 画布单位是点，故 textSize 直接用 sp 数值。 */
    private fun paintFor(line: SlipLine): Paint =
        paints.getOrPut(PaintKey(line.bold, line.sizeSp)) {
            Paint().apply {
                isAntiAlias = true
                color = Color.BLACK
                textSize = line.sizeSp
                typeface = if (line.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        }

    /** Paint 缓存键。 */
    private data class PaintKey(
        val bold: Boolean,
        val sizeSp: Float,
    )

    companion object {
        /** 纸张标签：系统打印预览里展示的纸张名称。 */
        private const val PAPER_LABEL = "MISEBASE"

        /** 任务名为空时的兜底名称（PrintDocumentInfo.Builder 不接受空名）。 */
        private const val DEFAULT_JOB_NAME = "库存单据"

        private const val PAGE_NUMBER = 1
        private const val PAGE_COUNT = 1

        /**
         * 小票纸张对应的打印属性：自定义 MediaSize（宽高单位 mil）+ 无边距 + 黑白。
         *
         * 同一个函数既供 `PrintManager.print` 作为默认纸张提示，也供 [onLayout] 判断版面是否变化，
         * 避免两处各写一份尺寸换算。
         */
        fun attributesFor(plan: SlipRenderPlan): PrintAttributes {
            val mediaSize =
                PrintAttributes.MediaSize(
                    "misebase_${plan.widthMm}",
                    PAPER_LABEL,
                    SlipLayout.milsFromMm(plan.widthMm),
                    SlipLayout.milsFromMm(plan.heightMm),
                )
            return PrintAttributes
                .Builder()
                .setMediaSize(mediaSize)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                .build()
        }
    }
}
