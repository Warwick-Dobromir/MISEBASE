package com.culinary.core.domain.usecase.recipe

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeCategory
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.rule.common.NameText
import com.culinary.core.domain.transaction.TransactionRunner

/** 新建或编辑菜谱档案的输入。 */
data class SaveRecipeInput(
    val name: String,
    val category: RecipeCategory = RecipeCategory.DISH,
    val id: RecipeId? = null,
    val englishName: String? = null,
    val allergenNotes: String? = null,
)

/** 保存菜谱档案（不含版本内容）；编辑档案不改变已发布版本。 */
class SaveRecipeUseCase(
    private val repository: RecipeRepository,
    private val idGenerator: IdGenerator,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(input: SaveRecipeInput): Outcome<RecipeId> {
        val name = input.name.trim()
        if (name.isEmpty()) return invalid("must not be blank")
        if (name.length > MAX_NAME_LENGTH) return invalid("must be at most $MAX_NAME_LENGTH characters")

        val existing =
            if (input.id == null) {
                null
            } else {
                when (val result = repository.get(input.id)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> result.value
                }
            }
        val base =
            existing ?: Recipe(
                id = input.id ?: RecipeId(idGenerator.newId()),
                name = name,
                normalizedName = NameText.normalize(name),
                category = input.category,
                status = EntityStatus.ACTIVE,
            )
        val recipe =
            base.copy(
                name = name,
                normalizedName = NameText.normalize(name),
                category = input.category,
                englishName = input.englishName.blankToNull() ?: existing?.englishName,
                allergenNotes = input.allergenNotes.blankToNull() ?: existing?.allergenNotes,
            )
        return transactionRunner.write { repository.save(recipe) }.flatMap { it }
    }

    private fun invalid(rule: String): Outcome<Nothing> =
        Outcome.failure(CulinaryError.Validation(field = "recipe.name", rule = rule))

    private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val MAX_NAME_LENGTH = 120
    }
}
