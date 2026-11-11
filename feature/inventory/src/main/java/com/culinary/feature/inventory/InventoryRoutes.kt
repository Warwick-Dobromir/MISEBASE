package com.culinary.feature.inventory

/**
 * 库存模块路由契约（v9 档案化）。
 *
 * 旧路由（overview/product/ledger/movement/plan）继续保留，供需求计划等既有入口使用；
 * 新页面全部围绕库存档案、批次与出库单。
 */
object InventoryRoutes {
    // ---- 旧路由（保留，不删除既有功能）----
    const val OVERVIEW = "inventory"
    const val PRODUCT = "inventory/product/{productId}"
    const val LEDGER = "inventory/ledger"
    const val MOVEMENT = "inventory/movement/{mode}?batchId={batchId}"
    const val PLAN = "inventory/plan"

    const val ARG_PRODUCT_ID = "productId"
    const val ARG_MODE = "mode"
    const val ARG_BATCH_ID = "batchId"

    const val MODE_RECEIPT = "receipt"
    const val MODE_ISSUE = "issue"
    const val MODE_ADJUST = "adjust"

    // ---- v9 新路由 ----
    const val CATEGORY = "inventory/category?categoryId={categoryId}"
    const val CATEGORY_MANAGE = "inventory/categories"
    const val ARCHIVES = "inventory/archives?filter={filter}"
    const val ARCHIVE = "inventory/archive/{archiveId}"
    const val ARCHIVE_EDIT = "inventory/archive/edit?archiveId={archiveId}&kind={kind}&sauceId={sauceId}&name={name}&barcode={barcode}"
    const val BATCHES = "inventory/archive/{archiveId}/batches"
    const val BATCH = "inventory/batch/{batchId}"
    const val RECEIPT = "inventory/receipt?archiveId={archiveId}"
    const val PURCHASE = "inventory/purchase"
    const val ISSUE = "inventory/issue?archiveId={archiveId}&recipeVersionId={recipeVersionId}"
    const val STOCKTAKE = "inventory/stocktake?scope={scope}&scopeRef={scopeRef}"
    const val FREEZE = "inventory/freeze"
    const val TEMPORARY = "inventory/temporary"
    const val LOCATIONS = "inventory/locations"
    const val LEDGER_V9 = "inventory/ledger-v9"
    const val RECYCLE = "inventory/recycle"
    const val RULES = "inventory/rules"
    const val MANAGEMENT = "inventory/management"
    const val PRINT_PREVIEW = "inventory/print/{kind}/{refId}?width={width}"

    const val ARG_CATEGORY_ID = "categoryId"
    const val ARG_FILTER = "filter"
    const val ARG_ARCHIVE_ID = "archiveId"
    const val ARG_KIND = "kind"
    const val ARG_SAUCE_ID = "sauceId"
    const val ARG_RECIPE_VERSION_ID = "recipeVersionId"
    const val ARG_SCOPE = "scope"
    const val ARG_SCOPE_REF = "scopeRef"
    const val ARG_PRINT_KIND = "kind"
    const val ARG_REF_ID = "refId"
    const val ARG_WIDTH = "width"
    const val ARG_NAME = "name"
    const val ARG_BARCODE = "barcode"

    fun product(productId: String): String = "inventory/product/$productId"

    fun ledger(): String = LEDGER

    fun receiptLegacy(): String = "inventory/movement/$MODE_RECEIPT"

    fun issueLegacy(batchId: String? = null): String =
        if (batchId == null) "inventory/movement/$MODE_ISSUE" else "inventory/movement/$MODE_ISSUE?batchId=$batchId"

    fun adjustLegacy(batchId: String? = null): String =
        if (batchId == null) "inventory/movement/$MODE_ADJUST" else "inventory/movement/$MODE_ADJUST?batchId=$batchId"

    fun plan(): String = PLAN

    fun home(): String = OVERVIEW

    fun category(categoryId: String? = null): String =
        if (categoryId == null) "inventory/category" else "inventory/category?categoryId=$categoryId"

    fun categories(): String = CATEGORY_MANAGE

    fun archives(filter: String = "ALL"): String = "inventory/archives?filter=$filter"

    fun archive(archiveId: String): String = "inventory/archive/$archiveId"

    fun archiveNew(
        kind: String = "INGREDIENT",
        sauceId: String? = null,
        name: String? = null,
        barcode: String? = null,
    ): String =
        "inventory/archive/edit?archiveId=&kind=$kind" +
            (sauceId?.let { "&sauceId=$it" } ?: "") +
            (name?.let { "&name=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: "") +
            (barcode?.let { "&barcode=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: "")

    fun archiveEdit(archiveId: String): String =
        "inventory/archive/edit?archiveId=$archiveId&kind=&sauceId=&name=&barcode="

    fun batches(archiveId: String): String = "inventory/archive/$archiveId/batches"

    fun batch(batchId: String): String = "inventory/batch/$batchId"

    fun receipt(archiveId: String? = null): String =
        if (archiveId == null) "inventory/receipt" else "inventory/receipt?archiveId=$archiveId"

    fun purchase(): String = PURCHASE

    fun issue(archiveId: String? = null, recipeVersionId: String? = null): String =
        "inventory/issue" +
            (archiveId?.let { "?archiveId=$it" } ?: "?") +
            (recipeVersionId?.let { "&recipeVersionId=$it" } ?: "")

    fun stocktake(scope: String = "ALL", scopeRef: String? = null): String =
        "inventory/stocktake?scope=$scope" + (scopeRef?.let { "&scopeRef=$it" } ?: "")

    fun freeze(): String = FREEZE

    fun temporary(): String = TEMPORARY

    fun locations(): String = LOCATIONS

    fun ledgerV9(): String = LEDGER_V9

    fun recycle(): String = RECYCLE

    fun rules(): String = RULES

    fun management(): String = MANAGEMENT

    fun printPreview(kind: String, refId: String, widthMm: Int = 80): String =
        "inventory/print/$kind/$refId?width=$widthMm"
}
