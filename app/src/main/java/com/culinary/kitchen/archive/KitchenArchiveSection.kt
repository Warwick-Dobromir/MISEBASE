package com.culinary.kitchen.archive

import androidx.annotation.StringRes
import com.culinary.R
import com.culinary.core.data.kitchen.KitchenArchiveKind
import com.culinary.core.data.kitchen.KitchenReferenceType
import com.culinary.kitchen.KitchenCategory

/** 厨房可见资料库仅保留酱汁、食材、菜谱。 */
enum class KitchenArchiveSection(
    val routeValue: String,
    val kind: KitchenArchiveKind,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @StringRes val searchHintRes: Int,
    @StringRes val newLabelRes: Int,
) {
    SAUCES(
        routeValue = "sauces",
        kind = KitchenArchiveKind.SAUCE,
        titleRes = R.string.kitchen_library_title_sauces,
        subtitleRes = R.string.kitchen_library_subtitle_sauces,
        searchHintRes = R.string.kitchen_library_search_sauces,
        newLabelRes = R.string.kitchen_library_new_sauce,
    ),
    INGREDIENTS(
        routeValue = "ingredients",
        kind = KitchenArchiveKind.INGREDIENT,
        titleRes = R.string.kitchen_library_title_ingredients,
        subtitleRes = R.string.kitchen_library_subtitle_ingredients,
        searchHintRes = R.string.kitchen_library_search_ingredients,
        newLabelRes = R.string.kitchen_library_new_ingredient,
    ),
    RECIPES(
        routeValue = "recipes",
        kind = KitchenArchiveKind.RECIPE,
        titleRes = R.string.kitchen_library_title_recipes,
        subtitleRes = R.string.kitchen_library_subtitle_recipes,
        searchHintRes = R.string.kitchen_library_search_recipes,
        newLabelRes = R.string.kitchen_library_new_recipe,
    ),
    ;

    companion object {
        fun fromRoute(value: String?): KitchenArchiveSection? = entries.firstOrNull { it.routeValue == value }
    }
}

@StringRes
fun KitchenArchiveKind.typeLabelRes(): Int =
    when (this) {
        KitchenArchiveKind.SAUCE -> R.string.kitchen_type_sauce
        KitchenArchiveKind.INGREDIENT -> R.string.kitchen_type_ingredient
        KitchenArchiveKind.RECIPE -> R.string.kitchen_type_recipe
        KitchenArchiveKind.TECHNIQUE -> R.string.kitchen_type_technique
        KitchenArchiveKind.PRODUCTION_STANDARD -> R.string.kitchen_type_production_standard
        KitchenArchiveKind.STORAGE_STANDARD -> R.string.kitchen_type_storage_standard
    }

@StringRes
fun KitchenReferenceType.typeLabelRes(): Int =
    when (this) {
        KitchenReferenceType.TECHNIQUE -> R.string.kitchen_type_technique
        KitchenReferenceType.PRODUCTION_STANDARD -> R.string.kitchen_type_production_standard
        KitchenReferenceType.STORAGE_STANDARD -> R.string.kitchen_type_storage_standard
    }

fun KitchenReferenceType.archiveKind(): KitchenArchiveKind =
    when (this) {
        KitchenReferenceType.TECHNIQUE -> KitchenArchiveKind.TECHNIQUE
        KitchenReferenceType.PRODUCTION_STANDARD -> KitchenArchiveKind.PRODUCTION_STANDARD
        KitchenReferenceType.STORAGE_STANDARD -> KitchenArchiveKind.STORAGE_STANDARD
    }

fun KitchenArchiveKind.toReferenceType(): KitchenReferenceType? =
    when (this) {
        KitchenArchiveKind.TECHNIQUE -> KitchenReferenceType.TECHNIQUE
        KitchenArchiveKind.PRODUCTION_STANDARD -> KitchenReferenceType.PRODUCTION_STANDARD
        KitchenArchiveKind.STORAGE_STANDARD -> KitchenReferenceType.STORAGE_STANDARD
        KitchenArchiveKind.SAUCE,
        KitchenArchiveKind.INGREDIENT,
        KitchenArchiveKind.RECIPE,
        -> null
    }

fun KitchenCategory.toArchiveSection(): KitchenArchiveSection =
    when (this) {
        KitchenCategory.SAUCES -> KitchenArchiveSection.SAUCES
        KitchenCategory.INGREDIENTS -> KitchenArchiveSection.INGREDIENTS
        KitchenCategory.RECIPES -> KitchenArchiveSection.RECIPES
    }

/** 菜谱分类在数据层保存为枚举名，展示时统一映射为中文。 */
@StringRes
fun recipeCategoryLabelRes(raw: String?): Int? =
    when (raw) {
        "STOCK" -> R.string.kitchen_recipe_category_stock
        "PREP" -> R.string.kitchen_recipe_category_prep
        "COMPONENT" -> R.string.kitchen_recipe_category_component
        "DISH" -> R.string.kitchen_recipe_category_dish
        "OTHER" -> R.string.kitchen_recipe_category_other
        else -> null
    }
