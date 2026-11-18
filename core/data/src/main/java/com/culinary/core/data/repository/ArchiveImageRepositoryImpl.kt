package com.culinary.core.data.repository

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.data.file.ImageFileStorage
import com.culinary.core.database.dao.ChangeLogDao
import com.culinary.core.database.dao.ImageAssetDao
import com.culinary.core.database.entity.ChangeLogEntity
import com.culinary.core.database.entity.ImageAssetEntity
import com.culinary.core.database.model.ImageOwnerTypeDb
import com.culinary.core.database.model.ChangeActionDb
import com.culinary.core.database.model.ImageRoleDb
import com.culinary.core.domain.model.ArchiveImage
import com.culinary.core.domain.model.ArchiveImageOwner
import com.culinary.core.domain.repository.ArchiveImageRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** 食材图片读写：复用既有 image_asset 与私有文件存储。 */
class ArchiveImageRepositoryImpl
    @Inject
    constructor(
        private val imageAssetDao: ImageAssetDao,
        private val changeLogDao: ChangeLogDao,
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
                        mimeType = entity.mimeType,
                    )
                }
            }

        override suspend fun addIngredient(
            ingredientId: String,
            role: String,
            bytes: ByteArray,
            mimeType: String,
        ): Outcome<ArchiveImage> {
            return try {
                if (bytes.isEmpty()) {
                    return Outcome.failure(CulinaryError.Validation("ingredient.image", "image is empty"))
                }
                val imageRole =
                    when (role) {
                        "PRIMARY" -> ImageRoleDb.PRIMARY
                        "REFERENCE" -> ImageRoleDb.REFERENCE
                        else -> {
                            return Outcome.failure(CulinaryError.Validation("ingredient.image", "invalid image role"))
                        }
                    }
                val stored =
                    when (val result = imageFileStorage.store(bytes, mimeType)) {
                        is Outcome.Failure -> return result
                        is Outcome.Success -> result.value
                    }
                val existing = imageAssetDao.listByOwner(ImageOwnerTypeDb.INGREDIENT.name, ingredientId)
                val entity =
                    ImageAssetEntity(
                        id = idGenerator.newId(),
                        ownerType = ImageOwnerTypeDb.INGREDIENT,
                        ownerId = ingredientId,
                        role = imageRole,
                        relativePath = stored.relativePath,
                        mimeType = mimeType,
                        byteSize = stored.sizeBytes,
                        sha256 = stored.sha256,
                        sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                        createdAt = clock.now().toEpochMilli(),
                    )
                imageAssetDao.insert(entity)
                changeLogDao.insert(
                    ChangeLogEntity(
                        id = idGenerator.newId(),
                        entityType = "INGREDIENT",
                        entityId = ingredientId,
                        action = ChangeActionDb.UPDATE,
                        createdAt = entity.createdAt,
                    ),
                )
                Outcome.success(
                    ArchiveImage(
                        id = entity.id,
                        owner = ArchiveImageOwner.INGREDIENT,
                        ownerId = ingredientId,
                        role = entity.role.name,
                        sortOrder = entity.sortOrder,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(
                    CulinaryError.Data(
                        message = "ingredient image write failed",
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
                        ?: return Outcome.failure(CulinaryError.NotFound("image_asset", imageId))
                if (entity.ownerType != ImageOwnerTypeDb.INGREDIENT) {
                    return Outcome.failure(CulinaryError.Validation("ingredient.image", "only ingredient images can be deleted here"))
                }
                imageAssetDao.softDelete(imageId, clock.now().toEpochMilli())
                when (val result = imageFileStorage.delete(entity.relativePath)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> Unit
                }
                changeLogDao.insert(
                    ChangeLogEntity(
                        id = idGenerator.newId(),
                        entityType = "INGREDIENT",
                        entityId = entity.ownerId,
                        action = ChangeActionDb.UPDATE,
                        createdAt = clock.now().toEpochMilli(),
                    ),
                )
                Outcome.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(
                    CulinaryError.Data(
                        message = "ingredient image delete failed",
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

        private fun ArchiveImageOwner.toDb(): ImageOwnerTypeDb =
            when (this) {
                ArchiveImageOwner.INGREDIENT -> ImageOwnerTypeDb.INGREDIENT
                ArchiveImageOwner.RECIPE -> ImageOwnerTypeDb.RECIPE
                ArchiveImageOwner.RECIPE_VERSION -> ImageOwnerTypeDb.RECIPE_VERSION
                ArchiveImageOwner.SAUCE -> ImageOwnerTypeDb.SAUCE
                ArchiveImageOwner.SAUCE_VERSION -> ImageOwnerTypeDb.SAUCE_VERSION
            }
    }
