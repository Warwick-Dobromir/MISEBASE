package com.culinary.core.domain.model

/** 版本状态（A06 §2，D-006）。已发布版本不可编辑，新编辑产生新版本。 */
enum class VersionStatus {
    DRAFT,
    PUBLISHED,
    SUPERSEDED,
}
