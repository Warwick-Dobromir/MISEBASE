package com.culinary.core.domain.rule.recipe

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.ComponentTargetDraft
import com.culinary.core.domain.model.RecipeComponent
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionDraft
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.VersionStatus

/**
 * 子配方版本固定（MB-D-013 / D-006）。
 *
 * - 草稿可指向"当前版本"（[ComponentTargetDraft.RecipeCurrent]）；
 * - **发布时解析并写入明确版本 ID**，生成 [RecipeVersion]；
 * - 已发布版本只含固定引用，不随"当前版本"指针变化；
 * - 解析不到当前版本或引用的版本不存在时返回 `CUL-NF-001`，不写入半成品。
 */
class RecipeVersionPinRule {
    /** 只做固定：把草稿中的"当前版本"引用解析为明确版本 ID。 */
    fun pin(
        draft: RecipeVersionDraft,
        versions: RecipeGraphSource,
        currentVersions: CurrentVersionSource,
    ): Outcome<List<RecipeComponent>> {
        val pinned = mutableListOf<RecipeComponent>()
        for (component in draft.components) {
            val target =
                when (val draftTarget = component.target) {
                    is ComponentTargetDraft.Ingredient -> ComponentTarget.Ingredient(draftTarget.id)

                    is ComponentTargetDraft.RecipePinned -> {
                        if (versions.findVersion(draftTarget.versionId) == null) {
                            return missingVersion(draftTarget.versionId)
                        }
                        ComponentTarget.Recipe(draftTarget.recipeId, draftTarget.versionId)
                    }

                    is ComponentTargetDraft.RecipeCurrent -> {
                        val versionId =
                            currentVersions.currentVersionId(draftTarget.recipeId)
                                ?: return missingCurrent(draftTarget.recipeId)
                        if (versions.findVersion(versionId) == null) {
                            return missingVersion(versionId)
                        }
                        ComponentTarget.Recipe(draftTarget.recipeId, versionId)
                    }

                    is ComponentTargetDraft.SaucePinned ->
                        ComponentTarget.Sauce(draftTarget.sauceId, draftTarget.versionId)
                }
            pinned +=
                RecipeComponent(
                    lineNo = component.lineNo,
                    target = target,
                    quantity = component.quantity,
                    isOptional = component.isOptional,
                )
        }
        return Outcome.success(pinned)
    }

    /** 固定并发布：返回状态为 `PUBLISHED` 的不可变版本。 */
    fun publish(
        draft: RecipeVersionDraft,
        versions: RecipeGraphSource,
        currentVersions: CurrentVersionSource,
    ): Outcome<RecipeVersion> =
        when (val pinned = pin(draft, versions, currentVersions)) {
            is Outcome.Failure -> pinned
            is Outcome.Success ->
                Outcome.success(
                    RecipeVersion(
                        id = draft.id,
                        recipeId = draft.recipeId,
                        versionNo = draft.versionNo,
                        status = VersionStatus.PUBLISHED,
                        yield = draft.yield,
                        components = pinned.value,
                        servings = draft.servings,
                    ),
                )
        }

    private fun missingCurrent(recipeId: RecipeId): Outcome<Nothing> =
        Outcome.failure(
            CulinaryError.NotFound(entity = "recipe_current_version", id = recipeId.value),
        )

    private fun missingVersion(versionId: RecipeVersionId): Outcome<Nothing> =
        Outcome.failure(
            CulinaryError.NotFound(entity = "recipe_version", id = versionId.value),
        )
}
