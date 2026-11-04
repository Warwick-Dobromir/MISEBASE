package com.culinary.core.domain.usecase.sauce

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.SauceComponent
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.SauceRepository
import com.culinary.core.domain.transaction.TransactionRunner

/** 草稿组成行输入：数量 + 单位 + 可选标记。 */
data class SauceDraftLineInput(
    val ingredientId: IngredientId,
    val amount: Double,
    val unit: Unit,
    val isOptional: Boolean = false,
)

/** 保存酱汁草稿版本的输入。 */
data class SaveSauceDraftInput(
    val sauceId: SauceId,
    val yieldAmount: Double,
    val yieldUnit: Unit,
    val components: List<SauceDraftLineInput>,
)

/**
 * 保存酱汁草稿版本（总是新版本，不覆盖已发布内容）。
 *
 * 校验在用例内完成；数量/单位均为合法值后才写入。
 */
class SaveSauceDraftUseCase(
    private val repository: SauceRepository,
    private val idGenerator: IdGenerator,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(input: SaveSauceDraftInput): Outcome<SauceVersionId> {
        if (!input.yieldAmount.isFinite() || input.yieldAmount <= 0.0) {
            return invalid("sauce.yield", "must be a finite positive number")
        }
        input.components.forEachIndexed { index, line ->
            if (!line.amount.isFinite() || line.amount <= 0.0) {
                return invalid("sauce.component[$index].quantity", "must be a finite positive number")
            }
        }

        val components =
            input.components.mapIndexed { index, line ->
                SauceComponent(
                    lineNo = index + 1,
                    target = ComponentTarget.Ingredient(line.ingredientId),
                    quantity = Quantity(line.amount, line.unit),
                    isOptional = line.isOptional,
                )
            }
        val version =
            SauceVersion(
                id = SauceVersionId(idGenerator.newId()),
                sauceId = input.sauceId,
                versionNo = repository.nextVersionNo(input.sauceId),
                status = VersionStatus.DRAFT,
                yield = Quantity(input.yieldAmount, input.yieldUnit),
                components = components,
            )
        return transactionRunner.write { repository.saveDraft(version) }.flatMap { it }
    }

    private fun invalid(
        field: String,
        rule: String,
    ): Outcome<Nothing> = Outcome.failure(CulinaryError.Validation(field = field, rule = rule))
}
