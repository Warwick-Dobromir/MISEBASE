package com.culinary.core.domain.repository

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.RecipeImage
import com.culinary.core.domain.model.RecipeImageOwner
import com.culinary.core.domain.model.RecipeImageRole

/** 菜谱图片读写端口；复用既有 image_asset 与私有目录存储。 */
interface RecipeImageRepository {
    suspend fun listForRecipe(recipeId: String): List<RecipeImage>

    suspend fun listForVersion(versionId: String): List<RecipeImage>

    suspend fun add(
        owner: RecipeImageOwner,
        ownerId: String,
        role: RecipeImageRole,
        stepNo: Int?,
        bytes: ByteArray,
        mimeType: String,
    ): Outcome<RecipeImage>

    suspend fun delete(imageId: String): Outcome<Unit>

    suspend fun read(imageId: String): ByteArray?

    suspend fun updateStepNumbers(images: List<Pair<String, Int>>): Outcome<Unit>
}
