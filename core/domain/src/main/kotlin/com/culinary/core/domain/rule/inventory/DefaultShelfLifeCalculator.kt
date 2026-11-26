package com.culinary.core.domain.rule.inventory

import com.culinary.core.domain.model.DefaultShelfLifeUnit
import java.time.LocalDate

/** 按库存档案默认保质期计算批次到期日。 */
fun calculateDefaultExpiryDate(
    productionDate: LocalDate?,
    shelfLifeValue: Int?,
    shelfLifeUnit: DefaultShelfLifeUnit?,
): LocalDate? {
    if (productionDate == null || shelfLifeValue == null || shelfLifeValue <= 0 || shelfLifeUnit == null) {
        return null
    }
    return when (shelfLifeUnit) {
        DefaultShelfLifeUnit.DAY -> productionDate.plusDays(shelfLifeValue.toLong())
        DefaultShelfLifeUnit.MONTH -> productionDate.plusMonths(shelfLifeValue.toLong())
        DefaultShelfLifeUnit.YEAR -> productionDate.plusYears(shelfLifeValue.toLong())
    }
}
