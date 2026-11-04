package com.culinary.core.domain.model

/**
 * 酱汁版本（A06 §4.5 的酱汁侧）。
 *
 * 与 [RecipeVersion] 一致：产量必须大于 0，`lineNo` 不得重复，已发布版本不可原地修改。
 */
data class SauceVersion(
    val id: SauceVersionId,
    val sauceId: SauceId,
    val versionNo: Int,
    val status: VersionStatus,
    val yield: Quantity,
    val components: List<SauceComponent>,
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
}
