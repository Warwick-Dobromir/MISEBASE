package com.culinary.core.domain.usecase.sauce

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.SauceImage
import com.culinary.core.domain.model.SauceImageOwner
import com.culinary.core.domain.model.SauceImageRole
import com.culinary.core.domain.repository.SauceImageRepository

/** 酱汁编辑器的图片入口；UI 不直接依赖 DAO / 文件存储。 */
class SauceImageUseCase(
    private val repository: SauceImageRepository,
) {
    suspend fun listForSauce(sauceId: String): List<SauceImage> = repository.listForSauce(sauceId)

    suspend fun listForVersion(versionId: String): List<SauceImage> = repository.listForVersion(versionId)

    suspend fun add(
        owner: SauceImageOwner,
        ownerId: String,
        role: SauceImageRole,
        stepNo: Int?,
        bytes: ByteArray,
        mimeType: String,
    ): Outcome<SauceImage> = repository.add(owner, ownerId, role, stepNo, bytes, mimeType)

    suspend fun delete(imageId: String): Outcome<Unit> = repository.delete(imageId)

    suspend fun read(imageId: String): ByteArray? = repository.read(imageId)

    suspend fun updateStepNumbers(images: List<Pair<String, Int>>): Outcome<Unit> = repository.updateStepNumbers(images)
}
