package com.culinary.kitchen

import androidx.annotation.StringRes
import com.culinary.R
import com.culinary.core.data.kitchen.KitchenCounts

/** 厨房首页只展示三个核心资料库。 */
enum class KitchenSection(
    @StringRes val titleRes: Int,
    @StringRes val eyebrowRes: Int,
) {
    REFERENCES(
        titleRes = R.string.kitchen_group_references,
        eyebrowRes = R.string.kitchen_group_references_eyebrow,
    ),
}

/** 当前分组下的分类，保持枚举声明顺序。 */
fun KitchenSection.categories(): List<KitchenCategory> = KitchenCategory.entries.filter { it.section == this }

/** 厨房首页三个核心资料库的真实数量来源。 */
enum class KitchenCountSource {
    SAUCES,
    INGREDIENTS,
    RECIPES,
    TECHNIQUES,
    PRODUCTION_STANDARDS,
    STORAGE_STANDARDS,
}

/** 厨房首页只保留酱汁、食材、菜谱三个入口。 */
enum class KitchenCategory(
    val routeKey: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val section: KitchenSection,
    val countSource: KitchenCountSource,
) {
    SAUCES(
        routeKey = "sauces",
        titleRes = R.string.kitchen_category_sauces,
        subtitleRes = R.string.kitchen_category_sauces_eyebrow,
        section = KitchenSection.REFERENCES,
        countSource = KitchenCountSource.SAUCES,
    ),
    INGREDIENTS(
        routeKey = "ingredients",
        titleRes = R.string.kitchen_category_ingredients,
        subtitleRes = R.string.kitchen_category_ingredients_eyebrow,
        section = KitchenSection.REFERENCES,
        countSource = KitchenCountSource.INGREDIENTS,
    ),
    RECIPES(
        routeKey = "recipes",
        titleRes = R.string.kitchen_category_recipes,
        subtitleRes = R.string.kitchen_category_recipes_eyebrow,
        section = KitchenSection.REFERENCES,
        countSource = KitchenCountSource.RECIPES,
    ),
    ;

    companion object {
        fun fromRouteKey(value: String?): KitchenCategory? = entries.firstOrNull { it.routeKey == value }
    }
}

/** 保留既有计数数据结构；首页仅展示三个核心资料库的数量。 */
fun KitchenCounts.countFor(category: KitchenCategory): Int =
    when (category.countSource) {
        KitchenCountSource.SAUCES -> sauceCount
        KitchenCountSource.INGREDIENTS -> ingredientCount
        KitchenCountSource.RECIPES -> recipeCount
        KitchenCountSource.TECHNIQUES -> cookingTechniqueCount
        KitchenCountSource.PRODUCTION_STANDARDS -> productionStandardCount
        KitchenCountSource.STORAGE_STANDARDS -> storageStandardCount
    }
