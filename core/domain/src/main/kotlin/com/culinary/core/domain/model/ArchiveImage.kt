package com.culinary.core.domain.model

/** 食材/菜谱详情只读图片归属；不扩展到参考资料。 */
enum class ArchiveImageOwner {
    INGREDIENT,
    RECIPE,
    RECIPE_VERSION,
}

data class ArchiveImage(
    val id: String,
    val owner: ArchiveImageOwner,
    val ownerId: String,
    val role: String,
    val sortOrder: Int,
)
