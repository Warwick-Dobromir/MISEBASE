package com.culinary.core.data.kitchen

import com.culinary.core.common.Outcome
import kotlinx.coroutines.flow.Flow

/** app 可见的三类新增参考资料类型；不暴露 domain 枚举。 */
enum class KitchenReferenceType {
    TECHNIQUE,
    PRODUCTION_STANDARD,
    STORAGE_STANDARD,
}

/** app 编辑/详情使用的只读投影。 */
data class KitchenReferenceRecord(
    val id: String,
    val type: KitchenReferenceType,
    val name: String,
    val category: String?,
    val description: String?,
    val englishName: String? = null,
    val seedNote: String? = null,
    val isSeed: Boolean = false,
)

/**
 * 新增参考资料的 app 端口。
 *
 * 内部由 core:data 调用 domain 用例，app 不直接接触 domain/DAO。
 */
interface KitchenReferenceStore {
    fun observe(type: KitchenReferenceType): Flow<List<KitchenReferenceRecord>>

    suspend fun get(id: String): Outcome<KitchenReferenceRecord>

    suspend fun save(
        type: KitchenReferenceType,
        id: String?,
        name: String,
        category: String?,
        description: String?,
        englishName: String? = null,
    ): Outcome<String>

    suspend fun archive(id: String): Outcome<Unit>
}
