package com.culinary.core.domain.usecase.recipe

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.RecipeComponentKind
import com.culinary.core.domain.model.RecipeComponentView
import com.culinary.core.domain.model.RecipeDetail
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.SauceRepository

/** 读取菜谱详情：档案 + 当前版本 + 版本历史 + 组成行标签与固定版本号。 */
class GetRecipeDetailUseCase(
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val sauceRepository: SauceRepository,
) {
    suspend operator fun invoke(recipeId: RecipeId): Outcome<RecipeDetail> {
        val recipe =
            when (val result = recipeRepository.get(recipeId)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val versions = recipeRepository.listVersions(recipeId)
        val current =
            versions.firstOrNull { it.isCurrent }
                ?: recipeRepository.getCurrentVersion(recipeId)
        // 编辑已发布菜谱时优先展示可编辑草稿，保存后用户能立即看到修改；
        // currentVersion 仍保持"当前发布版本"的指针语义，不被草稿替换。
        val shown =
            versions.firstOrNull { it.status == VersionStatus.DRAFT }
                ?: current
                ?: versions.firstOrNull()
        val components =
            shown
                ?.components
                ?.sortedBy { it.lineNo }
                ?.map { component -> componentView(component) }
                .orEmpty()
        return Outcome.success(
            RecipeDetail(
                recipe = recipe,
                currentVersion = current,
                versions = versions,
                components = components,
                baseServings = shown?.servings ?: 1,
                displayVersion = shown,
            ),
        )
    }

    private suspend fun componentView(component: com.culinary.core.domain.model.RecipeComponent): RecipeComponentView =
        when (val target = component.target) {
            is ComponentTarget.Ingredient -> {
                val name =
                    when (val result = ingredientRepository.get(target.id)) {
                        is Outcome.Success -> result.value.name
                        is Outcome.Failure -> "已删除食材"
                    }
                RecipeComponentView(
                    lineNo = component.lineNo,
                    label = name,
                    kind = RecipeComponentKind.INGREDIENT,
                    quantity = component.quantity,
                    isOptional = component.isOptional,
                    ingredientId = target.id,
                )
            }

            is ComponentTarget.Recipe -> {
                val name =
                    when (val result = recipeRepository.get(target.recipeId)) {
                        is Outcome.Success -> result.value.name
                        is Outcome.Failure -> "子菜谱"
                    }
                RecipeComponentView(
                    lineNo = component.lineNo,
                    label = name,
                    kind = RecipeComponentKind.RECIPE,
                    quantity = component.quantity,
                    isOptional = component.isOptional,
                    referencedVersionNo = recipeRepository.getVersion(target.versionId)?.versionNo,
                    recipeId = target.recipeId,
                    recipeVersionId = target.versionId,
                )
            }

            is ComponentTarget.Sauce -> {
                val name =
                    when (val result = sauceRepository.get(target.sauceId)) {
                        is Outcome.Success -> result.value.name
                        is Outcome.Failure -> "子酱汁"
                    }
                val versionNo =
                    (sauceRepository.getVersion(target.versionId) as? Outcome.Success)?.value?.versionNo
                RecipeComponentView(
                    lineNo = component.lineNo,
                    label = name,
                    kind = RecipeComponentKind.SAUCE,
                    quantity = component.quantity,
                    isOptional = component.isOptional,
                    referencedVersionNo = versionNo,
                    sauceId = target.sauceId,
                    sauceVersionId = target.versionId,
                )
            }
        }
}
