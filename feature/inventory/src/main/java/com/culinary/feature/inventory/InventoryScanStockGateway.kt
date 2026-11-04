package com.culinary.feature.inventory

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.scan.ScanImportKey
import com.culinary.core.domain.scan.ScanStockGateway
import com.culinary.core.domain.scan.ScanStockReceipt
import com.culinary.core.domain.scan.ScanStockRequest
import com.culinary.core.domain.usecase.inventory.RecordStockReceiptUseCase
import com.culinary.core.domain.usecase.inventory.StockReceiptInput
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 扫码导入的"入库"接口实现（MB-D-004 §2.3 / MB-S5-002）。
 *
 * 归属明确：库存批次、流水与商品创建归 `feature:inventory`；`feature:scan` 只调用本接口。
 * - 幂等键 = `scan-import:<条码>:<operation_id>`：**一次确认一个键**，
 *   同一商品的多次入库互不拦截，同一次确认的重试命中重放；
 * - 事务由既有 `RecordStockReceiptUseCase` 负责：批次 + 流水同一事务；
 * - 商品缺失时由该用例按食材补建，不在这里另写一套库存逻辑。
 */
@Singleton
class InventoryScanStockGateway
    @Inject
    constructor(
        private val recordReceipt: RecordStockReceiptUseCase,
    ) : ScanStockGateway {
        override suspend fun receive(request: ScanStockRequest): Outcome<ScanStockReceipt> {
            if (!request.amount.isFinite() || request.amount <= 0.0) {
                return Outcome.failure(
                    CulinaryError.Validation(field = "quantity", rule = "must be a finite positive number"),
                )
            }
            if (request.operationId.isBlank()) {
                return Outcome.failure(
                    CulinaryError.Validation(field = "operationId", rule = "must not be blank"),
                )
            }
            val input =
                StockReceiptInput(
                    quantity = Quantity(request.amount, request.target.unit),
                    ingredientId = request.target.ingredient.id,
                    note = request.note,
                    idempotencyKey = ScanImportKey.of(request.capture, request.operationId),
                )
            return when (val result = recordReceipt(input)) {
                is Outcome.Failure -> result
                is Outcome.Success ->
                    Outcome.success(
                        ScanStockReceipt(
                            entry = result.value.entry,
                            item = result.value.item,
                            idempotentReplay = result.value.idempotentReplay,
                        ),
                    )
            }
        }
    }
