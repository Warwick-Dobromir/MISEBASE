package com.culinary.feature.recipes

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.culinary.core.domain.model.RecipeCategory

/** 分类展示文案；页面只调用本函数，不各自维护映射。 */
@Composable
fun recipeCategoryLabel(category: RecipeCategory): String =
    stringResource(
        when (category) {
            RecipeCategory.STOCK -> R.string.recipes_category_stock
            RecipeCategory.PREP -> R.string.recipes_category_prep
            RecipeCategory.COMPONENT -> R.string.recipes_category_component
            RecipeCategory.DISH -> R.string.recipes_category_dish
            RecipeCategory.OTHER -> R.string.recipes_category_other
        },
    )
