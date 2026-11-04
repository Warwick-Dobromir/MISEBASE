# MB-S4-05 计算记录业务接线说明

**文档编号**：MB-S4-05
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-05
**范围**：`calculation_record` / `calculation_record_line` 的业务接线、不可变性与回放

---

## 1. 触发时点（MB-D-017）

计算记录只在用户明确动作时写入：

| 动作 | 写入内容 | 是否扣库存 |
|---|---|---|
| 点击“生成采购清单” | 确认记录 + 需求行 | 否 |
| 点击“确认扣减” | 确认记录 + 需求行 + 库存流水 | 是 |

预览/计算只产生 `PREVIEW` 对象，不落库。

---

## 2. 写入顺序

### 2.1 生成采购清单

```text
TransactionRunner.write
  ├─ BuildRequirementPlanUseCase（展开/汇总/缺口/清单/记录预览）
  ├─ record.confirm(now)
  └─ CalculationRecordRepository.saveConfirmed
```

### 2.2 确认扣减

```text
TransactionRunner.write
  ├─ BuildRequirementPlanUseCase（重新读取库存后构建计划）
  ├─ 必需缺口检查（有缺口返回 CUL-INV-003）
  ├─ record.confirm(now) → saveConfirmed
  ├─ 需求行 → ProductRequirement（换算到商品库存单位）
  ├─ InventoryDeductionPlanner（FEFO 分配）
  ├─ InventoryRepository.appendIfAbsent × N
  └─ 返回 appliedEntryCount
```

任一步失败，Room 事务回滚；不会留下“记录已确认但库存未扣”的半成品。

---

## 3. 不可变性

- 领域模型所有字段 `val`，无修改方法；
- `CalculationRecord.confirm` 只接受 `PREVIEW`；重复确认返回 `CUL-INV-003`；
- Repository 只接受 `CONFIRMED`；同 ID 再次保存返回 `CUL-CON-001`；
- `payload_hash` 唯一；重复提交返回既有记录；
- 数据库触发器拒绝 `calculation_record` / `calculation_record_line` 的 UPDATE/DELETE。

---

## 4. payload hash

规范串：

```text
engineVersion
recipeVersionId
servings
ingredientId|amount|unitCode|optional
...
```

- 不含记录 ID、生成时间；
- 同一配方版本、份数、需求行、引擎版本得到同一 hash；
- 引擎版本本阶段为 `stage4-1`（酱汁图算法变更后递增）。

---

## 5. 回放

- `ListCalculationRecordsUseCase(recipeVersionId)` 按版本列历史；
- `GetCalculationRecordUseCase(recordId)` 按 ID 回放；
- 页面展示确认时间、payload hash、需求行（食材名、数量、单位、可选标记）；
- 回放只读历史快照，不重新展开当前配方，也不修改库存。

---

## 6. 事务边界

- 业务事务在 Use Case，Repository 不自开事务；
- `CalculationRecordDao.insertConfirmed` 的 `@Transaction` 在 Room 外层事务内可重入；
- 新增 `test(data): cover calculation snapshot writes inside an outer transaction` 验证嵌套写入；
- 写屏障仍由 `RoomTransactionRunner` 唯一持有。

---

## 7. 测试

- 同输入 payload hash 稳定；份数/引擎/行变化 hash 变化；
- 确认后不可变、重复确认拒绝；
- 同 ID 不覆盖、同 payload 去重；
- 生成清单落一条记录；
- 缺口确认不写记录/流水；
- 覆盖扣减写记录 + 流水；
- 记录列表与按 ID 回放；
- 外层事务内写入。

---

## 8. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-05 | 计算记录业务接线与回放说明 | 接管工程组 |
