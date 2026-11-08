package com.culinary.core.domain.repository

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.SauceImage
import com.culinary.core.domain.model.SauceImageOwner
import com.culinary.core.domain.model.SauceImageRole

/** 酱汁图片读写端口；复用既有 image_asset 与私有目录存储。 */
interface SauceImageRepository {
    suspend fun listForSauce(sauceId: String): List<SauceImage>

    suspend fun listForVersion(versionId: String): List<SauceImage>

    suspend fun add(
        owner: SauceImageOwner,
        ownerId: String,
        role: SauceImageRole,
        stepNo: Int?,
        bytes: ByteArray,
        mimeType: String,
    ): Outcome<SauceImage>

    suspend fun delete(imageId: String): Outcome<Unit>

    suspend fun read(imageId: String): ByteArray?

    /** 步骤拖动后把已持久化图片的 step_no 同步到新序号。 */
    suspend fun updateStepNumbers(images: List<Pair<String, Int>>): Outcome<Unit>
}
