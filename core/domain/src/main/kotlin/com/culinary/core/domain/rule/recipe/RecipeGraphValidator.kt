package com.culinary.core.domain.rule.recipe

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.RecipeVersionId

/**
 * 配方图循环校验（D-006，CUL-REP-001）。
 *
 * 从根版本出发沿子菜谱引用做深度优先遍历；命中灰色节点即判定循环，
 * 并把循环路径（首个重复节点到再次出现）返回给调用方用于提示。
 *
 * 只校验"引用是否成环"与"被引用版本是否存在"；
 * 展开深度/节点上限（D-025，仍为 `PROPOSED`）不在这里实现。
 */
class RecipeGraphValidator {
    fun validate(
        root: RecipeVersionId,
        source: RecipeGraphSource,
    ): Outcome<Unit> {
        val issue = walk(root, source) ?: return Outcome.success(Unit)
        return when (issue) {
            is GraphIssue.Cycle ->
                Outcome.failure(
                    CulinaryError.Domain(
                        detail = DomainDetail.RecipeCycle(issue.path.map { it.value }),
                        code = ErrorCode.REP_001,
                    ),
                )
            is GraphIssue.Missing ->
                Outcome.failure(
                    CulinaryError.NotFound(
                        entity = "recipe_version",
                        id = issue.id.value,
                    ),
                )
        }
    }

    private fun walk(
        root: RecipeVersionId,
        source: RecipeGraphSource,
    ): GraphIssue? {
        val visiting = LinkedHashSet<RecipeVersionId>()
        val visited = HashSet<RecipeVersionId>()
        return visit(root, source, visiting, visited)
    }

    private fun visit(
        id: RecipeVersionId,
        source: RecipeGraphSource,
        visiting: LinkedHashSet<RecipeVersionId>,
        visited: HashSet<RecipeVersionId>,
    ): GraphIssue? {
        if (id in visiting) {
            val path = visiting.toList()
            val start = path.indexOf(id)
            return GraphIssue.Cycle(path.subList(start, path.size) + id)
        }
        if (id in visited) return null

        val version = source.findVersion(id) ?: return GraphIssue.Missing(id)

        visiting += id
        var issue: GraphIssue? = null
        for (child in version.childRecipeVersions()) {
            issue = visit(child, source, visiting, visited)
            if (issue != null) break
        }
        visiting -= id
        if (issue == null) visited += id
        return issue
    }

    private sealed interface GraphIssue {
        data class Cycle(
            val path: List<RecipeVersionId>,
        ) : GraphIssue

        data class Missing(
            val id: RecipeVersionId,
        ) : GraphIssue
    }
}
