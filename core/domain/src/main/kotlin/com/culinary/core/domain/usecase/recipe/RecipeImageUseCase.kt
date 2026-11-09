package com.culinary.core.domain.usecase.recipe

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.RecipeImage
import com.culinary.core.domain.model.RecipeImageOwner
import com.culinary.core.domain.model.RecipeImageRole
import com.culinary.core.domain.repository.RecipeImageRepository

/** 菜谱编辑器的图片入口；UI 不直接依赖 DAO / 文件存储。 */
class RecipeImageUseCase(
    private val repository: RecipeImageRepository,
) {
    suspend fun listForRecipe(recipeId: String): List<RecipeImage> = repository.listForRecipe(recipeId)

    suspend fun listForVersion(versionId: String): List<RecipeImage> = repository.listForVersion(versionId)

    suspend fun add(
        owner: RecipeImageOwner,
        ownerId: String,
        role: RecipeImageRole,
        stepNo: Int?,
        bytes: ByteArray,
        mimeType: String,
    ): Outcome<RecipeImage> = repository.add(owner, ownerId, role, stepNo, bytes, mimeType)

    suspend fun delete(imageId: String): Outcome<Unit> = repository.delete(imageId)

    suspend fun read(imageId: String): ByteArray? = repository.read(imageId)

    suspend fun updateStepNumbers(images: List<Pair<String, Int>>): Outcome<Unit> = repository.updateStepNumbers(images)
}
