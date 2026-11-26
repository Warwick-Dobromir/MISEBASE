package com.culinary.core.domain.repository

interface TemporaryBarcodeRepository {
    suspend fun findForBatch(batchId: String): String?
    suspend fun assign(batchId: String): String?
    suspend fun release(batchId: String): Boolean
}