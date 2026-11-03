package com.culinary.core.data.util

import java.security.MessageDigest

/** SHA-256 工具；备份与图片校验使用小写十六进制（A06 §1.2）。 */
internal object Sha256 {
    fun of(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
