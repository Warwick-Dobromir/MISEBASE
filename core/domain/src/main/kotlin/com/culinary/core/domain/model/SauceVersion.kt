package com.culinary.core.domain.model

/**
 * 酱汁版本（A06 §4.5 的酱汁侧）。
 *
 * 与 [RecipeVersion] 一致：产量必须大于 0，`lineNo` 不得重复，已发布版本不可原地修改。
 * [isCurrent] 表示该版本是档案的"当前版本"；同一酱汁至多一个（数据库部分索引保证）。
 */
data class SauceVersion(
    val id: SauceVersionId,
    val sauceId: SauceId,
    val versionNo: Int,
    val status: VersionStatus,
    val yield: Quantity,
    val components: List<SauceComponent>,
    val isCurrent: Boolean = false,
    val instructions: String? = null,
    val stepsJson: String? = null,
) {
    init {
        require(versionNo > 0) { "sauce versionNo must be > 0, was $versionNo" }
        require(yield.amount > 0.0) { "sauce yield must be > 0, was ${yield.amount}" }
        require(components.map { it.lineNo }.toSet().size == components.size) {
            "sauce version ${id.value} contains duplicate component lineNo"
        }
    }

    val isPublished: Boolean get() = status == VersionStatus.PUBLISHED

    /** 本版本引用到的子菜谱版本，供图校验与展开使用。 */
    fun childRecipeVersions(): List<RecipeVersionId> =
        components.mapNotNull { component ->
            (component.target as? ComponentTarget.Recipe)?.versionId
        }

    /** 本版本引用到的子酱汁版本（酱汁嵌套在 Stage 3 不开放编辑，但读取需保真）。 */
    fun childSauceVersions(): List<SauceVersionId> =
        components.mapNotNull { component ->
            (component.target as? ComponentTarget.Sauce)?.versionId
        }
}
