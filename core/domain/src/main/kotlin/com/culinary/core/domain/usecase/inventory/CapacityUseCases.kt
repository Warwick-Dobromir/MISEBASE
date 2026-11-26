package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.CapacityCellSummary
import com.culinary.core.domain.repository.InventoryArchiveRepository

class SetLocationCapacityLimitUseCase(private val repository: InventoryArchiveRepository) {
    suspend operator fun invoke(locationId: String, capacityCu: Double): Outcome<Unit> {
        if (!capacityCu.isFinite() || capacityCu <= 0.0) {
            return Outcome.failure(CulinaryError.Validation("capacityCu", "must be finite and positive"))
        }
        return repository.setLocationCapacityLimit(locationId, capacityCu)
    }
}

class ListInventoryCapacityCellsUseCase(private val repository: InventoryArchiveRepository) {
    suspend operator fun invoke(): Outcome<List<CapacityCellSummary>> = repository.listCapacityCells()
}

class GetLocationCapacityLimitUseCase(private val repository: InventoryArchiveRepository) {
    suspend operator fun invoke(locationId: String): Outcome<Double> =
        repository.getLocationCapacityLimit(locationId)
}
