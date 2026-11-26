package com.culinary.core.domain.model

import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import org.junit.Test

class CapacityUnitAllocatorTest {
    @Test
    fun `converts stock quantity into capacity units`() {
        assertThat(CapacityUnitAllocator.calculateRequiredCu(5.0, 1.5))
            .isEqualTo(BigDecimal("7.5"))
    }

    @Test
    fun `fills existing cells before creating the next cell`() {
        val plan = CapacityUnitAllocator.plan(
            requestedCu = BigDecimal("8"),
            cellCapacityCu = BigDecimal("10"),
            existingCells = listOf(
                CapacityCellSnapshot(1, BigDecimal("10"), BigDecimal("7")),
                CapacityCellSnapshot(2, BigDecimal("10"), BigDecimal("0")),
            ),
        )

        assertThat(plan.allocations).containsExactly(
            CapacityCellAllocation(1, BigDecimal("3")),
            CapacityCellAllocation(2, BigDecimal("5")),
        ).inOrder()
        assertThat(plan.cellsToCreate).isEmpty()
        assertThat(plan.allocatedCu).isEqualTo(BigDecimal("8"))
    }

    @Test
    fun `creates sequential cells when existing capacity is exhausted`() {
        val plan = CapacityUnitAllocator.plan(
            requestedCu = BigDecimal("23"),
            cellCapacityCu = BigDecimal("10"),
            existingCells = listOf(
                CapacityCellSnapshot(1, BigDecimal("10"), BigDecimal("10")),
                CapacityCellSnapshot(2, BigDecimal("10"), BigDecimal("5")),
            ),
        )

        assertThat(plan.allocations).containsExactly(
            CapacityCellAllocation(2, BigDecimal("5")),
            CapacityCellAllocation(3, BigDecimal("10")),
            CapacityCellAllocation(4, BigDecimal("8")),
        ).inOrder()
        assertThat(plan.cellsToCreate).containsExactly(3, 4).inOrder()
        assertThat(plan.allocatedCu).isEqualTo(BigDecimal("23"))
    }


    @Test
    fun `respects different historical cell capacities after template changes`() {
        val plan = CapacityUnitAllocator.plan(
            requestedCu = BigDecimal("7"),
            cellCapacityCu = BigDecimal("10"),
            existingCells = listOf(
                CapacityCellSnapshot(1, BigDecimal("6"), BigDecimal("6")),
                CapacityCellSnapshot(2, BigDecimal("12"), BigDecimal("10")),
            ),
        )

        assertThat(plan.allocations).containsExactly(
            CapacityCellAllocation(2, BigDecimal("2")),
            CapacityCellAllocation(3, BigDecimal("5")),
        ).inOrder()
        assertThat(plan.cellsToCreate).containsExactly(3)
    }

    @Test
    fun `rejects invalid coefficients and quantities`() {
        assertThat(runCatching { CapacityUnitAllocator.calculateRequiredCu(0.0, 1.0) }.isFailure).isTrue()
        assertThat(runCatching { CapacityUnitAllocator.calculateRequiredCu(1.0, Double.NaN) }.isFailure).isTrue()
    }
}
