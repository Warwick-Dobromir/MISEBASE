package com.culinary.core.domain.usecase.kitchen

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.KitchenReference
import com.culinary.core.domain.model.KitchenReferenceId
import com.culinary.core.domain.model.KitchenReferenceKind
import com.culinary.core.domain.repository.KitchenReferenceRepository
import com.culinary.core.domain.rule.common.NameText
import com.culinary.core.domain.transaction.TransactionRunner

data class SaveKitchenReferenceInput(
    val kind: KitchenReferenceKind,
    val name: String,
    val id: KitchenReferenceId? = null,
    val category: String? = null,
    val description: String? = null,
    val englishName: String? = null,
)

/** 新建/编辑烹饪技术或工作标准；字段最小化，但不与既有档案混用。 */
class SaveKitchenReferenceUseCase(
    private val repository: KitchenReferenceRepository,
    private val idGenerator: IdGenerator,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(input: SaveKitchenReferenceInput): Outcome<KitchenReferenceId> {
        validate(input)?.let { return Outcome.failure(it) }
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
            existing ?: KitchenReference(
                id = input.id ?: KitchenReferenceId(idGenerator.newId()),
                kind = input.kind,
                name = name,
                normalizedName = NameText.normalize(name),
                category = null,
                description = null,
                status = EntityStatus.ACTIVE,
            )
        val reference =
            base.copy(
                kind = input.kind,
                name = name,
                normalizedName = NameText.normalize(name),
                category = input.category.blankToNull(),
                description = input.description.blankToNull(),
                englishName = input.englishName.blankToNull() ?: existing?.englishName,
            )
        return transactionRunner.write { repository.save(reference) }.flatMap { it }
    }

    private fun validate(input: SaveKitchenReferenceInput): CulinaryError? {
        val name = input.name.trim()
        val category = input.category.blankToNull()
        val description = input.description.blankToNull()
        return when {
            name.isEmpty() -> validation("kitchenReference.name", "must not be blank")
            name.length > MAX_NAME_LENGTH ->
                validation("kitchenReference.name", "must be at most $MAX_NAME_LENGTH characters")

            category != null && category.length > MAX_CATEGORY_LENGTH ->
                validation("kitchenReference.category", "must be at most $MAX_CATEGORY_LENGTH characters")

            description != null && description.length > MAX_DESCRIPTION_LENGTH ->
                validation("kitchenReference.description", "must be at most $MAX_DESCRIPTION_LENGTH characters")

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
