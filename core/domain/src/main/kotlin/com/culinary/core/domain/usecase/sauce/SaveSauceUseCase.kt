package com.culinary.core.domain.usecase.sauce

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceDifficulty
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
    val englishName: String? = null,
    val description: String? = null,
    val difficulty: SauceDifficulty? = null,
    val heroCaption: String? = null,
    val mainIngredients: String? = null,
    val preparationSummary: String? = null,
    val keyParameters: String? = null,
    val storageNotes: String? = null,
    val flavorProfile: String? = null,
    val adjustableNotes: String? = null,
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
    @Suppress("CyclomaticComplexMethod") // 多个可选档案字段的保留/覆盖分支集中在一个编辑入口
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
        val base =
            existing ?: Sauce(
                id = input.id ?: SauceId(idGenerator.newId()),
                name = name,
                normalizedName = NameText.normalize(name),
                status = EntityStatus.ACTIVE,
            )
        val sauce =
            base.copy(
                name = name,
                normalizedName = NameText.normalize(name),
                family = input.family.blankToNull(),
                usageNotes = input.usageNotes.blankToNull() ?: existing?.usageNotes,
                englishName = input.englishName.blankToNull() ?: existing?.englishName,
                description = input.description.blankToNull(),
                difficulty = input.difficulty,
                heroCaption = input.heroCaption.blankToNull(),
                mainIngredients = input.mainIngredients.blankToNull() ?: existing?.mainIngredients,
                preparationSummary = input.preparationSummary.blankToNull() ?: existing?.preparationSummary,
                keyParameters = input.keyParameters.blankToNull() ?: existing?.keyParameters,
                storageNotes = input.storageNotes.blankToNull() ?: existing?.storageNotes,
                flavorProfile = input.flavorProfile.blankToNull() ?: existing?.flavorProfile,
                adjustableNotes = input.adjustableNotes.blankToNull() ?: existing?.adjustableNotes,
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
