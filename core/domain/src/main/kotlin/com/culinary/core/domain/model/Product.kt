package com.culinary.core.domain.model

import java.util.zip.CRC32

/**
 * 商品（采购 SKU，A06 §5.2）的库存视图。
 *
 * 商品属于一个食材；库存按 [stockUnit] 记账，[contentQty] × [contentUnit]
 * 描述包装内容量，[stockToContentFactor] 用于库存单位与内容量互换。
 */
data class Product(
    val id: ProductId,
    val ingredientId: IngredientId,
    val name: String,
    val stockUnit: Unit,
    val contentQty: Double,
    val contentUnit: Unit,
    val stockToContentFactor: Double,
    val defaultLocationId: LocationId? = null,
    val status: EntityStatus = EntityStatus.ACTIVE,
    val brand: String? = null,
    val sku: String? = null,
    val packageLabel: String? = null,
    val supplierId: String? = null,
    val shelfLifeDays: Int? = null,
    val storageNote: String? = null,
    val lastUnitPriceMinor: Long? = null,
    val currencyCode: String = "CNY",
) {
    init {
        require(name.isNotBlank()) { "product name must not be blank" }
        require(contentQty > 0.0 && contentQty.isFinite()) {
            "product contentQty must be finite and > 0, was $contentQty"
        }
        require(stockToContentFactor > 0.0 && stockToContentFactor.isFinite()) {
            "stockToContentFactor must be finite and > 0, was $stockToContentFactor"
        }
    }

    val isActive: Boolean get() = status == EntityStatus.ACTIVE
}

object ProductSkuGenerator {
    fun generate(category: String?, productId: String): String {
        val categoryName = category?.trim()?.takeIf(String::isNotEmpty) ?: "UNCATEGORIZED"
        val checksum = CRC32().apply { update(categoryName.toByteArray(Charsets.UTF_8)) }.value
        val categoryCode = checksum.toString(16).uppercase().padStart(8, '0')
        val productCode = productId.filter(Char::isLetterOrDigit).uppercase().takeLast(8).ifBlank { "UNKNOWN" }
        return "SKU-$categoryCode-$productCode"
    }
}

/** 储存位置（A06 §6.1）的库存视图。 */
data class StorageLocation(
    val id: LocationId,
    val name: String,
    val parentId: LocationId? = null,
    val status: EntityStatus = EntityStatus.ACTIVE,
) {
    init {
        require(name.isNotBlank()) { "storage location name must not be blank" }
    }

    val isActive: Boolean get() = status == EntityStatus.ACTIVE
}
