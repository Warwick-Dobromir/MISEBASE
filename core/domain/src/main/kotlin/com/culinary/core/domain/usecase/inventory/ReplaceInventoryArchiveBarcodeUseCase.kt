package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.domain.repository.InventoryArchiveRepository

/**
 * Replaces the barcode on an existing inventory archive.
 * A barcode already assigned to another active archive is never overwritten.
 */
class ReplaceInventoryArchiveBarcodeUseCase(
    private val repository: InventoryArchiveRepository,
) {
    suspend operator fun invoke(archiveId: String, barcode: String): Outcome<String> {
        val normalized = barcode.trim()
        if (normalized.isEmpty()) {
            return Outcome.failure(CulinaryError.Validation(field = "barcode", rule = "must not be blank"))
        }

        val archive = when (val result = repository.findArchive(archiveId)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
                ?: return Outcome.failure(CulinaryError.NotFound(entity = "inventory_archive", id = archiveId))
        }

        val archives = when (val result = repository.listArchives()) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
        }
        val duplicate = archives.any { other ->
            other.id != archiveId && other.barcode?.trim()?.equals(normalized, ignoreCase = true) == true
        }
        if (duplicate) {
            return Outcome.failure(CulinaryError.Conflict(entity = "inventory_archive_barcode"))
        }

        return repository.saveArchive(archive.copy(barcode = normalized))
    }
}
