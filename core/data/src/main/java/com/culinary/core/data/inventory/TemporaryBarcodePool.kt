package com.culinary.core.data.inventory

import com.culinary.core.database.dao.TemporaryBarcodeDao
import com.culinary.core.database.entity.TemporaryBarcodeEntity
import com.culinary.core.database.entity.TemporaryBarcodeHistoryEntity
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Allocates internal-only barcode identifiers to inventory batches.
 * Codes use the reserved internal prefix 29 and a valid EAN-13 check digit.
 * Releasing a code returns it to the pool; history rows are never deleted.
 */
@Singleton
class TemporaryBarcodePool @Inject constructor(
    private val dao: TemporaryBarcodeDao,
) {
    private val random = SecureRandom()

    suspend fun assign(batchId: String, now: Long = System.currentTimeMillis()): String? {
        dao.findForBatch(batchId)?.let { return it.code }
        repeat(12) {
            val available = dao.nextAvailable()
            if (available != null) {
                val event = history(available, batchId, "ASSIGNED", now)
                if (dao.recordAssignment(available, batchId, event, now)) return available.code
            } else {
                val code = nextCode()
                val entity = TemporaryBarcodeEntity(
                    id = UUID.randomUUID().toString(),
                    code = code,
                    createdAt = now,
                    updatedAt = now,
                )
                val event = TemporaryBarcodeHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    barcodeId = entity.id,
                    barcodeCode = code,
                    batchId = batchId,
                    action = "ASSIGNED",
                    occurredAt = now,
                )
                try {
                    if (dao.recordAssignment(entity, batchId, event, now)) return code
                } catch (_: Exception) {
                    // A unique-index collision is retried with a fresh EAN-13 value.
                }
            }
        }
        return null
    }

    suspend fun release(batchId: String, now: Long = System.currentTimeMillis()): Boolean {
        val current = dao.findForBatch(batchId) ?: return false
        val event = history(current, batchId, "RELEASED", now)
        return dao.recordRelease(current.id, batchId, now, event)
    }

    suspend fun availableCount(): Int = dao.countAvailable()

    private fun history(
        barcode: TemporaryBarcodeEntity,
        batchId: String,
        action: String,
        at: Long,
    ) = TemporaryBarcodeHistoryEntity(
        id = UUID.randomUUID().toString(),
        barcodeId = barcode.id,
        barcodeCode = barcode.code,
        batchId = batchId,
        action = action,
        occurredAt = at,
    )

    private fun nextCode(): String {
        val body = buildString(12) {
            append("29")
            repeat(10) { append(random.nextInt(10)) }
        }
        val sum = body.mapIndexed { index, char ->
            char.digitToInt() * if (index % 2 == 0) 1 else 3
        }.sum()
        return body + ((10 - sum % 10) % 10)
    }
}
