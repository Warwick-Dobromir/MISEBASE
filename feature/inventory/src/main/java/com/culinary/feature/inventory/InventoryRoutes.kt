package com.culinary.feature.inventory

/** 库存模块内的路由契约；底部一级入口仍由 app 的 N1 导航持有。 */
object InventoryRoutes {
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

    fun product(productId: String): String = "inventory/product/$productId"

    fun ledger(): String = LEDGER

    fun receipt(): String = "inventory/movement/$MODE_RECEIPT"

    fun issue(batchId: String? = null): String =
        if (batchId == null) {
            "inventory/movement/$MODE_ISSUE"
        } else {
            "inventory/movement/$MODE_ISSUE?batchId=$batchId"
        }

    fun adjust(batchId: String? = null): String =
        if (batchId == null) {
            "inventory/movement/$MODE_ADJUST"
        } else {
            "inventory/movement/$MODE_ADJUST?batchId=$batchId"
        }

    fun plan(): String = PLAN
}
