package com.culinary.core.domain.model

/**
 * 实体状态（A06 §2）。
 *
 * `ARCHIVED` 不等于删除：历史引用与库存仍然保留（D-020）。
 */
enum class EntityStatus {
    ACTIVE,
    ARCHIVED,
}
