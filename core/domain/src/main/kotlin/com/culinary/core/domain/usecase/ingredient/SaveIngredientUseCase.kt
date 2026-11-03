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
import com.culinary.core.domain.rule.ingredient.IngredientNames
import com.culinary.core.domain.transaction.TransactionRunner

/** 新建或重命名食材的输入；[id] 为空表示新建。 */
data class SaveIngredientInput(
    val name: String,
    val id: IngredientId? = null,
    val defaultUnitId: UnitId? = null,
)

/**
 * 保存食材（阶段 7 纵向切片）。
 *
 * 校验在用例内完成：名称非空、长度上限；规范化用于查重。
 * 事务由 [TransactionRunner] 统一开启，Repository 只做单条写入。
 */
class SaveIngredientUseCase(
    private val repository: IngredientRepository,
    private val idGenerator: IdGenerator,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(input: SaveIngredientInput): Outcome<IngredientId> {
        val name = input.name.trim()
        if (name.isEmpty()) {
            return invalid("must not be blank")
        }
        if (name.length > MAX_NAME_LENGTH) {
            return invalid("must be at most $MAX_NAME_LENGTH characters")
        }

        val ingredient =
            Ingredient(
                id = input.id ?: IngredientId(idGenerator.newId()),
                name = name,
                normalizedName = IngredientNames.normalize(name),
                defaultUnitId = input.defaultUnitId,
                status = EntityStatus.ACTIVE,
            )

        return transactionRunner.write { repository.save(ingredient) }.flatMap { it }
    }

    private fun invalid(rule: String): Outcome<Nothing> =
        Outcome.failure(
            CulinaryError.Validation(
                field = "ingredient.name",
                rule = rule,
            ),
        )

    private companion object {
        const val MAX_NAME_LENGTH = 120
    }
}
