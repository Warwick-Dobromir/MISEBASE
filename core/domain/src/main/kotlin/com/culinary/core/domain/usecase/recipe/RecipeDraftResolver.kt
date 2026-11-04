package com.culinary.core.domain.usecase.recipe

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ComponentTargetDraft
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RecipeComponent
import com.culinary.core.domain.model.RecipeComponentDraft
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionDraft
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.rule.recipe.CurrentVersionSource
import com.culinary.core.domain.rule.recipe.RecipeGraphSource
import com.culinary.core.domain.rule.recipe.RecipeGraphValidator
import com.culinary.core.domain.rule.recipe.RecipeVersionPinRule

/**
 * 菜谱草稿的版本固定与图校验（MB-D-013 / CUL-REP-001）。
 *
 * 先把"当前版本"引用解析成明确版本 ID，再沿子菜谱引用预取整张图；
 * 发布前用 [RecipeGraphValidator] 拒绝成环或缺失版本，避免写入半成品。
 */
internal class RecipeDraftResolver(
    private val repository: RecipeRepository,
) {
    private val pinRule = RecipeVersionPinRule()
    private val validator = RecipeGraphValidator()

    suspend fun pin(draft: RecipeVersionDraft): Outcome<Pinned> {
        val currentVersions = mutableMapOf<RecipeId, RecipeVersionId>()
        for (component in draft.components) {
            val target = component.target
            if (target is ComponentTargetDraft.RecipeCurrent) {
                val current =
                    repository.getCurrentVersion(target.recipeId)
                        ?: return Outcome.failure(
                            CulinaryError.NotFound(
                                entity = "recipe_current_version",
                                id = target.recipeId.value,
                            ),
                        )
                currentVersions[target.recipeId] = current.id
            }
        }
        val roots = mutableSetOf<RecipeVersionId>()
        for (component in draft.components) {
            when (val target = component.target) {
                is ComponentTargetDraft.Ingredient -> Unit
                is ComponentTargetDraft.RecipePinned -> roots += target.versionId
                is ComponentTargetDraft.RecipeCurrent ->
                    currentVersions[target.recipeId]?.let(roots::add)
                is ComponentTargetDraft.SaucePinned -> Unit
            }
        }
        val versions = mutableMapOf<RecipeVersionId, RecipeVersion>()
        val queue = ArrayDeque(roots)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (versions.containsKey(id)) continue
            val version =
                repository.getVersion(id)
                    ?: return Outcome.failure(
                        CulinaryError.NotFound(entity = "recipe_version", id = id.value),
                    )
            versions[id] = version
            version.childRecipeVersions().forEach(queue::addLast)
        }
        val source = RecipeGraphSource { versions[it] }
        val currentSource = CurrentVersionSource { currentVersions[it] }
        return when (val pinned = pinRule.pin(draft, source, currentSource)) {
            is Outcome.Failure -> pinned
            is Outcome.Success -> Outcome.success(Pinned(components = pinned.value, versions = versions))
        }
    }

    /** 校验新版本及其子图；[versions] 必须包含所有被引用版本。 */
    fun validate(
        version: RecipeVersion,
        versions: Map<RecipeVersionId, RecipeVersion>,
    ): Outcome<Unit> {
        val graph = versions + (version.id to version)
        return validator.validate(version.id, RecipeGraphSource { graph[it] })
    }

    /** 发布已保存的草稿：预取子图后做同样的循环校验。 */
    suspend fun validateExisting(version: RecipeVersion): Outcome<Unit> {
        val versions = mutableMapOf<RecipeVersionId, RecipeVersion>()
        val queue = ArrayDeque(version.childRecipeVersions())
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (versions.containsKey(id) || id == version.id) continue
            val child =
                repository.getVersion(id)
                    ?: return Outcome.failure(
                        CulinaryError.NotFound(entity = "recipe_version", id = id.value),
                    )
            versions[id] = child
            child.childRecipeVersions().forEach(queue::addLast)
        }
        return validate(version, versions)
    }

    fun toDraft(
        input: RecipeVersionInput,
        id: RecipeVersionId,
        versionNo: Int,
    ): RecipeVersionDraft =
        RecipeVersionDraft(
            id = id,
            recipeId = input.recipeId,
            versionNo = versionNo,
            yield = Quantity(input.yieldAmount, input.yieldUnit),
            servings = input.servings,
            components =
                input.components.mapIndexed { index, line ->
                    RecipeComponentDraft(
                        lineNo = index + 1,
                        target = toDraftTarget(line.target),
                        quantity = Quantity(line.amount, line.unit),
                        isOptional = line.isOptional,
                    )
                },
        )

    private fun toDraftTarget(target: RecipeLineTarget): ComponentTargetDraft =
        when (target) {
            is RecipeLineTarget.Ingredient -> ComponentTargetDraft.Ingredient(target.id)
            is RecipeLineTarget.RecipeCurrent -> ComponentTargetDraft.RecipeCurrent(target.recipeId)
            is RecipeLineTarget.RecipePinned ->
                ComponentTargetDraft.RecipePinned(target.recipeId, target.versionId)
            is RecipeLineTarget.SaucePinned ->
                ComponentTargetDraft.SaucePinned(target.sauceId, target.versionId)
        }

    internal data class Pinned(
        val components: List<RecipeComponent>,
        val versions: Map<RecipeVersionId, RecipeVersion>,
    )
}
