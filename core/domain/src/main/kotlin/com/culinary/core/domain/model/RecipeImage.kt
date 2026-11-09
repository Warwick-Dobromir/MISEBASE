package com.culinary.core.domain.model

/** 菜谱图片归属；RECIPE 用于成品图/研发照片，RECIPE_VERSION 用于步骤图。 */
enum class RecipeImageOwner {
    RECIPE,
    RECIPE_VERSION,
}

enum class RecipeImageRole {
    PRIMARY,
    PROCESS,
    REFERENCE,
}

/** 已持久化的菜谱图片元数据；文件本体由数据层私有目录保存。 */
data class RecipeImage(
    val id: String,
    val owner: RecipeImageOwner,
    val ownerId: String,
    val role: RecipeImageRole,
    val stepNo: Int? = null,
    val relativePath: String,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    val byteSize: Long,
    val sortOrder: Int = 0,
)

/** 从系统选择器读入、尚未写入磁盘的菜谱图片。 */
data class PendingRecipeImage(
    val bytes: ByteArray,
    val mimeType: String,
)
