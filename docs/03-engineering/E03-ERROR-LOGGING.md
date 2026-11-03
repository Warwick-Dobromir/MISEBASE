# CULINARY 异常处理与日志规范

**文档编号**：E03
**版本**：`0.1.0`
**状态**：`Review`
**日期**：2026-11-04
**负责人**：首席架构师 / 技术负责人

---

## 1. 目标

1. 任何失败都能被**定位**：有错误码、操作、实体、相关 ID、堆栈（仅日志）。
2. 任何失败都能被**理解**：用户看到可行动的提示，不是“未知错误”。
3. 任何失败都能被**恢复**：可重试、可回退、可导出诊断，不靠清空数据。
4. 不通过空 `catch`、静默忽略、伪造默认值来维持“表面正常”。

## 2. 错误分类

### 2.1 顶层类型

```kotlin
sealed interface CulinaryError {
    val code: ErrorCode
    val retryable: Boolean
    val cause: Throwable?
    val context: Map<String, String>

    data class Validation(...) : CulinaryError
    data class NotFound(...) : CulinaryError
    data class Conflict(...) : CulinaryError
    data class Domain(...) : CulinaryError
    data class Data(...) : CulinaryError
    data class Platform(...) : CulinaryError
    data class Transfer(...) : CulinaryError
    data class Unexpected(...) : CulinaryError
}
```

### 2.2 分类表

| 类型 | 例子 | 是否可重试 | 用户处理 |
|---|---|---|---|
| `Validation` | 数量 ≤ 0、名称空 | 否 | 修正输入 |
| `NotFound` | 配方版本不存在 | 否 | 返回列表/刷新 |
| `Conflict` | 并发修改、条码重复 | 是/否 | 刷新/选择已有 |
| `Domain` | 配方循环、单位不可换算、库存不足 | 视情况 | 修正配方/补货 |
| `Data` | DB busy、磁盘满、文件写失败 | 部分 | 重试/清理空间 |
| `Platform` | 相机权限拒绝、SAF 取消 | 部分 | 设置/重选 |
| `Transfer` | 备份损坏、版本不兼容、导入校验失败 | 否 | 换备份/升级 App |
| `Unexpected` | 未预期异常 | 否 | 导出诊断/重启 |

### 2.3 领域错误示例

```kotlin
sealed interface DomainError : CulinaryError {
    data class RecipeCycle(val path: List<RecipeRef>) : DomainError
    data class UnitIncompatible(
        val from: UnitRef,
        val to: UnitRef,
        val ingredient: IngredientRef?,
    ) : DomainError
    data class InsufficientStock(
        val product: ProductRef,
        val requested: Quantity,
        val available: Quantity,
    ) : DomainError
    data class InvalidState(val entity: String, val expected: String, val actual: String) : DomainError
}
```

领域错误必须携带足够上下文，便于 UI 显示“哪条配方、哪个单位、哪个批次”。

## 3. 错误码

### 3.1 格式

```text
CUL-<DOMAIN>-<NNN>
```

- `DOMAIN`：`VAL`、`NF`、`CON`、`REP`、`UNIT`、`INV`、`PLAN`、`PO`、`DB`、`FILE`、`SCAN`、`BACKUP`、`IMPORT`、`UNKNOWN`。
- `NNN`：三位数字，稳定不复用。

### 3.2 错误码表（首批）

| 错误码 | 含义 | 可重试 | 用户提示方向 |
|---|---|---|---|
| `CUL-VAL-001` | 输入不合法 | 否 | 指出字段与规则 |
| `CUL-NF-001` | 实体不存在 | 否 | 刷新/返回 |
| `CUL-CON-001` | 并发修改冲突 | 是 | 刷新后重试 |
| `CUL-REP-001` | 配方循环引用 | 否 | 显示循环路径 |
| `CUL-REP-002` | 子配方未发布 | 否 | 先发布子配方 |
| `CUL-REP-003` | 配方产量非法 | 否 | 修正产量/单位 |
| `CUL-REP-004` | 展开超深度/节点上限 | 否 | 拆分配方 |
| `CUL-UNIT-001` | 单位不可换算 | 否 | 增加换算/换单位 |
| `CUL-UNIT-002` | 换算因子无效 | 否 | 修正换算 |
| `CUL-INV-001` | 库存不足 | 是（补货后） | 显示可用量 |
| `CUL-INV-002` | 预留冲突 | 是 | 刷新后重试 |
| `CUL-INV-003` | 商品未映射食材 | 否 | 关联商品 |
| `CUL-PLAN-001` | 计划无明细 | 否 | 添加菜品 |
| `CUL-PLAN-002` | 计划状态不允许 | 否 | 刷新/新建 |
| `CUL-PO-001` | 超量收货 | 否 | 调整数量/开启超收 |
| `CUL-PO-002` | 收货单重复过账 | 否 | 刷新 |
| `CUL-DB-001` | 数据库忙/超时 | 是 | 重试 |
| `CUL-DB-002` | 数据库损坏 | 否 | 进入恢复 |
| `CUL-DB-003` | 迁移失败 | 否 | 恢复预迁移备份 |
| `CUL-FILE-001` | 文件写入失败 | 部分 | 检查空间/重试 |
| `CUL-FILE-002` | 图片文件缺失 | 否 | 按无图显示，记录诊断 |
| `CUL-SCAN-001` | 相机权限拒绝 | 否 | 设置/手工输入 |
| `CUL-SCAN-002` | 扫码识别失败 | 是 | 重试/手工输入 |
| `CUL-BACKUP-001` | 备份损坏/校验失败 | 否 | 换备份 |
| `CUL-BACKUP-002` | 备份版本不兼容 | 否 | 升级 App |
| `CUL-IMPORT-001` | 导入预检失败 | 否 | 修正文件 |
| `CUL-UNKNOWN-001` | 未预期错误 | 否 | 导出诊断 |

## 4. 统一结果类型

### 4.1 `Outcome`

```kotlin
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: CulinaryError) : Outcome<Nothing>
}
```

### 4.2 使用规则

- **可预期失败**（校验、业务规则、权限、网络/文件）返回 `Outcome.Failure`。
- **编程错误**（不可能状态、违反前置条件）用 `require`/`check` 抛 `IllegalArgumentException`/`IllegalStateException`，由全局边界记录。
- 不用 `Result<T>` 混用；全项目统一 `Outcome`。
- 不在 `Outcome` 中放堆栈给 UI；堆栈只进日志。
- `Outcome` 可以携带 `ErrorCode` 与上下文；UI 用 code 映射文案。

### 4.3 禁止

- `catch (e: Exception) { }`。
- `catch (e: Throwable) { return null }`。
- 用默认值吞掉错误（例如换算失败返回 0）。
- 在 UI 层捕获底层 SQL 异常并显示原始 message。
- 把 `CancellationException` 当成普通错误。

## 5. 分层异常边界

| 层 | 捕获 | 转换 | 记录 |
|---|---|---|---|
| Platform | `SecurityException`、`CameraException`、`ActivityNotFoundException`、SAF 异常 | `PlatformError` | 是（无敏感数据） |
| Database | `SQLiteException`、`SQLiteDatabaseCorruptException` | `DataError`（含 SQLite 错误码） | 是（SQL 摘要，不含参数值） |
| Data | 文件 IO、序列化、备份校验 | `DataError` / `TransferError` | 是（路径用相对路径） |
| Domain | 不捕获底层异常；返回业务错误 | `DomainError` | 业务错误按 WARN；不打印堆栈 |
| ViewModel | `Outcome` | `UiState.Error` | 不重复记录底层堆栈 |
| Global | 未捕获异常 | 崩溃日志 + 系统处理 | 是 |

**原则：** 异常在**最接近发生地**转换，在**边界**记录一次，避免每层重复打印。

## 6. 异常到用户提示

### 6.1 提示结构

每条用户提示包含：

1. **发生了什么**（简短）。
2. **为什么**（可选，专业用户需要）。
3. **可以怎么做**（重试/修正/恢复/联系）。
4. **错误码**（可复制）。

示例：

```text
无法确认计划：配方存在循环引用
路径：牛肉高汤 v2 → 酱汁 A v1 → 牛肉高汤 v2
错误码：CUL-REP-001
[查看配方] [返回]
```

### 6.2 禁止的提示

- “操作失败，请重试”而没有原因。
- “未知错误”。
- 直接显示 SQLite/Java 堆栈。
- 把“数据可能已损坏”轻描淡写为“加载失败”。

### 6.3 可重试与不可重试

- 可重试：显示“重试”，保留用户输入。
- 不可重试：显示修正路径，不提供无意义的重试按钮。
- 致命：进入 Recovery 页面，提供备份/导出/重启。

## 7. 日志体系

### 7.1 门面

```kotlin
interface AppLogger {
    fun debug(event: String, fields: Map<String, Any?> = emptyMap())
    fun info(event: String, fields: Map<String, Any?> = emptyMap())
    fun warn(event: String, error: Throwable? = null, fields: Map<String, Any?> = emptyMap())
    fun error(event: String, error: Throwable? = null, fields: Map<String, Any?> = emptyMap())
}
```

- 实现基于 Timber，但业务代码只依赖 `AppLogger`。
- 禁止业务代码直接 `Log.d`/`println`。
- 日志调用不阻塞主线程；文件写入异步。

### 7.2 级别

| 级别 | 用途 | Release |
|---|---|---|
| DEBUG | 开发细节、SQL 耗时 | 关闭（可按开关开启） |
| INFO | 启动、迁移、备份、计划确认等关键动作 | 开启 |
| WARN | 可恢复错误、重试、缺图、权限拒绝 | 开启 |
| ERROR | 数据损坏、迁移失败、未预期异常 | 开启 |

### 7.3 结构化字段

| 字段 | 说明 |
|---|---|
| `event` | 稳定事件名，如 `plan.confirm` |
| `correlationId` | 一次用户操作的链路 ID |
| `entityType` / `entityId` | 实体类型与 ID（不记录名称） |
| `operation` | `create` / `update` / `confirm` / `receive` |
| `durationMs` | 耗时 |
| `rowCount` | 影响行数 |
| `errorCode` | 业务错误码 |
| `schemaVersion` / `appVersion` | 版本 |
| `sqliteCode` | SQLite 错误码 |

**不记录：**

- 用户输入的完整文本、配方步骤、备注。
- 姓名、电话、地址、邮箱。
- 完整文件路径（用相对路径/哈希）。
- 条码原文（可记录哈希前缀）。
- 备份密码/密钥。

### 7.4 文件日志

- 路径：`filesDir/logs/culinary.log`。
- 滚动：单文件 2 MB，保留 5 个，旧文件 gzip。
- 保留：最多 14 天 / 10 MB。
- 写入：单线程异步队列；队列满时丢弃 DEBUG，WARN/ERROR 尽量保留并计数。
- 格式：`timestamp level event key=value ...`。
- 启动时记录：app version、schema version、device model、API level、install_id 前缀。
- 不记录数据库内容。

### 7.5 崩溃日志

```kotlin
Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
    runCatching {
        crashLogger.write(thread, throwable, appInfo, recentLogs)
    }
    defaultHandler.uncaughtException(thread, throwable)
}
```

- 先落盘（同步、限时），再交回系统默认处理。
- 记录：堆栈、线程、App 版本、schema、设备、最近 200 行日志。
- 不记录用户内容。
- 防止递归崩溃：写入失败直接放弃，不再次抛异常。

### 7.6 诊断导出

- 用户主动触发；导出 ZIP：
  - 日志文件；
  - 设备/版本信息；
  - DB 表计数/大小/PRAGMA 结果；
  - 最近错误摘要；
  - **不包含**数据库内容和图片。
- 导出前提示内容范围；不上传。

## 8. 特定场景

### 8.1 数据库

- `SQLITE_BUSY`：WARN，记录重试次数；最终失败 ERROR。
- `SQLITE_CORRUPT`：ERROR，进入恢复；不删除文件。
- 迁移失败：ERROR，记录 from/to、失败语句摘要；恢复预迁移备份。
- 约束冲突：WARN，记录约束名与实体 ID；不记录字段值。

### 8.2 文件/图片

- 图片缺失：WARN，`CUL-FILE-002`；UI 按无图显示。
- 写入失败：ERROR；清理临时文件；不留下半张图。
- 磁盘满：ERROR；提示清理；阻止继续写入。

### 8.3 扫码

- 权限拒绝：INFO/WARN；提供设置与手工输入。
- 识别失败：DEBUG/WARN；不刷屏。
- 相机不可用：ERROR；降级手工输入。

### 8.4 备份/恢复

- 备份开始/成功/失败：INFO/ERROR；记录大小、耗时、SHA-256 前缀。
- 校验失败：ERROR；`CUL-BACKUP-001`；不覆盖现有数据。
- 恢复开始/成功/回滚：INFO/ERROR；记录预恢复备份 ID。

## 9. 日志与隐私

- 默认不申请网络权限；日志不离开设备。
- 诊断导出由用户主动发起。
- 日志脱敏规则集中实现，单元测试覆盖。
- 崩溃日志保留在应用私有目录；卸载即删除。
- 未来若增加云诊断，必须单独 ADR + 用户同意。

## 10. 测试要求

| 测试 | 内容 |
|---|---|
| 错误映射 | SQLite/文件/权限异常 → 正确错误码 |
| 无空 catch | 静态检查/评审 |
| 取消传播 | `CancellationException` 不被吞 |
| 日志脱敏 | 名称/电话/路径/条码不出现 |
| 日志滚动 | 大小/数量/压缩 |
| 崩溃写入 | 模拟未捕获异常，文件生成且不递归 |
| 诊断导出 | 不含 DB/图片 |
| 用户提示 | 每个错误码有文案与恢复动作 |

## 11. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 初稿；错误分类、错误码、日志、崩溃与隐私 | 架构组 |
