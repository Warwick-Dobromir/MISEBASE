package com.culinary.home

import com.culinary.core.common.CulinaryError
import com.culinary.core.data.home.HomeInventorySummary
import com.culinary.core.data.home.HomeKnowledgeSummary

/**
 * 首页单模块状态。
 *
 * 每个模块独立加载、独立失败：库存读取失败不会替换知识库，反之亦然。
 *
 * [Failed] 把“给用户看的说明”和“给诊断用的错误码”分开：
 * 界面只展示 [Failed.message]，错误码保留在 [Failed.diagnosticCode] 供日志/诊断使用。
 */
sealed interface HomeSection<out T> {
    data object Loading : HomeSection<Nothing>

    data class Ready<T>(
        val value: T,
    ) : HomeSection<T>

    data class Failed(
        val message: String,
        val diagnosticCode: String? = null,
    ) : HomeSection<Nothing>
}

data class HomeUiState(
    val todayLabel: String = "",
    val inventory: HomeSection<HomeInventorySummary> = HomeSection.Loading,
    val knowledge: HomeSection<HomeKnowledgeSummary> = HomeSection.Loading,
    /** 后台刷新进行中；此时保留旧数据，只显示一条 2dp 细进度条。 */
    val isRefreshing: Boolean = false,
)

/** 首页错误文案：面向用户说明“发生了什么”，错误码单独提供给诊断。 */
object HomeErrorMessages {
    const val UNEXPECTED: String = "读取时发生未预期错误，请重试。"

    /** 用户可见文案，不含内部错误码。 */
    fun userMessage(error: CulinaryError): String = friendly(error)

    /** 诊断用错误码，例如 `CUL-DB-001`；不进入主界面文案。 */
    fun diagnosticCode(error: CulinaryError): String = error.code.value

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
