package com.culinary.core.domain.usecase.sauce

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.SauceDetail
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceLineView
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.SauceRepository

/**
 * 读取酱汁详情：档案 + 当前版本 + 版本历史 + 组成行标签。
 *
 * 标签解析（食材名称）在用例内完成，UI 不直接访问 Repository。
 */
class GetSauceDetailUseCase(
    private val sauceRepository: SauceRepository,
    private val ingredientRepository: IngredientRepository,
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
        val shown =
            current
                ?: versions.firstOrNull { it.status == VersionStatus.DRAFT }
                ?: versions.firstOrNull()
        val lines =
            shown
                ?.components
                ?.sortedBy { it.lineNo }
                ?.map { component ->
                    SauceLineView(
                        lineNo = component.lineNo,
                        label = labelFor(component.target),
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

            is ComponentTarget.Recipe -> "子菜谱"
            is ComponentTarget.Sauce -> "子酱汁"
        }
}
