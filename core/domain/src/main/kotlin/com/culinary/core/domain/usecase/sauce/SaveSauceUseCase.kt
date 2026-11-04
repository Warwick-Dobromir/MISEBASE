package com.culinary.core.domain.usecase.sauce

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.repository.SauceRepository
import com.culinary.core.domain.rule.common.NameText
import com.culinary.core.domain.transaction.TransactionRunner

/** 新建或编辑酱汁档案的输入；[id] 为空表示新建。 */
data class SaveSauceInput(
    val name: String,
    val id: SauceId? = null,
    val family: String? = null,
    val usageNotes: String? = null,
)

/**
 * 保存酱汁档案（不含版本内容）。
 *
 * 编辑已发布酱汁只改档案元数据；配方内容必须通过新版本表达（D-006）。
 */
class SaveSauceUseCase(
    private val repository: SauceRepository,
    private val idGenerator: IdGenerator,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(input: SaveSauceInput): Outcome<SauceId> {
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
        val sauce =
            Sauce(
                id = input.id ?: SauceId(idGenerator.newId()),
                name = name,
                normalizedName = NameText.normalize(name),
                status = existing?.status ?: EntityStatus.ACTIVE,
                family = input.family.blankToNull(),
                usageNotes = input.usageNotes.blankToNull(),
            )
        return transactionRunner.write { repository.save(sauce) }.flatMap { it }
    }

    private fun invalid(rule: String): Outcome<Nothing> =
        Outcome.failure(CulinaryError.Validation(field = "sauce.name", rule = rule))

    private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val MAX_NAME_LENGTH = 120
    }
}
