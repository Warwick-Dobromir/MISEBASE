# MB-S2-04 Repository 契约与实现说明

**文档编号**：MB-S2-04
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04

---

## 1. 端口总览

| 端口 | 方法 | 失败语义 |
|---|---|---|
| `RecipeRepository` | `observeActive`、`getVersion`、`saveDraft`、`savePublished` | `getVersion` 缺失/不可映射返回 `null`；`savePublished` 返回 `Outcome` |
| `InventoryRepository` | `listItems`、`findItem`、`findByIdempotencyKey`、`appendIfAbsent`、`ledger` | 单位缺失 `CUL-NF-001`；负库存 `CUL-INV-003`；并发 `CUL-CON-001` |
| `CalculationRecordRepository` | `saveConfirmed`、`find`、`findByRecipeVersion`、`findByPayloadHash` | `PREVIEW` 拒绝 `CUL-INV-003`；同 ID 覆盖 `CUL-CON-001`；同 payload 幂等 |
| `SauceRepository` | `observeActive`、`getVersion`、`saveDraft` | 缺失 `CUL-NF-001` |
| `IngredientRepository` / `UnitRepository`（既有） | 未改签名 | 既有语义 |

## 2. 基础实现

| 实现 | 依赖 DAO | 说明 |
|---|---|---|
| `RecipeRepositoryImpl` | `RecipeDao`、`RecipeVersionDao`、`RecipeComponentDao`、`UnitDao` | 组件单位逐个加载；`SAUCE` 目标返回 `CUL-REP-004`；发布时先清旧当前指针再插入 |
| `InventoryRepositoryImpl` | `InventoryBatchDao`、`InventoryLedgerDao`、`UnitDao` | 追加顺序：查幂等键 → 校验余额 → 原子调整批次 → 插入流水 |

### 2.1 DAO 新增（仅查询/受约束更新）

| DAO | 方法 | 用途 |
|---|---|---|
| `RecipeDao` | `listActive()` | 档案列表 |
| `InventoryBatchDao` | `listActive()` | 批次快照 |
| `InventoryBatchDao` | `applyDeltas(id, onHandDelta, reservedDelta, updatedAt)` | 带非负/预留约束的原子余额调整；返回 0 表示失败 |
| `InventoryLedgerDao` | `findByIdempotencyKey(key)` | 幂等检查 |
| `InventoryLedgerDao` | `listAll()` | 账本读取（测试/审计） |

**未修改**：Entity 字段、表结构、Schema 版本、迁移、数据库文件、备份/恢复实现。

## 3. fake

位于 `core:data/src/test/java/com/culinary/core/data/fake/FakeRepositories.kt`：

| fake | 行为 |
|---|---|
| `InMemoryRecipeRepository` | 草稿/版本存储；`savePublished` 只维护一个当前版本 |
| `InMemoryInventoryRepository` | 幂等追加；负库存返回 `CUL-INV-003`；流水只追加 |
| `InMemoryCalculationRecordRepository` | 仅接受 `CONFIRMED`；同 ID 冲突；同 payload 去重 |
| `InMemorySauceRepository` | 草稿保存与缺失查询 |

fake 与实现共享同一组契约断言（见 §4），供 Stage 3/4 用例测试直接复用。

## 4. 契约测试

`core:data/src/test/.../RepositoryContractTest.kt`（7 例，Robolectric + 内存 Room）：

1. 草稿保存后读回：数量、单位、目标、产量；
2. 发布两次后仅一个当前版本，旧版本内容仍可读；
3. 幂等追加：同键第二次返回 `false`，余额不重复扣；
4. 负库存拒绝：`CUL-INV-003`，流水不增加；
5. fake 与实现语义一致（菜谱、库存）；
6. 计算记录 fake：`PREVIEW` 拒绝、同 ID 冲突、同 payload 去重；
7. 酱汁 fake：保存/读取/缺失。

测试使用现有 Schema v1 的内存库；未引入新表、未修改迁移。

## 5. 事务边界（重要）

Repository **不自行开启事务**（E02 §4.3）。以下调用必须在 `TransactionRunner.write { }` 内：

- `InventoryRepository.appendIfAbsent`：查键、调余额、写流水必须同事务；否则并发下可能出现
  "余额已扣、流水插入冲突"的半完成状态；
- `RecipeRepository.savePublished`：清旧当前指针与插入新版本必须同事务；否则插入失败会丢失当前指针。

`appendIfAbsent` 的幂等键由 `InventoryDeductionPlanner` 确定性生成
（`referenceType:referenceId:batchId:lineIndex:allocationIndex`），DB 唯一索引是最后防线。

## 6. 无实现与后续阶段

| 端口 | 现状 | 阻塞 |
|---|---|---|
| `CalculationRecordRepository` | 契约 + fake | Schema v1 无表；**需要 Schema 变更授权**后补实现与迁移 |
| `SauceRepository` | 契约 + fake | 酱汁档案在 Stage 3 落地；表已存在，无需 Schema 变更 |

## 7. 给 Stage 3/4 的约束

1. 不得绕过端口直接操作 DAO/Entity；
2. 库存余额只能通过 `appendIfAbsent` 改变，禁止 `UPDATE inventory_batch` 直改；
3. 已发布版本不可修改；编辑必须产生新版本（D-006）；
4. 计算记录一旦确认只能追加查询，不得覆盖；
5. 新增持久化能力（计算记录、servings）必须先取得 Schema 变更授权并走迁移规范。
