package com.culinary.core.domain.model

/**
 * 菜谱版本（A06 §4.5）。
 *
 * [yield] 必须大于 0（CUL-REP-003）；[components] 的 [RecipeComponent.lineNo] 不得重复。
 * 已发布版本不可原地修改；编辑必须产生新版本（D-006）。
 */
data class RecipeVersion(
    val id: RecipeVersionId,
    val recipeId: RecipeId,
    val versionNo: Int,
    val status: VersionStatus,
    val yield: Quantity,
    val components: List<RecipeComponent>,
    val servings: Int = 1,
    val isCurrent: Boolean = false,
) {
    init {
        require(versionNo > 0) { "versionNo must be > 0, was $versionNo" }
        require(servings > 0) { "recipe servings must be > 0, was $servings" }
        require(yield.amount > 0.0) { "recipe yield must be > 0, was ${yield.amount}" }
        require(components.map { it.lineNo }.toSet().size == components.size) {
            "recipe version ${id.value} contains duplicate component lineNo"
        }
    }

    val isPublished: Boolean get() = status == VersionStatus.PUBLISHED

    /** 本版本中引用到的子菜谱版本，供图校验与展开使用。 */
    fun childRecipeVersions(): List<RecipeVersionId> =
        components.mapNotNull { component ->
            (component.target as? ComponentTarget.Recipe)?.versionId
        }
}
