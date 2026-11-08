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
import com.culinary.core.domain.model.SauceImage
import com.culinary.core.domain.model.SauceImageOwner
import com.culinary.core.domain.model.SauceImageRole
import com.culinary.core.domain.repository.SauceImageRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** 酱汁图片实现：image_asset 管元数据，ImageFileStorage 管私有文件。 */
@Suppress("TooGenericExceptionCaught")
class SauceImageRepositoryImpl
    @Inject
    constructor(
        private val imageAssetDao: ImageAssetDao,
        private val imageFileStorage: ImageFileStorage,
        private val clock: Clock,
        private val idGenerator: IdGenerator,
    ) : SauceImageRepository {
        override suspend fun listForSauce(sauceId: String): List<SauceImage> =
            listByOwner(ImageOwnerTypeDb.SAUCE, sauceId)

        override suspend fun listForVersion(versionId: String): List<SauceImage> =
            listByOwner(ImageOwnerTypeDb.SAUCE_VERSION, versionId)

        @Suppress("ReturnCount") // 先校验 owner/role，再写入文件与元数据；失败即返回
        override suspend fun add(
            owner: SauceImageOwner,
            ownerId: String,
            role: SauceImageRole,
            stepNo: Int?,
            bytes: ByteArray,
            mimeType: String,
        ): Outcome<SauceImage> {
            return try {
                if (bytes.isEmpty()) return invalid("image is empty")
                if (role == SauceImageRole.PRIMARY && owner != SauceImageOwner.SAUCE) {
                    return invalid("primary image must belong to sauce")
                }
                if (role == SauceImageRole.PROCESS && (owner != SauceImageOwner.SAUCE_VERSION || stepNo == null)) {
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
        ): List<SauceImage> =
            imageAssetDao
                .listByOwner(ownerType.name, ownerId)
                .map { it.toDomain() }

        private fun invalid(message: String): Outcome<Nothing> =
            Outcome.failure(
                CulinaryError.Validation(
                    field = "sauce.image",
                    rule = message,
                ),
            )

        private fun SauceImageOwner.toDb(): ImageOwnerTypeDb =
            when (this) {
                SauceImageOwner.SAUCE -> ImageOwnerTypeDb.SAUCE
                SauceImageOwner.SAUCE_VERSION -> ImageOwnerTypeDb.SAUCE_VERSION
            }

        private fun SauceImageRole.toDb(): ImageRoleDb =
            when (this) {
                SauceImageRole.PRIMARY -> ImageRoleDb.PRIMARY
                SauceImageRole.PROCESS -> ImageRoleDb.PROCESS
                SauceImageRole.REFERENCE -> ImageRoleDb.REFERENCE
            }

        private fun ImageAssetEntity.toDomain(): SauceImage =
            SauceImage(
                id = id,
                owner =
                    when (ownerType) {
                        ImageOwnerTypeDb.SAUCE -> SauceImageOwner.SAUCE
                        ImageOwnerTypeDb.SAUCE_VERSION -> SauceImageOwner.SAUCE_VERSION
                        else -> SauceImageOwner.SAUCE
                    },
                ownerId = ownerId,
                role =
                    when (role) {
                        ImageRoleDb.PRIMARY -> SauceImageRole.PRIMARY
                        ImageRoleDb.PROCESS -> SauceImageRole.PROCESS
                        ImageRoleDb.REFERENCE -> SauceImageRole.REFERENCE
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
