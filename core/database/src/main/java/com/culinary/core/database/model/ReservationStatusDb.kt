package com.culinary.core.database.model

/** 预留状态（A06 §2）；ACTIVE 之外都视为已释放或已消耗。 */
enum class ReservationStatusDb {
    ACTIVE,
    RELEASED,
    CONSUMED,
}
