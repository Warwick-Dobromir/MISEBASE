package com.culinary.settings

import java.util.Locale

/** 字节数的人类可读形式（诊断与备份结果共用）。 */
fun formatBytes(bytes: Long): String =
    when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
