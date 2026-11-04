package com.culinary.feature.scan

import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.barcode.ScanSource
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 扫码模块内的路由契约（底部一级入口仍由 app 的 N1 导航持有）。
 *
 * 识别结果通过路由参数传给确认页：条码值、格式、来源三项都是导航参数，
 * 值做 URL 编码，避免二维码里的 `/`、`?`、`&`、空格破坏路由匹配。
 */
object ScanRoutes {
    const val HOME: String = "scan"

    const val CONFIRM: String = "scan/confirm?code={code}&format={format}&source={source}"

    const val ARG_CODE: String = "code"
    const val ARG_FORMAT: String = "format"
    const val ARG_SOURCE: String = "source"

    fun confirm(capture: ScanCapture): String =
        "scan/confirm?code=${encode(capture.rawValue)}" +
            "&format=${encode(capture.format.orEmpty())}" +
            "&source=${capture.source.name}"

    /**
     * 从导航参数还原识别结果。
     *
     * Navigation 已经对参数做过 URI 解码，这里只做去空白、空格式归一与来源兜底；
     * 空白或超长条码返回 `null`，由确认页显示"无法读取扫码结果"而不是抛异常。
     */
    fun captureFrom(
        code: String?,
        format: String?,
        source: String?,
    ): ScanCapture? {
        val raw = code?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (raw.length > ScanCapture.MAX_VALUE_LENGTH) return null
        val parsedSource =
            runCatching { ScanSource.valueOf(source.orEmpty().trim().uppercase()) }
                .getOrDefault(ScanSource.CAMERA)
        return ScanCapture(
            rawValue = raw,
            format = format?.trim()?.takeIf { it.isNotEmpty() },
            source = parsedSource,
        )
    }

    private fun encode(value: String): String =
        URLEncoder
            .encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
}
