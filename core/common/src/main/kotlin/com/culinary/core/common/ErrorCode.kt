package com.culinary.core.common

/**
 * 稳定错误码（E03 §3.1 / §3.2）。
 *
 * 格式 `CUL-<DOMAIN>-<NNN>`；三位数字一经分配不得复用。
 * [retryable] 是该错误的默认语义，具体失败可在 [CulinaryError] 中覆盖。
 */
enum class ErrorCode(
    val value: String,
    val retryable: Boolean,
) {
    VAL_001("CUL-VAL-001", retryable = false),
    NF_001("CUL-NF-001", retryable = false),
    CON_001("CUL-CON-001", retryable = true),
    REP_001("CUL-REP-001", retryable = false),
    REP_002("CUL-REP-002", retryable = false),
    REP_003("CUL-REP-003", retryable = false),
    REP_004("CUL-REP-004", retryable = false),
    UNIT_001("CUL-UNIT-001", retryable = false),
    UNIT_002("CUL-UNIT-002", retryable = false),
    INV_001("CUL-INV-001", retryable = true),
    INV_002("CUL-INV-002", retryable = true),
    INV_003("CUL-INV-003", retryable = false),
    PLAN_001("CUL-PLAN-001", retryable = false),
    PLAN_002("CUL-PLAN-002", retryable = false),
    PO_001("CUL-PO-001", retryable = false),
    PO_002("CUL-PO-002", retryable = false),
    DB_001("CUL-DB-001", retryable = true),
    DB_002("CUL-DB-002", retryable = false),
    DB_003("CUL-DB-003", retryable = false),
    FILE_001("CUL-FILE-001", retryable = true),
    FILE_002("CUL-FILE-002", retryable = false),
    SCAN_001("CUL-SCAN-001", retryable = false),
    SCAN_002("CUL-SCAN-002", retryable = true),
    BACKUP_001("CUL-BACKUP-001", retryable = false),
    BACKUP_002("CUL-BACKUP-002", retryable = false),
    BACKUP_003("CUL-BACKUP-003", retryable = true),
    BACKUP_004("CUL-BACKUP-004", retryable = false),
    IMPORT_001("CUL-IMPORT-001", retryable = false),
    UNKNOWN_001("CUL-UNKNOWN-001", retryable = false),
}
