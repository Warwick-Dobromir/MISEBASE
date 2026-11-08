package com.culinary.core.domain.usecase.sauce

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.SauceDetail
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceLineView
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.SauceRepository

/**
 * 读取酱汁详情：档案 + 当前版本 + 版本历史 + 组成行标签。
 *
 * 标签解析在用例内完成，UI 不直接访问 Repository；子菜谱 / 子酱汁目标
 * 同样解析为可读名称，供只读保护提示使用。
 */
class GetSauceDetailUseCase(
    private val sauceRepository: SauceRepository,
    private val ingredientRepository: IngredientRepository,
    private val recipeRepository: RecipeRepository? = null,
) {
    suspend operator fun invoke(sauceId: SauceId): Outcome<SauceDetail> {
        val sauce =
            when (val result = sauceRepository.get(sauceId)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val versions = sauceRepository.listVersions(sauceId)
        val current =
            versions.firstOrNull { it.isCurrent }
                ?: sauceRepository.findCurrentVersion(sauceId)
        // 有草稿时组成行展示草稿内容；当前已发布版本仍由 currentVersion 单独展示。
        val shown =
            versions.firstOrNull { it.status == VersionStatus.DRAFT }
                ?: current
                ?: versions.firstOrNull()
        val lines =
            shown
                ?.components
                ?.sortedBy { it.lineNo }
                ?.map { component ->
                    SauceLineView(
                        lineNo = component.lineNo,
                        label = component.labelOverride ?: labelFor(component.target),
                        quantity = component.quantity,
                        isOptional = component.isOptional,
                    )
                }.orEmpty()
        return Outcome.success(
            SauceDetail(
                sauce = sauce,
                currentVersion = current,
                versions = versions,
                lines = lines,
            ),
        )
    }

    private suspend fun labelFor(target: ComponentTarget): String =
        when (target) {
            is ComponentTarget.Ingredient -> {
                when (val result = ingredientRepository.get(target.id)) {
                    is Outcome.Success -> result.value.name
                    is Outcome.Failure -> "已删除食材"
                }
            }

            is ComponentTarget.Recipe -> {
                val name =
                    (recipeRepository?.get(target.recipeId) as? Outcome.Success)?.value?.name
                        ?: "子菜谱"
                val versionNo = recipeRepository?.getVersion(target.versionId)?.versionNo
                if (versionNo == null) name else "$name（子菜谱 v$versionNo）"
            }

            is ComponentTarget.Sauce -> {
                val name =
                    (sauceRepository.get(target.sauceId) as? Outcome.Success)?.value?.name
                        ?: "子酱汁"
                val versionNo =
                    (sauceRepository.getVersion(target.versionId) as? Outcome.Success)?.value?.versionNo
                if (versionNo == null) name else "$name（子酱汁 v$versionNo）"
            }
        }
}
