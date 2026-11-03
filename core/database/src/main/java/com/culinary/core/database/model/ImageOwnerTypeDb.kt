package com.culinary.core.database.model

/** 图片归属类型（A06 §2）。 */
enum class ImageOwnerTypeDb {
    INGREDIENT,
    SAUCE,
    SAUCE_VERSION,
    RECIPE,
    RECIPE_VERSION,
    PRODUCT,
    INVENTORY_BATCH,
    PLAN,
    RECEIPT,
}
