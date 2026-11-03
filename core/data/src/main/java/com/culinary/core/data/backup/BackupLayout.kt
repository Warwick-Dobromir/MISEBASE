package com.culinary.core.data.backup

import kotlinx.serialization.json.Json

/** 备份归档内部的固定路径与文件名（E05 §3.1）。 */
internal object BackupLayout {
    const val MANIFEST_JSON: String = "manifest.json"
    const val MANIFEST_SHA256: String = "manifest.sha256"
    const val CHECKSUMS_SHA256: String = "checksums.sha256"
    const val DATABASE_DIR: String = "database"
    const val DATABASE_FILE: String = "database/culinary.db"
    const val IMAGES_DIR: String = "images"
}

/** manifest 序列化配置：容忍未来新增字段，但必填字段缺失会解析失败。 */
internal val BackupJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
