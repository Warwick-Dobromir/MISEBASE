package com.culinary.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.culinary.core.database.converter.CoreConverters
import com.culinary.core.database.converter.CrossCuttingConverters
import com.culinary.core.database.converter.InventoryConverters
import com.culinary.core.database.converter.PlanConverters
import com.culinary.core.database.converter.ProcurementConverters
import com.culinary.core.database.dao.AppMetadataDao
import com.culinary.core.database.dao.BackupRecordDao
import com.culinary.core.database.dao.BarcodeDao
import com.culinary.core.database.dao.CalculationRecordDao
import com.culinary.core.database.dao.ChangeLogDao
import com.culinary.core.database.dao.GoodsReceiptDao
import com.culinary.core.database.dao.GoodsReceiptItemDao
import com.culinary.core.database.dao.ImageAssetDao
import com.culinary.core.database.dao.IngredientAliasDao
import com.culinary.core.database.dao.IngredientDao
import com.culinary.core.database.dao.IngredientUnitConversionDao
import com.culinary.core.database.dao.InventoryBatchDao
import com.culinary.core.database.dao.InventoryLedgerDao
import com.culinary.core.database.dao.InventoryReservationDao
import com.culinary.core.database.dao.MaterialRequirementDao
import com.culinary.core.database.dao.MaterialRequirementSourceDao
import com.culinary.core.database.dao.PlanSnapshotDao
import com.culinary.core.database.dao.ProcurementRequirementDao
import com.culinary.core.database.dao.ProductDao
import com.culinary.core.database.dao.ProductionPlanDao
import com.culinary.core.database.dao.ProductionPlanItemDao
import com.culinary.core.database.dao.PurchaseOrderDao
import com.culinary.core.database.dao.PurchaseOrderItemDao
import com.culinary.core.database.dao.RecipeComponentDao
import com.culinary.core.database.dao.RecipeDao
import com.culinary.core.database.dao.RecipeVersionDao
import com.culinary.core.database.dao.SauceComponentDao
import com.culinary.core.database.dao.SauceDao
import com.culinary.core.database.dao.SauceVersionDao
import com.culinary.core.database.dao.StorageLocationDao
import com.culinary.core.database.dao.SupplierDao
import com.culinary.core.database.dao.UnitDao
import com.culinary.core.database.entity.AppMetadataEntity
import com.culinary.core.database.entity.BackupRecordEntity
import com.culinary.core.database.entity.BarcodeEntity
import com.culinary.core.database.entity.CalculationRecordEntity
import com.culinary.core.database.entity.CalculationRecordLineEntity
import com.culinary.core.database.entity.ChangeLogEntity
import com.culinary.core.database.entity.GoodsReceiptEntity
import com.culinary.core.database.entity.GoodsReceiptItemEntity
import com.culinary.core.database.entity.ImageAssetEntity
import com.culinary.core.database.entity.IngredientAliasEntity
import com.culinary.core.database.entity.IngredientEntity
import com.culinary.core.database.entity.IngredientUnitConversionEntity
import com.culinary.core.database.entity.InventoryBatchEntity
import com.culinary.core.database.entity.InventoryLedgerEntity
import com.culinary.core.database.entity.InventoryReservationEntity
import com.culinary.core.database.entity.MaterialRequirementEntity
import com.culinary.core.database.entity.MaterialRequirementSourceEntity
import com.culinary.core.database.entity.ProcurementRequirementEntity
import com.culinary.core.database.entity.ProductEntity
import com.culinary.core.database.entity.ProductionPlanEntity
import com.culinary.core.database.entity.ProductionPlanItemEntity
import com.culinary.core.database.entity.ProductionPlanSnapshotEntity
import com.culinary.core.database.entity.PurchaseOrderEntity
import com.culinary.core.database.entity.PurchaseOrderItemEntity
import com.culinary.core.database.entity.RecipeComponentEntity
import com.culinary.core.database.entity.RecipeEntity
import com.culinary.core.database.entity.RecipeVersionEntity
import com.culinary.core.database.entity.SauceComponentEntity
import com.culinary.core.database.entity.SauceEntity
import com.culinary.core.database.entity.SauceVersionEntity
import com.culinary.core.database.entity.StorageLocationEntity
import com.culinary.core.database.entity.SupplierEntity
import com.culinary.core.database.entity.UnitEntity

/**
 * CULINARY 数据库（schema v2，A06 + MB-SC-001）。
 *
 * `exportSchema = true`：每个版本必须提交 `schemas/` JSON，迁移测试依赖它。
 * v2 新增计算记录持久化结构，并为 `recipe_version` 增加 `servings`；
 * v1 的 31 张表（含 11 张 DORMANT）结构不改。
 */
@Database(
    entities = [
        UnitEntity::class,
        IngredientEntity::class,
        IngredientAliasEntity::class,
        IngredientUnitConversionEntity::class,
        SauceEntity::class,
        SauceVersionEntity::class,
        SauceComponentEntity::class,
        RecipeEntity::class,
        RecipeVersionEntity::class,
        RecipeComponentEntity::class,
        SupplierEntity::class,
        ProductEntity::class,
        BarcodeEntity::class,
        StorageLocationEntity::class,
        InventoryBatchEntity::class,
        InventoryLedgerEntity::class,
        ProductionPlanEntity::class,
        ProductionPlanItemEntity::class,
        ProductionPlanSnapshotEntity::class,
        MaterialRequirementEntity::class,
        MaterialRequirementSourceEntity::class,
        InventoryReservationEntity::class,
        ProcurementRequirementEntity::class,
        PurchaseOrderEntity::class,
        PurchaseOrderItemEntity::class,
        GoodsReceiptEntity::class,
        GoodsReceiptItemEntity::class,
        ImageAssetEntity::class,
        ChangeLogEntity::class,
        BackupRecordEntity::class,
        AppMetadataEntity::class,
        CalculationRecordEntity::class,
        CalculationRecordLineEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(
    CoreConverters::class,
    InventoryConverters::class,
    PlanConverters::class,
    ProcurementConverters::class,
    CrossCuttingConverters::class,
)
abstract class CulinaryDatabase : RoomDatabase() {
    abstract fun unitDao(): UnitDao

    abstract fun ingredientDao(): IngredientDao

    abstract fun ingredientAliasDao(): IngredientAliasDao

    abstract fun ingredientUnitConversionDao(): IngredientUnitConversionDao

    abstract fun sauceDao(): SauceDao

    abstract fun sauceVersionDao(): SauceVersionDao

    abstract fun sauceComponentDao(): SauceComponentDao

    abstract fun recipeDao(): RecipeDao

    abstract fun recipeVersionDao(): RecipeVersionDao

    abstract fun recipeComponentDao(): RecipeComponentDao

    abstract fun supplierDao(): SupplierDao

    abstract fun productDao(): ProductDao

    abstract fun barcodeDao(): BarcodeDao

    abstract fun storageLocationDao(): StorageLocationDao

    abstract fun inventoryBatchDao(): InventoryBatchDao

    abstract fun inventoryLedgerDao(): InventoryLedgerDao

    abstract fun productionPlanDao(): ProductionPlanDao

    abstract fun productionPlanItemDao(): ProductionPlanItemDao

    abstract fun planSnapshotDao(): PlanSnapshotDao

    abstract fun materialRequirementDao(): MaterialRequirementDao

    abstract fun materialRequirementSourceDao(): MaterialRequirementSourceDao

    abstract fun inventoryReservationDao(): InventoryReservationDao

    abstract fun procurementRequirementDao(): ProcurementRequirementDao

    abstract fun purchaseOrderDao(): PurchaseOrderDao

    abstract fun purchaseOrderItemDao(): PurchaseOrderItemDao

    abstract fun goodsReceiptDao(): GoodsReceiptDao

    abstract fun goodsReceiptItemDao(): GoodsReceiptItemDao

    abstract fun imageAssetDao(): ImageAssetDao

    abstract fun changeLogDao(): ChangeLogDao

    abstract fun backupRecordDao(): BackupRecordDao

    abstract fun appMetadataDao(): AppMetadataDao

    abstract fun calculationRecordDao(): CalculationRecordDao

    companion object {
        const val DATABASE_NAME: String = "culinary.db"
    }
}
