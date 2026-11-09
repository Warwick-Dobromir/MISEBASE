package com.culinary.core.domain.usecase.archive

import com.culinary.core.domain.model.ArchiveImage
import com.culinary.core.domain.model.ArchiveImageOwner
import com.culinary.core.domain.repository.ArchiveImageRepository

class ArchiveImageUseCase(
    private val repository: ArchiveImageRepository? = null,
) {
    suspend fun list(
        owner: ArchiveImageOwner,
        ownerId: String,
    ): List<ArchiveImage> = repository?.list(owner, ownerId).orEmpty()

    suspend fun read(imageId: String): ByteArray? = repository?.read(imageId)

    suspend fun primary(
        owner: ArchiveImageOwner,
        ownerId: String,
    ): ByteArray? {
        val images = repository?.list(owner, ownerId).orEmpty()
        val image = images.firstOrNull { it.role == "PRIMARY" } ?: images.firstOrNull()
        return image?.let { repository?.read(it.id) }
    }

    /** 步骤图片：stepNo（1 起）→ 原始字节；无图片的步骤不返回键。 */
    suspend fun stepImages(
        owner: ArchiveImageOwner,
        ownerId: String,
    ): Map<Int, ByteArray> {
        val images = repository?.list(owner, ownerId).orEmpty()
        val result = linkedMapOf<Int, ByteArray>()
        images.forEach { image ->
            val stepNo = image.stepNo ?: return@forEach
            if (stepNo <= 0 || result.containsKey(stepNo)) return@forEach
            val bytes = repository?.read(image.id) ?: return@forEach
            result[stepNo] = bytes
        }
        return result
    }
}
