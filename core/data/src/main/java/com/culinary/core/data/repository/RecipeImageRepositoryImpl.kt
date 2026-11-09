package com.culinary.core.data.repository

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.data.file.ImageFileStorage
import com.culinary.core.database.dao.ImageAssetDao
import com.culinary.core.database.entity.ImageAssetEntity
import com.culinary.core.database.model.ImageOwnerTypeDb
import com.culinary.core.database.model.ImageRoleDb
import com.culinary.core.domain.model.RecipeImage
import com.culinary.core.domain.model.RecipeImageOwner
import com.culinary.core.domain.model.RecipeImageRole
import com.culinary.core.domain.repository.RecipeImageRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** 菜谱图片实现：复用 image_asset 元数据与 ImageFileStorage 私有文件。 */
@Suppress("TooGenericExceptionCaught")
class RecipeImageRepositoryImpl
    @Inject
    constructor(
        private val imageAssetDao: ImageAssetDao,
        private val imageFileStorage: ImageFileStorage,
        private val clock: Clock,
        private val idGenerator: IdGenerator,
    ) : RecipeImageRepository {
        override suspend fun listForRecipe(recipeId: String): List<RecipeImage> =
            listByOwner(ImageOwnerTypeDb.RECIPE, recipeId)

        override suspend fun listForVersion(versionId: String): List<RecipeImage> =
            listByOwner(ImageOwnerTypeDb.RECIPE_VERSION, versionId)

        @Suppress("ReturnCount")
        override suspend fun add(
            owner: RecipeImageOwner,
            ownerId: String,
            role: RecipeImageRole,
            stepNo: Int?,
            bytes: ByteArray,
            mimeType: String,
        ): Outcome<RecipeImage> {
            return try {
                if (bytes.isEmpty()) return invalid("image is empty")
                if (role == RecipeImageRole.PRIMARY && owner != RecipeImageOwner.RECIPE) {
                    return invalid("primary image must belong to recipe")
                }
                if (role == RecipeImageRole.PROCESS && (owner != RecipeImageOwner.RECIPE_VERSION || stepNo == null)) {
                    return invalid("process image must belong to a version step")
                }
                val stored =
                    when (val result = imageFileStorage.store(bytes, mimeType)) {
                        is Outcome.Failure -> return result
                        is Outcome.Success -> result.value
                    }
                val existing = listByOwner(owner.toDb(), ownerId)
                val entity =
                    ImageAssetEntity(
                        id = idGenerator.newId(),
                        ownerType = owner.toDb(),
                        ownerId = ownerId,
                        role = role.toDb(),
                        stepNo = stepNo,
                        relativePath = stored.relativePath,
                        mimeType = mimeType,
                        byteSize = stored.sizeBytes,
                        sha256 = stored.sha256,
                        sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                        createdAt = clock.now().toEpochMilli(),
                    )
                imageAssetDao.insert(entity)
                Outcome.success(entity.toDomain())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(
                    CulinaryError.Data(
                        message = "image metadata write failed",
                        code = ErrorCode.FILE_001,
                        retryable = true,
                        cause = e,
                    ),
                )
            }
        }

        override suspend fun delete(imageId: String): Outcome<Unit> {
            return try {
                val entity =
                    imageAssetDao.findById(imageId)
                        ?: return Outcome.failure(CulinaryError.NotFound(entity = "image_asset", id = imageId))
                imageAssetDao.softDelete(imageId, clock.now().toEpochMilli())
                when (val result = imageFileStorage.delete(entity.relativePath)) {
                    is Outcome.Failure -> result
                    is Outcome.Success -> Outcome.success(Unit)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(
                    CulinaryError.Data(
                        message = "image delete failed",
                        code = ErrorCode.FILE_001,
                        retryable = true,
                        cause = e,
                    ),
                )
            }
        }

        override suspend fun read(imageId: String): ByteArray? {
            val entity = imageAssetDao.findById(imageId)?.takeIf { it.deletedAt == null } ?: return null
            return imageFileStorage.readOrNull(entity.relativePath)
        }

        override suspend fun updateStepNumbers(images: List<Pair<String, Int>>): Outcome<Unit> =
            try {
                images.forEach { (id, stepNo) -> imageAssetDao.updateStepNo(id, stepNo) }
                Outcome.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(
                    CulinaryError.Data(
                        message = "image step order update failed",
                        code = ErrorCode.FILE_001,
                        retryable = true,
                        cause = e,
                    ),
                )
            }

        private suspend fun listByOwner(
            ownerType: ImageOwnerTypeDb,
            ownerId: String,
        ): List<RecipeImage> =
            imageAssetDao
                .listByOwner(ownerType.name, ownerId)
                .map { it.toDomain() }

        private fun invalid(message: String): Outcome<Nothing> =
            Outcome.failure(CulinaryError.Validation(field = "recipe.image", rule = message))

        private fun RecipeImageOwner.toDb(): ImageOwnerTypeDb =
            when (this) {
                RecipeImageOwner.RECIPE -> ImageOwnerTypeDb.RECIPE
                RecipeImageOwner.RECIPE_VERSION -> ImageOwnerTypeDb.RECIPE_VERSION
            }

        private fun RecipeImageRole.toDb(): ImageRoleDb =
            when (this) {
                RecipeImageRole.PRIMARY -> ImageRoleDb.PRIMARY
                RecipeImageRole.PROCESS -> ImageRoleDb.PROCESS
                RecipeImageRole.REFERENCE -> ImageRoleDb.REFERENCE
            }

        private fun ImageAssetEntity.toDomain(): RecipeImage =
            RecipeImage(
                id = id,
                owner =
                    when (ownerType) {
                        ImageOwnerTypeDb.RECIPE -> RecipeImageOwner.RECIPE
                        ImageOwnerTypeDb.RECIPE_VERSION -> RecipeImageOwner.RECIPE_VERSION
                        else -> RecipeImageOwner.RECIPE
                    },
                ownerId = ownerId,
                role =
                    when (role) {
                        ImageRoleDb.PRIMARY -> RecipeImageRole.PRIMARY
                        ImageRoleDb.PROCESS -> RecipeImageRole.PROCESS
                        ImageRoleDb.REFERENCE -> RecipeImageRole.REFERENCE
                    },
                stepNo = stepNo,
                relativePath = relativePath,
                mimeType = mimeType,
                width = width,
                height = height,
                byteSize = byteSize,
                sortOrder = sortOrder,
            )
    }
