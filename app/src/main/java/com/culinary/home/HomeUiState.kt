package com.culinary.home

import com.culinary.core.common.CulinaryError
import com.culinary.core.data.home.HomeInventorySummary
import com.culinary.core.data.home.HomeKnowledgeSummary

/**
 * 首页单模块状态。
 *
 * 每个模块独立加载、独立失败：库存读取失败不会替换知识库，反之亦然。
 */
sealed interface HomeSection<out T> {
    data object Loading : HomeSection<Nothing>

    data class Ready<T>(
        val value: T,
    ) : HomeSection<T>

    data class Failed(
        val message: String,
    ) : HomeSection<Nothing>
}

data class HomeUiState(
    val todayLabel: String = "",
    val inventory: HomeSection<HomeInventorySummary> = HomeSection.Loading,
    val knowledge: HomeSection<HomeKnowledgeSummary> = HomeSection.Loading,
)

/** 首页错误文案：说明发生了什么，并保留稳定错误码便于诊断。 */
object HomeErrorMessages {
    const val UNEXPECTED: String = "读取时发生未预期错误，请重试。"

    fun describe(error: CulinaryError): String = "${friendly(error)}（${error.code.value}）"

    private fun friendly(error: CulinaryError): String =
        when (error) {
            is CulinaryError.Data -> "本机数据库读取失败，请重试。"
            is CulinaryError.Conflict -> "数据正在被其他操作修改，请稍后重试。"
            is CulinaryError.Unexpected -> "读取时发生未预期错误。"
            is CulinaryError.NotFound -> "所需数据不存在或已被删除。"
            is CulinaryError.Domain -> "读取失败，请重试。"
            is CulinaryError.Platform -> "读取失败，请重试。"
            is CulinaryError.Transfer -> "读取失败，请重试。"
            is CulinaryError.Validation -> "读取失败，请重试。"
        }
}
