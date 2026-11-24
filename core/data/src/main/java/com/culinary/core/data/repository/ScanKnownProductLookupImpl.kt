package com.culinary.core.data.repository

import com.culinary.core.data.file.ImageFileStorage
import com.culinary.core.database.dao.BarcodeDao
import com.culinary.core.database.dao.ImageAssetDao
import com.culinary.core.database.dao.ProductDao
import com.culinary.core.database.dao.UnitDao
import com.culinary.core.database.model.ImageOwnerTypeDb
import com.culinary.core.database.model.ImageRoleDb
import com.culinary.core.domain.scan.ScanKnownProduct
import com.culinary.core.domain.scan.ScanKnownProductLookup
import javax.inject.Inject
import javax.inject.Singleton

/** 本地条码 → 商品档案只读查询，不触碰库存表。 */
@Singleton
class ScanKnownProductLookupImpl
    @Inject
    constructor(
        private val barcodeDao: BarcodeDao,
        private val productDao: ProductDao,
        private val unitDao: UnitDao,
        private val imageAssetDao: ImageAssetDao,
        private val imageFileStorage: ImageFileStorage,
    ) : ScanKnownProductLookup {
        override suspend fun findByBarcode(code: String): ScanKnownProduct? {
            val barcode = barcodeDao.findByCode(code) ?: return null
            val product = productDao.findById(barcode.productId) ?: return null
            if (product.deletedAt != null || product.status.name != "ACTIVE") return null
            val image =
                imageAssetDao.listByOwner(ImageOwnerTypeDb.PRODUCT.name, product.id)
                    .firstOrNull { it.role == ImageRoleDb.PRIMARY && it.deletedAt == null }
            val imagePath = image?.let { imageFileStorage.resolve(it.relativePath)?.absolutePath }
            val contentUnit = unitDao.findById(product.contentUnitId)?.code ?: product.contentUnitId
            val quantity = java.math.BigDecimal.valueOf(product.contentQty).stripTrailingZeros().toPlainString()
            return ScanKnownProduct(
                productId = product.id,
                name = product.name,
                specification = product.packageLabel?.takeIf { it.isNotBlank() }
                    ?: "$quantity $contentUnit",
                imagePath = imagePath,
            )
        }
    }
