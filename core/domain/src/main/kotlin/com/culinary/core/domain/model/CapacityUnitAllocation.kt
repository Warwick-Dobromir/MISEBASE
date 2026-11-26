package com.culinary.core.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Capacity-unit allocation is a logical classification layer. It never changes the physical
 * storage location recorded on an inventory batch.
 */
enum class ExpiryCapacityZone(val displayLabel: String) {
    NORMAL("正常"),
    ATTENTION("关注"),
    NEAR_EXPIRY("临期"),
    EXPIRED("过期"),
    UNCLASSIFIED("待补充效期");

    companion object {
        /** >50% normal, >20% attention, >0% near expiry, otherwise expired; null is unclassified. */
        fun fromRemainingRatio(ratio: BigDecimal?): ExpiryCapacityZone? {
            if (ratio == null) return null
            return when {
                ratio > BigDecimal("0.5") -> NORMAL
                ratio > BigDecimal("0.2") -> ATTENTION
                ratio > BigDecimal.ZERO -> NEAR_EXPIRY
                else -> EXPIRED
            }
        }
    }
}

/** Capacity of one logical cell under a physical base location and an expiry zone. */
data class CapacityCellSnapshot(
    val sequence: Int,
    val capacityCu: BigDecimal,
    val occupiedCu: BigDecimal,
) {
    init {
        require(sequence > 0) { "sequence must be positive" }
        require(capacityCu > BigDecimal.ZERO) { "capacity must be positive" }
        require(occupiedCu >= BigDecimal.ZERO) { "occupied capacity cannot be negative" }
    }

    val remainingCu: BigDecimal get() = (capacityCu - occupiedCu).max(BigDecimal.ZERO)
}

/** A quantity allocation to one logical capacity cell. */
data class CapacityCellAllocation(
    val sequence: Int,
    val allocatedCu: BigDecimal,
)

/** Pure allocation result; persistence and physical movement are deliberately handled elsewhere. */
data class CapacityAllocationPlan(
    val requestedCu: BigDecimal,
    val allocations: List<CapacityCellAllocation>,
    val cellsToCreate: List<Int>,
) {
    val allocatedCu: BigDecimal get() = allocations.fold(BigDecimal.ZERO) { sum, item -> sum + item.allocatedCu }
}

/**
 * Calculates how much capacity a stock receipt consumes and fills existing cells before
 * creating sequential cells. Different archives may share a cell when they have the same
 * physical base location and expiry zone; callers provide the occupancy for that scope.
 */
object CapacityUnitAllocator {
    fun calculateRequiredCu(
        stockQuantity: Double,
        cuPerStockUnit: Double,
    ): BigDecimal {
        require(stockQuantity.isFinite() && stockQuantity > 0.0) { "stock quantity must be finite and positive" }
        require(cuPerStockUnit.isFinite() && cuPerStockUnit > 0.0) { "CU coefficient must be finite and positive" }
        return BigDecimal(stockQuantity.toString())
            .multiply(BigDecimal(cuPerStockUnit.toString()))
            .setScale(6, RoundingMode.HALF_UP)
            .stripTrailingZeros()
    }

    fun plan(
        requestedCu: BigDecimal,
        cellCapacityCu: BigDecimal,
        existingCells: List<CapacityCellSnapshot>,
    ): CapacityAllocationPlan {
        require(requestedCu > BigDecimal.ZERO) { "requested capacity must be positive" }
        require(cellCapacityCu > BigDecimal.ZERO) { "cell capacity must be positive" }
        require(existingCells.map { it.sequence }.distinct().size == existingCells.size) {
            "cell sequence numbers must be unique"
        }
        var remaining = requestedCu
        val allocations = mutableListOf<CapacityCellAllocation>()
        existingCells.sortedBy { it.sequence }.forEach { cell ->
            if (remaining > BigDecimal.ZERO && cell.remainingCu > BigDecimal.ZERO) {
                val amount = remaining.min(cell.remainingCu)
                allocations += CapacityCellAllocation(cell.sequence, amount)
                remaining -= amount
            }
        }

        val cellsToCreate = mutableListOf<Int>()
        var nextSequence = (existingCells.maxOfOrNull { it.sequence } ?: 0) + 1
        while (remaining > BigDecimal.ZERO) {
            val amount = remaining.min(cellCapacityCu)
            allocations += CapacityCellAllocation(nextSequence, amount)
            cellsToCreate += nextSequence
            remaining -= amount
            nextSequence += 1
        }

        return CapacityAllocationPlan(
            requestedCu = requestedCu,
            allocations = allocations,
            cellsToCreate = cellsToCreate,
        )
    }
}
