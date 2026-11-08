package com.culinary.core.domain.model

/** 酱汁图片归属；SAUCE 用于成品图/研发照片，SAUCE_VERSION 用于步骤图。 */
enum class SauceImageOwner {
    SAUCE,
    SAUCE_VERSION,
}

enum class SauceImageRole {
    PRIMARY,
    PROCESS,
    REFERENCE,
}

/** 已持久化的酱汁图片元数据；文件本体由数据层私有目录保存。 */
data class SauceImage(
    val id: String,
    val owner: SauceImageOwner,
    val ownerId: String,
    val role: SauceImageRole,
    val stepNo: Int? = null,
    val relativePath: String,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    val byteSize: Long,
    val sortOrder: Int = 0,
)

/** 从系统选择器读入、尚未写入磁盘的图片。 */
data class PendingSauceImage(
    val bytes: ByteArray,
    val mimeType: String,
)
