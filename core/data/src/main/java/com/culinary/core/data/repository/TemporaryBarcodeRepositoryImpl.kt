package com.culinary.core.data.repository

import com.culinary.core.data.inventory.TemporaryBarcodePool
import com.culinary.core.database.dao.TemporaryBarcodeDao
import com.culinary.core.domain.repository.TemporaryBarcodeRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemporaryBarcodeRepositoryImpl @Inject constructor(
    private val dao: TemporaryBarcodeDao,
    private val pool: TemporaryBarcodePool,
) : TemporaryBarcodeRepository {
    override suspend fun findForBatch(batchId: String): String? = dao.findForBatch(batchId)?.code
    override suspend fun assign(batchId: String): String? = pool.assign(batchId)
    override suspend fun release(batchId: String): Boolean = pool.release(batchId)
}