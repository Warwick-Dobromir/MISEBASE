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
import com.culinary.core.domain.model.ArchiveImage
import com.culinary.core.domain.model.ArchiveImageOwner
import com.culinary.core.domain.repository.ArchiveImageRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** 复用既有 image_asset 与私有文件存储；只读，不改图片管理逻辑。 */
class ArchiveImageRepositoryImpl
    @Inject
    constructor(
        private val imageAssetDao: ImageAssetDao,
        private val imageFileStorage: ImageFileStorage,
        private val clock: Clock,
        private val idGenerator: IdGenerator,
    ) : ArchiveImageRepository {
        override suspend fun list(
            owner: ArchiveImageOwner,
            ownerId: String,
        ): List<ArchiveImage> =
            imageAssetDao.listByOwner(owner.toDb().name, ownerId).mapNotNull { entity ->
                if (entity.deletedAt != null) {
                    null
                } else {
                    ArchiveImage(
                        id = entity.id,
                        owner = owner,
                        ownerId = entity.ownerId,
                        role = entity.role.name,
                        sortOrder = entity.sortOrder,
                        stepNo = entity.stepNo,
                    )
                }
            }

        override suspend fun read(imageId: String): ByteArray? {
            val entity = imageAssetDao.findById(imageId)?.takeIf { it.deletedAt == null } ?: return null
            return imageFileStorage.readOrNull(entity.relativePath)
        }

        override suspend fun addPrimaryIngredientImage(
            ingredientId: String,
            bytes: ByteArray,
            mimeType: String,
        ): Outcome<ArchiveImage> {
            return try {
                if (bytes.isEmpty() || mimeType !in setOf("image/jpeg", "image/png", "image/webp")) {
                    return Outcome.failure(CulinaryError.Validation(field = "ingredient.image", rule = "invalid image"))
                }
                val stored = when (val result = imageFileStorage.store(bytes, mimeType)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> result.value
                }
                imageAssetDao.listByOwner(ImageOwnerTypeDb.INGREDIENT.name, ingredientId)
                    .filter { it.role == ImageRoleDb.PRIMARY }
                    .forEach { imageAssetDao.softDelete(it.id, clock.now().toEpochMilli()) }
                val entity = ImageAssetEntity(
                    id = idGenerator.newId(),
                    ownerType = ImageOwnerTypeDb.INGREDIENT,
                    ownerId = ingredientId,
                    role = ImageRoleDb.PRIMARY,
                    relativePath = stored.relativePath,
                    mimeType = mimeType,
                    byteSize = stored.sizeBytes,
                    sha256 = stored.sha256,
                    sortOrder = 0,
                    createdAt = clock.now().toEpochMilli(),
                )
                imageAssetDao.insert(entity)
                Outcome.success(
                    ArchiveImage(
                        id = entity.id,
                        owner = ArchiveImageOwner.INGREDIENT,
                        ownerId = ingredientId,
                        role = ImageRoleDb.PRIMARY.name,
                        sortOrder = 0,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(CulinaryError.Data(
                    message = "ingredient image write failed",
                    code = ErrorCode.FILE_001,
                    retryable = true,
                    cause = e,
                ))
            }
        }

        override suspend fun delete(imageId: String): Outcome<Unit> {
            return try {
                val entity = imageAssetDao.findById(imageId)
                    ?: return Outcome.failure(CulinaryError.NotFound(entity = "image_asset", id = imageId))
                imageAssetDao.softDelete(imageId, clock.now().toEpochMilli())
                imageFileStorage.delete(entity.relativePath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(CulinaryError.Data(
                    message = "ingredient image delete failed",
                    code = ErrorCode.FILE_001,
                    retryable = true,
                    cause = e,
                ))
            }
        }

        private fun ArchiveImageOwner.toDb(): ImageOwnerTypeDb =
            when (this) {
                ArchiveImageOwner.INGREDIENT -> ImageOwnerTypeDb.INGREDIENT
                ArchiveImageOwner.RECIPE -> ImageOwnerTypeDb.RECIPE
                ArchiveImageOwner.RECIPE_VERSION -> ImageOwnerTypeDb.RECIPE_VERSION
                ArchiveImageOwner.SAUCE -> ImageOwnerTypeDb.SAUCE
                ArchiveImageOwner.SAUCE_VERSION -> ImageOwnerTypeDb.SAUCE_VERSION
            }
    }
