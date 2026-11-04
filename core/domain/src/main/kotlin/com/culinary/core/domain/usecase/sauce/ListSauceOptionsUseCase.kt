package com.culinary.core.domain.usecase.sauce

import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.repository.SauceRepository
import kotlinx.coroutines.flow.first

/** 子酱汁选择项：档案 + 当前已发布版本（可为空）。 */
data class SauceOption(
    val sauce: Sauce,
    val currentVersion: SauceVersion?,
)

/** 读取可引用的酱汁与各自当前版本，供菜谱编辑器引用酱汁。 */
class ListSauceOptionsUseCase(
    private val repository: SauceRepository,
) {
    suspend operator fun invoke(): List<SauceOption> =
        repository.observeActive().first().map { sauce ->
            SauceOption(sauce = sauce, currentVersion = repository.findCurrentVersion(sauce.id))
        }
}
