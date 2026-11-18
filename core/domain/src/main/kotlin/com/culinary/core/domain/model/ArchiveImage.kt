package com.culinary.core.domain.model

/**
 * 档案图片归属；只读，不改变既有 owner 枚举语义。
 *
 * `SAUCE*` 在酱汁详情与制作模式读取，`RECIPE*` 在菜谱侧读取；
 * 参考资料不在此列（仍有独立约束）。
 */
enum class ArchiveImageOwner {
    INGREDIENT,
    RECIPE,
    RECIPE_VERSION,
    SAUCE,
    SAUCE_VERSION,
}

data class ArchiveImage(
    val id: String,
    val owner: ArchiveImageOwner,
    val ownerId: String,
    val role: String,
    val sortOrder: Int,
    /** 步骤序号（1 起）；主图/参考图为 null。 */
    val stepNo: Int? = null,
    val mimeType: String? = null,
)

/** 从系统照片选择器读入、尚未写入磁盘的食材图片。 */
data class PendingIngredientImage(val bytes: ByteArray, val mimeType: String)
