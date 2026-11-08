package com.culinary.core.domain.repository

import com.culinary.core.domain.model.ArchiveImage
import com.culinary.core.domain.model.ArchiveImageOwner

/** 食材/菜谱详情读取既有 image_asset 的只读端口。 */
interface ArchiveImageRepository {
    suspend fun list(
        owner: ArchiveImageOwner,
        ownerId: String,
    ): List<ArchiveImage>

    suspend fun read(imageId: String): ByteArray?
}
