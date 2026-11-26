package com.culinary.core.domain.model

import java.math.BigDecimal

/** Stable logical identity for a generated cell; this is not a physical StorageLocation. */
data class CapacityCellIdentity(
    val baseLocationId: LocationId,
    val expiryZone: ExpiryCapacityZone,
    val sequence: Int,
) {
    init { require(sequence > 0) { "sequence must be positive" } }

    fun displayName(baseLocationName: String): String {
        require(baseLocationName.isNotBlank()) { "base location name must not be blank" }
        val suffix = if (expiryZone == ExpiryCapacityZone.NORMAL) "" else "（${expiryZone.displayLabel}）"
        return "$baseLocationName$suffix-${sequence.toString().padStart(2, '0')}"
    }
}

data class CapacityPlanningRequest(
    val baseLocationId: LocationId,
    val baseLocationName: String,
    val expiryZone: ExpiryCapacityZone,
    val requestedCu: BigDecimal,
    val cellCapacityCu: BigDecimal,
    val existingCells: List<CapacityCellSnapshot>,
) {
    init {
        require(baseLocationName.isNotBlank())
        require(requestedCu > BigDecimal.ZERO)
        require(cellCapacityCu > BigDecimal.ZERO)
    }
}

data class ScopedCapacityAllocation(
    val identity: CapacityCellIdentity,
    val displayName: String,
    val allocatedCu: BigDecimal,
    val createCell: Boolean,
)

object CapacityPlanningService {
    fun plan(request: CapacityPlanningRequest): List<ScopedCapacityAllocation> {
        val plan = CapacityUnitAllocator.plan(request.requestedCu, request.cellCapacityCu, request.existingCells)
        val created = plan.cellsToCreate.toSet()
        return plan.allocations.map { allocation ->
            val identity = CapacityCellIdentity(request.baseLocationId, request.expiryZone, allocation.sequence)
            ScopedCapacityAllocation(
                identity = identity,
                displayName = identity.displayName(request.baseLocationName),
                allocatedCu = allocation.allocatedCu,
                createCell = allocation.sequence in created,
            )
        }
    }
}


/** 库位容量格的可读状态；逻辑格与真实物理库位分离。 */
data class CapacityCellSummary(
    val id: String,
    val baseLocationId: LocationId,
    val baseLocationName: String,
    val expiryZone: ExpiryCapacityZone,
    val sequence: Int,
    val capacityCu: BigDecimal,
    val occupiedCu: BigDecimal,
    val configuredCapacityCu: BigDecimal,
) {
    val remainingCu: BigDecimal get() = (capacityCu - occupiedCu).max(BigDecimal.ZERO)
    val isOverConfiguredLimit: Boolean get() = occupiedCu > configuredCapacityCu
    val displayName: String get() = CapacityCellIdentity(baseLocationId, expiryZone, sequence).displayName(baseLocationName)
}
