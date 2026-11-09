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

/**
 * 草稿组成行输入：目标 + 数量 + 单位 + 可选标记。
 *
 * Stage 3 只允许食材目标；Stage 4 起保留已导入的子菜谱/子酱汁目标，
 * 编辑时以只读方式回写，避免保存时静默丢弃。兼容旧调用方的食材构造器。
 */
data class SauceDraftLineInput(
    val target: ComponentTarget,
    val amount: Double,
    val unit: Unit,
    val isOptional: Boolean = false,
    val labelOverride: String? = null,
    val labelOverrideEn: String? = null,
) {
    constructor(
        ingredientId: IngredientId,
        amount: Double,
        unit: Unit,
        isOptional: Boolean = false,
        labelOverride: String? = null,
        labelOverrideEn: String? = null,
    ) : this(
        ComponentTarget.Ingredient(ingredientId),
        amount,
        unit,
        isOptional,
        labelOverride,
        labelOverrideEn,
    )
}

/**
 * 保存酱汁草稿版本的输入。
 *
 * [versionId] 非空表示更新同一份未发布草稿；为空表示创建新草稿。
 * 已发布版本不会被覆盖（D-006）。
 */
data class SaveSauceDraftInput(
    val sauceId: SauceId,
    val yieldAmount: Double,
    val yieldUnit: Unit,
    val components: List<SauceDraftLineInput>,
    val instructions: String? = null,
    val stepsJson: String? = null,
    val versionId: SauceVersionId? = null,
    /** 最小制作范围；为空表示未设置。数值与单位必须成对出现。 */
    val minRangeAmount: Double? = null,
    val minRangeUnit: Unit? = null,
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
    @Suppress("ReturnCount") // 逐项校验并返回首个错误，便于 UI 定位字段
    suspend operator fun invoke(input: SaveSauceDraftInput): Outcome<SauceVersionId> {
        if (!input.yieldAmount.isFinite() || input.yieldAmount <= 0.0) {
            return invalid("sauce.yield", "must be a finite positive number")
        }
        val minRange =
            when {
                input.minRangeAmount == null && input.minRangeUnit == null -> null
                input.minRangeAmount == null || input.minRangeUnit == null ->
                    return invalid("sauce.minRange", "amount and unit must be provided together")
                !input.minRangeAmount.isFinite() || input.minRangeAmount <= 0.0 ->
                    return invalid("sauce.minRange", "must be a finite positive number")
                !input.minRangeUnit.isSameDimensionAs(input.yieldUnit) ->
                    return invalid("sauce.minRange", "must use the same dimension as yield")
                input.minRangeAmount * input.minRangeUnit.baseFactor >
                    input.yieldAmount * input.yieldUnit.baseFactor ->
                    return invalid("sauce.minRange", "must not exceed yield")
                else -> Quantity(input.minRangeAmount, input.minRangeUnit)
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
                    target = line.target,
                    quantity = Quantity(line.amount, line.unit),
                    isOptional = line.isOptional,
                    labelOverride = line.labelOverride?.trim()?.takeIf { it.isNotEmpty() },
                    labelOverrideEn = line.labelOverrideEn?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
        val existing =
            input.versionId?.let { versionId ->
                when (val result = repository.getVersion(versionId)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> result.value
                }
            }
        if (existing != null && existing.status != VersionStatus.DRAFT) {
            return invalid("sauce.version", "only draft versions can be edited")
        }
        if (existing != null && existing.sauceId != input.sauceId) {
            return invalid("sauce.version", "draft belongs to another sauce")
        }
        return transactionRunner
            .write {
                val version =
                    SauceVersion(
                        id = existing?.id ?: SauceVersionId(idGenerator.newId()),
                        sauceId = input.sauceId,
                        versionNo = existing?.versionNo ?: repository.nextVersionNo(input.sauceId),
                        status = VersionStatus.DRAFT,
                        yield = Quantity(input.yieldAmount, input.yieldUnit),
                        components = components,
                        minRange = minRange,
                        instructions = input.instructions?.trim()?.takeIf { it.isNotEmpty() },
                        stepsJson = input.stepsJson?.trim()?.takeIf { it.isNotEmpty() },
                    )
                repository.saveDraft(version)
            }.flatMap { it }
    }

    private fun invalid(
        field: String,
        rule: String,
    ): Outcome<Nothing> = Outcome.failure(CulinaryError.Validation(field = field, rule = rule))
}
