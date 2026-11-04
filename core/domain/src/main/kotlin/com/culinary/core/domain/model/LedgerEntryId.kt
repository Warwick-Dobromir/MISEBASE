package com.culinary.core.domain.model

/** 库存流水标识；流水只追加，标识一经写入不得复用（A06 §6.3）。 */
@JvmInline
value class LedgerEntryId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ledger entry id must not be blank" }
    }

    override fun toString(): String = value
}
