package com.culinary.core.domain.model

import java.time.Instant
import java.time.LocalDate

/** 库存档案类型（v9）：普通食材档案，或绑定酱汁档案的现货档案。 */
enum class ArchiveKind {
    INGREDIENT,
    SAUCE,
}

/** 库存档案中记录的默认保质期单位；保留原始单位，不提前换算成天数。 */
enum class DefaultShelfLifeUnit(val label: String) {
    DAY("天"),
    MONTH("个月"),
    YEAR("年"),
}

/** 库存状态：正常 / 低库存 / 不足。阈值来自档案的最低库存。 */
enum class StockStatus {
    NORMAL,
    LOW,
    OUT,
}

/** 库存档案（v9）：描述"这是什么"，数量由批次与流水决定。 */
data class InventoryArchive(
    val id: String,
    val name: String,
    val englishName: String? = null,
    val categoryId: String? = null,
    val kind: ArchiveKind = ArchiveKind.INGREDIENT,
    /** 酱汁现货档案绑定的酱汁；普通食材为 null。 */
    val sauceId: String? = null,
    val stockUnit: Unit,
    /** 每一个库存单位占用的容量单位（CU），必须为正数。 */
    val capacityUnitPerStockUnit: Double = 1.0,
    val minimumStock: Double? = null,
    val targetStock: Double? = null,
    val imageUri: String? = null,
    val storageMethod: String? = null,
    val storageTemperature: String? = null,
    val defaultLocationId: LocationId? = null,
    val description: String? = null,
    /** Archive-level product identity fields, independent of optional purchase SKU links. */
    val brand: String? = null,
    val productSpecification: String? = null,
    val productCode: String? = null,
    val barcode: String? = null,
    val purchaseUnitId: String? = null,
    val purchaseSpecification: String? = null,
    val unitConversion: String? = null,
    val measurementPrecision: Int? = null,
    val batchManagementEnabled: Boolean = true,
    val defaultShelfLifeValue: Int? = null,
    val defaultShelfLifeUnit: DefaultShelfLifeUnit? = null,
    val shelfLifeEnabled: Boolean = false,
    val shelfLifeCalculationMethod: String? = null,
    val shelfLifeDays: Int? = null,
    val openedShelfLifeDays: Int? = null,
    val trackOpenedDate: Boolean = false,
    val supplierName: String? = null,
    val purchasePriceMinor: Long? = null,
    val purchasePriceUnit: String? = null,
    val status: EntityStatus = EntityStatus.ACTIVE,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val revision: Int = 1,
) {
    init {
        require(capacityUnitPerStockUnit.isFinite() && capacityUnitPerStockUnit > 0.0) {
            "capacityUnitPerStockUnit must be finite and positive"
        }
    }

    fun statusFor(available: Double): StockStatus =
        when {
            available <= EPSILON -> StockStatus.OUT
            minimumStock != null && available < minimumStock -> StockStatus.LOW
            else -> StockStatus.NORMAL
        }

    companion object {
        const val EPSILON = 1e-9
    }
}

/** 档案汇总行：总量、批次数、最近到期与库位分布，均在读取时计算。 */
data class InventoryArchiveRow(
    val archive: InventoryArchive,
    val onHand: Double,
    val reserved: Double,
    val batchCount: Int,
    val nearestExpiry: LocalDate? = null,
    val locationNames: List<String> = emptyList(),
    val categoryName: String? = null,
) {
    val available: Double get() = onHand - reserved

    val status: StockStatus get() = archive.statusFor(available)
}

/** 档案关联的采购商品；`conversionRatio` 为 1 个商品折算的库存单位数。 */
data class ArchiveProductLink(
    val product: Product,
    val conversionRatio: Double,
    val isDefault: Boolean,
)

/** 库存首页概况：总数与三档状态，数字来自真实库存。 */
data class InventoryHomeSummary(
    val total: Int,
    val normal: Int,
    val low: Int,
    val out: Int,
)

/** 分类节点：直接档案数与含后代档案数分开统计。 */
data class InventoryCategoryNode(
    val category: ArchiveCategory,
    val childCount: Int,
    val directArchiveCount: Int,
    val totalArchiveCount: Int,
)

/** 档案详情聚合：批次、流水、采购商品、分类与关联菜谱。 */
data class InventoryArchiveDetail(
    val archive: InventoryArchive,
    val row: InventoryArchiveRow,
    val categoryPath: String?,
    val links: List<ArchiveProductLink>,
    val batches: List<InventoryBatchRecord>,
    val ledger: List<InventoryLedgerEntry>,
    val relatedRecipeNames: List<String>,
    val sauceName: String? = null,
)

/** 档案列表筛选（对应页面"全部/正常/低库存/不足"与搜索）。 */
enum class ArchiveListFilter {
    ALL,
    NORMAL,
    LOW,
    OUT,
}
