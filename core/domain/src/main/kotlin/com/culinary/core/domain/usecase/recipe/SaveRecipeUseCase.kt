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

/**
 * 新建或编辑菜谱档案的输入。
 *
 * 可选字段为 null 表示"本次未提供"（保留旧值），空串表示"清空"；
 * 这样编辑单个字段时不会误删未参与本次保存的档案内容。
 */
data class SaveRecipeInput(
    val name: String,
    val category: RecipeCategory = RecipeCategory.DISH,
    val id: RecipeId? = null,
    val englishName: String? = null,
    val description: String? = null,
    val cuisine: String? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    /** 对应既有 `recipe.difficulty` 列；仅字段展示名改为"最小制作容量"。 */
    val minimumCapacity: String? = null,
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
                englishName = merge(input.englishName, existing?.englishName),
                description = merge(input.description, existing?.description),
                cuisine = merge(input.cuisine, existing?.cuisine),
                prepMinutes = input.prepMinutes ?: existing?.prepMinutes,
                cookMinutes = input.cookMinutes ?: existing?.cookMinutes,
                difficulty = merge(input.minimumCapacity, existing?.difficulty),
                allergenNotes = merge(input.allergenNotes, existing?.allergenNotes),
            )
        return transactionRunner.write { repository.save(recipe) }.flatMap { it }
    }

    private fun invalid(rule: String): Outcome<Nothing> =
        Outcome.failure(CulinaryError.Validation(field = "recipe.name", rule = rule))

    /** null 保留旧值；空串清空；非空写入新值。 */
    private fun merge(
        incoming: String?,
        existing: String?,
    ): String? = if (incoming == null) existing else incoming.trim().takeIf { it.isNotEmpty() }

    private companion object {
        const val MAX_NAME_LENGTH = 120
    }
}
