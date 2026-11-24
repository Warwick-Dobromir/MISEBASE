package com.culinary.core.domain.scan

/** 已有商品的只读识别信息；查找不产生库存或入库流水。 */
data class ScanKnownProduct(
    val productId: String,
    val name: String,
    val specification: String,
    val imagePath: String?,
)

/** 按条码查找已有商品档案的只读端口。 */
fun interface ScanKnownProductLookup {
    suspend fun findByBarcode(code: String): ScanKnownProduct?
}
