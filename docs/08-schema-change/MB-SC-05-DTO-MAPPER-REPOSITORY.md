# MB-SC-05 DTO / Mapper / Repository 变更说明

**文档编号**：MB-SC-05
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**范围**：`core:domain`（仅必要映射）、`core:data`、`core:database` 的持久化适配

---

## 1. 领域契约（最小变更）

本任务**没有**修改已接受业务规则，也没有新增计算记录页面/Use Case。

| 类型 | 变更 |
|---|---|
| `RecipeVersion` | 无签名变更；`servings: Int` 已存在，v2 起由数据库真实读出，不再固定为 1 |
| `RecipeVersionDraft` | 无变更 |
| `CalculationRecord` | 无签名变更；继续使用 `Servings`、`RequirementLine`、`payloadHash`、状态与时间字段 |
| `CalculationRecordRepository` | 无签名变更；保存仍只接受 `CONFIRMED` |
| `ConfirmCalculationRecordUseCase` | 无变更；仍不接页面 |

---

## 2. `recipe_version.servings` 映射

| 层 | 文件 | 变更 |
|---|---|---|
| Entity | `RecipeVersionEntity.kt` | 增加 `servings: Int = 1`，`@ColumnInfo(name = "servings", defaultValue = "1")` |
| Mapper | `RecipeMapper.kt` | `RecipeVersion(..., servings = version.servings)` |
| Repository | `RecipeRepositoryImpl.kt` | 写回 `servings = version.servings` |
| DAO | `RecipeVersionDao.kt` | 无接口变更；`findById/findCurrent` 自动带出新列 |

行为：

- 旧 v1 行迁移后默认 `1`，读取为 1；
- 新版本可写入非 1 份数（测试用 4）；
- 已发布版本修改 `servings` 会被发布不可变触发器拒绝；
- 非正数份数被 `CHECK` 触发器与领域 `require(servings > 0)` 双重拒绝。

---

## 3. 计算记录持久化映射

| 层 | 文件 | 变更 |
|---|---|---|
| Entity | `CalculationRecordEntity.kt` | 记录头：ID、版本 ID、份数、引擎版本、payload_hash、状态、创建/确认时间 |
| Entity | `CalculationRecordLineEntity.kt` | 需求行：记录 ID、行号、食材 ID、数量、单位 ID、可选标记 |
| DAO | `CalculationRecordDao.kt` | 查询、按 payload_hash 查询、按版本查询、事务内 insertConfirmed |
| Mapper | `CalculationRecordMapper.kt` | Entity ↔ Domain；行按 `line_no` 稳定排序 |
| Repository | `CalculationRecordRepositoryImpl.kt` | Room 实现既有契约 |
| DI | `DataModule.kt` / `RepositoryModule.kt` | 提供 DAO；绑定 `CalculationRecordRepository` |

### 3.1 Repository 语义

- `saveConfirmed` 只接受 `isConfirmed = true`，否则 `CUL-INV-003`；
- 同 ID 再次保存返回 `CUL-CON-001`，不覆盖；
- 同 `payload_hash`、不同 ID 返回既有记录，不产生第二条；
- 头与需求行在 DAO 的单个事务中写入；
- 事务内先写 `PREVIEW` 头、再写行、最后更新为 `CONFIRMED`，保证行插入时不触发“已确认行不可变”；
- `CONFIRMED` 后数据库触发器拒绝任何 UPDATE/DELETE，Repository 不提供修改方法。

### 3.2 读取

- `find(id)`、`findByPayloadHash` 不存在时返回 `Success(null)`；
- `findByRecipeVersion` 按 `created_at, id` 稳定排序；
- 行映射需要单位；单位缺失返回 `CUL-NF-001`，不静默丢弃；
- 计算记录读取路径不需要休眠表。

---

## 4. 未做事项

- 未新增 Compose 页面、导航、ViewModel、页面级 Use Case；
- 未接入计算记录业务 UI；
- 未新增 `feature:scan` 或其他模块；
- 未修改 `AutoBackupWorker`、`BackupArchiveWriter`、`RestoreExecutor`、写屏障、WAL checkpoint；
- 未修改 11 张休眠表的 Entity/DAO/列/数据；
- 未升级 Room 或任何技术栈版本。

---

## 5. 测试映射

| 测试 | 覆盖 |
|---|---|
| `CalculationRecordRepositoryTest`（4 例） | 保存/读取、预览拒绝、同 ID 冲突、同 hash 幂等、DB 不可变、hash 稳定 |
| `RepositoryContractTest.recipe repository reads servings...` | 旧行 1、新行 4、DAO 落库 |
| `CulinaryMigrationV1ToV2Test` | 迁移后计算记录表可写、servings 约束 |
| `BackupRestoreV2Test` | 恢复后 servings 与计算记录可读 |

---

## 6. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录 DTO/Mapper/Repository 适配、契约语义与未做事项 | 接管工程组 |
