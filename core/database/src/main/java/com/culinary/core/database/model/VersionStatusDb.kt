package com.culinary.core.database.model

/** 版本状态（A06 §2，D-006）；数据库层独立声明，由 core:data 映射到领域枚举。 */
enum class VersionStatusDb {
    DRAFT,
    PUBLISHED,
    SUPERSEDED,
}
