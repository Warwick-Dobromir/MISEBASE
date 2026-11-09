package com.culinary.core.domain.usecase.recipe

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RecipeComponent
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.VersionStatus

/**
 * 菜谱版本输入校验与构造；校验失败返回 `CUL-VAL-001`，不写入任何版本。
 */
internal object RecipeInputValidator {
    fun validate(input: RecipeVersionInput): Outcome<Nothing>? {
        if (!input.yieldAmount.isFinite() || input.yieldAmount <= 0.0) {
            return invalid("recipe.yield", "must be a finite positive number")
        }
        if (input.servings <= 0) {
            return invalid("recipe.servings", "must be > 0")
        }
        input.components.forEachIndexed { index, line ->
            if (!line.amount.isFinite() || line.amount <= 0.0) {
                return invalid("recipe.component[$index].quantity", "must be a finite positive number")
            }
        }
        return null
    }

    fun version(
        input: RecipeVersionInput,
        id: RecipeVersionId,
        versionNo: Int,
        status: VersionStatus,
        components: List<RecipeComponent>,
    ): RecipeVersion =
        RecipeVersion(
            id = id,
            recipeId = input.recipeId,
            versionNo = versionNo,
            status = status,
            yield = Quantity(input.yieldAmount, input.yieldUnit),
            components = components,
            servings = input.servings,
            instructions = input.instructions?.trim()?.takeIf { it.isNotEmpty() },
            stepsJson = input.stepsJson?.takeIf { it.isNotBlank() },
        )

    private fun invalid(
        field: String,
        rule: String,
    ): Outcome<Nothing> = Outcome.failure(CulinaryError.Validation(field = field, rule = rule))
}
