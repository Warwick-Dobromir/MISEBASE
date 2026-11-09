package com.culinary.core.data.repository

import com.culinary.core.data.file.ImageFileStorage
import com.culinary.core.database.dao.ImageAssetDao
import com.culinary.core.database.model.ImageOwnerTypeDb
import com.culinary.core.domain.model.ArchiveImage
import com.culinary.core.domain.model.ArchiveImageOwner
import com.culinary.core.domain.repository.ArchiveImageRepository
import javax.inject.Inject

/** 复用既有 image_asset 与私有文件存储；只读，不改图片管理逻辑。 */
class ArchiveImageRepositoryImpl
    @Inject
    constructor(
        private val imageAssetDao: ImageAssetDao,
        private val imageFileStorage: ImageFileStorage,
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

        private fun ArchiveImageOwner.toDb(): ImageOwnerTypeDb =
            when (this) {
                ArchiveImageOwner.INGREDIENT -> ImageOwnerTypeDb.INGREDIENT
                ArchiveImageOwner.RECIPE -> ImageOwnerTypeDb.RECIPE
                ArchiveImageOwner.RECIPE_VERSION -> ImageOwnerTypeDb.RECIPE_VERSION
                ArchiveImageOwner.SAUCE -> ImageOwnerTypeDb.SAUCE
                ArchiveImageOwner.SAUCE_VERSION -> ImageOwnerTypeDb.SAUCE_VERSION
            }
    }
