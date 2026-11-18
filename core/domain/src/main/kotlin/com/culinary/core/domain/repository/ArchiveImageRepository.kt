package com.culinary.core.domain.repository

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ArchiveImage
import com.culinary.core.domain.model.ArchiveImageOwner

/** 食材/菜谱详情读取既有 image_asset 的只读端口。 */
interface ArchiveImageRepository {
    suspend fun list(
        owner: ArchiveImageOwner,
        ownerId: String,
    ): List<ArchiveImage>

    suspend fun read(imageId: String): ByteArray?

    suspend fun addIngredient(
        ingredientId: String,
        role: String,
        bytes: ByteArray,
        mimeType: String,
    ): Outcome<ArchiveImage>

    suspend fun delete(imageId: String): Outcome<Unit>
}
