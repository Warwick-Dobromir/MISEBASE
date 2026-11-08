package com.culinary.core.domain.usecase.ingredient

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.rule.common.NameText
import com.culinary.core.domain.transaction.TransactionRunner

/** 新建或编辑食材的输入；[id] 为空表示新建。 */
data class SaveIngredientInput(
    val name: String,
    val id: IngredientId? = null,
    val defaultUnitId: UnitId? = null,
    val category: String? = null,
    val description: String? = null,
    val englishName: String? = null,
    val exampleProducts: String? = null,
    val storageNotes: String? = null,
    val allergenNotes: String? = null,
)

/**
 * 保存食材（Stage 3 档案编辑）。
 *
 * 校验在用例内完成：名称非空、长度上限；分类/描述有独立上限。
 * 事务由 [TransactionRunner] 统一开启，Repository 只做单条写入。
 */
class SaveIngredientUseCase(
    private val repository: IngredientRepository,
    private val idGenerator: IdGenerator,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(input: SaveIngredientInput): Outcome<IngredientId> {
        val error = validate(input)
        if (error != null) return Outcome.failure(error)
        val name = input.name.trim()
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
            existing ?: Ingredient(
                id = input.id ?: IngredientId(idGenerator.newId()),
                name = name,
                normalizedName = NameText.normalize(name),
                defaultUnitId = input.defaultUnitId,
                status = EntityStatus.ACTIVE,
            )
        val ingredient =
            base.copy(
                name = name,
                normalizedName = NameText.normalize(name),
                defaultUnitId = input.defaultUnitId,
                category = input.category.blankToNull(),
                description = input.description.blankToNull(),
                englishName = input.englishName.blankToNull() ?: existing?.englishName,
                exampleProducts = input.exampleProducts.blankToNull() ?: existing?.exampleProducts,
                storageNotes = input.storageNotes.blankToNull() ?: existing?.storageNotes,
                allergenNotes = input.allergenNotes.blankToNull() ?: existing?.allergenNotes,
            )

        return transactionRunner.write { repository.save(ingredient) }.flatMap { it }
    }

    private fun validate(input: SaveIngredientInput): CulinaryError? {
        val name = input.name.trim()
        val category = input.category.blankToNull()
        val description = input.description.blankToNull()
        return when {
            name.isEmpty() -> validation("ingredient.name", "must not be blank")
            name.length > MAX_NAME_LENGTH ->
                validation("ingredient.name", "must be at most $MAX_NAME_LENGTH characters")

            category != null && category.length > MAX_CATEGORY_LENGTH ->
                validation("ingredient.category", "must be at most $MAX_CATEGORY_LENGTH characters")

            description != null && description.length > MAX_DESCRIPTION_LENGTH ->
                validation("ingredient.description", "must be at most $MAX_DESCRIPTION_LENGTH characters")

            else -> null
        }
    }

    private fun validation(
        field: String,
        rule: String,
    ): CulinaryError = CulinaryError.Validation(field = field, rule = rule)

    private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val MAX_NAME_LENGTH = 120
        const val MAX_CATEGORY_LENGTH = 60
        const val MAX_DESCRIPTION_LENGTH = 500
    }
}
