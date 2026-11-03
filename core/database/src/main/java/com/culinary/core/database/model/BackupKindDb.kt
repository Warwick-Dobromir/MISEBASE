package com.culinary.core.database.model

/** 备份类型（A06 §2）。 */
enum class BackupKindDb {
    AUTO,
    MANUAL,
    PRE_MIGRATION,
    PRE_RESTORE,
}
