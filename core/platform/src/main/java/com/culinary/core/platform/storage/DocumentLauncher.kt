package com.culinary.core.platform.storage

import android.net.Uri

/**
 * SAF 文档选择的 UI 桥接（Activity Result）。
 *
 * `core:platform` 只负责 Uri → 流；真正的 `ActivityResultContracts` 注册由 UI 层
 * 实现本接口后注入（阶段 7 接线）。这样平台层不需要持有 Activity。
 */
interface DocumentLauncher {
    suspend fun createDocument(
        displayName: String,
        mimeType: String,
    ): Uri?

    suspend fun pickDocument(mimeTypes: Array<String>): Uri?
}
