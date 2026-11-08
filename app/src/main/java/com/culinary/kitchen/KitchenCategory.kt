package com.culinary.kitchen

import androidx.annotation.StringRes
import com.culinary.R
import com.culinary.core.data.kitchen.KitchenCounts

/** 厨房首页的两个分组。 */
enum class KitchenSection(
    @StringRes val titleRes: Int,
    @StringRes val eyebrowRes: Int,
) {
    REFERENCES(
        titleRes = R.string.kitchen_group_references,
        eyebrowRes = R.string.kitchen_group_references_eyebrow,
    ),
    STANDARDS(
        titleRes = R.string.kitchen_group_standards,
        eyebrowRes = R.string.kitchen_group_standards_eyebrow,
    ),
}

/** 当前分组下的分类，保持枚举声明顺序。 */
fun KitchenSection.categories(): List<KitchenCategory> = KitchenCategory.entries.filter { it.section == this }

/** 六个分类各自的真实数量来源，不做跨分类混算。 */
enum class KitchenCountSource {
    SAUCES,
    INGREDIENTS,
    RECIPES,
    TECHNIQUES,
    PRODUCTION_STANDARDS,
    STORAGE_STANDARDS,
}

/** 厨房首页六个分类；顺序与任务书一致。 */
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
    TECHNIQUES(
        routeKey = "techniques",
        titleRes = R.string.kitchen_category_techniques,
        subtitleRes = R.string.kitchen_category_techniques_eyebrow,
        section = KitchenSection.REFERENCES,
        countSource = KitchenCountSource.TECHNIQUES,
    ),
    PRODUCTION_STANDARDS(
        routeKey = "production-standards",
        titleRes = R.string.kitchen_category_production_standards,
        subtitleRes = R.string.kitchen_category_production_standards_eyebrow,
        section = KitchenSection.STANDARDS,
        countSource = KitchenCountSource.PRODUCTION_STANDARDS,
    ),
    STORAGE_STANDARDS(
        routeKey = "storage-standards",
        titleRes = R.string.kitchen_category_storage_standards,
        subtitleRes = R.string.kitchen_category_storage_standards_eyebrow,
        section = KitchenSection.STANDARDS,
        countSource = KitchenCountSource.STORAGE_STANDARDS,
    ),
    ;

    companion object {
        fun fromRouteKey(value: String?): KitchenCategory? = entries.firstOrNull { it.routeKey == value }
    }
}

/** 各类别只读取自己的来源，不做跨分类混算。 */
fun KitchenCounts.countFor(category: KitchenCategory): Int =
    when (category.countSource) {
        KitchenCountSource.SAUCES -> sauceCount
        KitchenCountSource.INGREDIENTS -> ingredientCount
        KitchenCountSource.RECIPES -> recipeCount
        KitchenCountSource.TECHNIQUES -> cookingTechniqueCount
        KitchenCountSource.PRODUCTION_STANDARDS -> productionStandardCount
        KitchenCountSource.STORAGE_STANDARDS -> storageStandardCount
    }
