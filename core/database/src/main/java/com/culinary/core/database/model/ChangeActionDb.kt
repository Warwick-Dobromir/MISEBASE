package com.culinary.core.database.model

/** 变更动作（A06 §2）。 */
enum class ChangeActionDb {
    CREATE,
    UPDATE,
    DELETE,
    PUBLISH,
    CONFIRM,
    RECEIVE,
    ADJUST,
    IMPORT,
    RESTORE,
}
