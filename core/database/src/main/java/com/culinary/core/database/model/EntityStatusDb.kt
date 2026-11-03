package com.culinary.core.database.model

/** 数据库层的实体状态（A06 §2），与领域 `EntityStatus` 一一对应。 */
enum class EntityStatusDb {
    ACTIVE,
    ARCHIVED,
}
