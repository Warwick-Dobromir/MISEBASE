package com.culinary.core.common

/**
 * 全项目统一的失败模型（E03 §2.1）。
 *
 * 可预期失败返回 [CulinaryError]；编程错误用 `require`/`check` 抛异常，由全局边界记录。
 * `cause` 只用于日志与诊断，不直接展示给用户。
 */
sealed interface CulinaryError {
    val code: ErrorCode
    val retryable: Boolean
    val cause: Throwable?
    val context: Map<String, String>

    /** 输入不合法：字段与规则必须明确，便于指出问题。 */
    data class Validation(
        val field: String,
        val rule: String,
        override val code: ErrorCode = ErrorCode.VAL_001,
        override val cause: Throwable? = null,
        override val context: Map<String, String> = emptyMap(),
    ) : CulinaryError {
        override val retryable: Boolean = false
    }

    /** 实体不存在或已被删除。 */
    data class NotFound(
        val entity: String,
        val id: String,
        override val code: ErrorCode = ErrorCode.NF_001,
        override val cause: Throwable? = null,
        override val context: Map<String, String> = emptyMap(),
    ) : CulinaryError {
        override val retryable: Boolean = false
    }

    /** 并发修改、唯一约束冲突等。默认不自动重试，由调用方决定刷新后重试。 */
    data class Conflict(
        val entity: String,
        override val code: ErrorCode = ErrorCode.CON_001,
        override val retryable: Boolean = true,
        override val cause: Throwable? = null,
        override val context: Map<String, String> = emptyMap(),
    ) : CulinaryError

    /** 业务规则拒绝：循环、单位不可换算、库存不足、状态不允许等。 */
    data class Domain(
        val detail: DomainDetail,
        override val code: ErrorCode,
        override val retryable: Boolean = false,
        override val cause: Throwable? = null,
        override val context: Map<String, String> = emptyMap(),
    ) : CulinaryError

    /** 数据库、文件、序列化等数据层失败。 */
    data class Data(
        val message: String,
        override val code: ErrorCode = ErrorCode.DB_001,
        override val retryable: Boolean = code.retryable,
        override val cause: Throwable? = null,
        override val context: Map<String, String> = emptyMap(),
    ) : CulinaryError

    /** 相机、SAF、权限等平台能力失败。 */
    data class Platform(
        val message: String,
        override val code: ErrorCode = ErrorCode.SCAN_001,
        override val retryable: Boolean = code.retryable,
        override val cause: Throwable? = null,
        override val context: Map<String, String> = emptyMap(),
    ) : CulinaryError

    /** 备份、恢复、导入等传输过程失败；通常不可重试，需用户换文件或升级。 */
    data class Transfer(
        val message: String,
        override val code: ErrorCode = ErrorCode.BACKUP_001,
        override val retryable: Boolean = false,
        override val cause: Throwable? = null,
        override val context: Map<String, String> = emptyMap(),
    ) : CulinaryError

    /** 未预期异常；必须记录堆栈并导出诊断，不能伪装成业务错误。 */
    data class Unexpected(
        val message: String,
        override val code: ErrorCode = ErrorCode.UNKNOWN_001,
        override val retryable: Boolean = false,
        override val cause: Throwable? = null,
        override val context: Map<String, String> = emptyMap(),
    ) : CulinaryError
}
