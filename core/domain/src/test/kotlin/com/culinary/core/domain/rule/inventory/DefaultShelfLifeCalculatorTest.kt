package com.culinary.core.domain.rule.inventory

import com.culinary.core.domain.model.DefaultShelfLifeUnit
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class DefaultShelfLifeCalculatorTest {
    @Test
    fun `calculates day based expiry`() {
        assertThat(calculateDefaultExpiryDate(LocalDate.parse("2026-11-20"), 9, DefaultShelfLifeUnit.DAY))
            .isEqualTo(LocalDate.parse("2026-11-29"))
    }

    @Test
    fun `calculates month based expiry and clamps month end`() {
        assertThat(calculateDefaultExpiryDate(LocalDate.parse("2026-01-31"), 1, DefaultShelfLifeUnit.MONTH))
            .isEqualTo(LocalDate.parse("2026-02-28"))
    }

    @Test
    fun `calculates year based expiry and clamps leap day`() {
        assertThat(calculateDefaultExpiryDate(LocalDate.parse("2024-02-29"), 1, DefaultShelfLifeUnit.YEAR))
            .isEqualTo(LocalDate.parse("2025-02-28"))
    }

    @Test
    fun `does not calculate without production date or complete shelf life`() {
        assertThat(calculateDefaultExpiryDate(null, 9, DefaultShelfLifeUnit.MONTH)).isNull()
        assertThat(calculateDefaultExpiryDate(LocalDate.parse("2026-11-20"), null, DefaultShelfLifeUnit.MONTH)).isNull()
        assertThat(calculateDefaultExpiryDate(LocalDate.parse("2026-11-20"), 9, null)).isNull()
    }
}
