# MB-S1-04 休眠表 DORMANT 清单与引用扫描

**文档编号**：MB-S1-04
**版本**：`0.1.0`
**状态**：`Review`（已完成标注与扫描；**未改表结构**）
**日期**：2026-11-04
**依据**：MB-D-005（ACCEPTED）、MB-S1-001 任务书 §4.5、S0-R-12
**边界**：不删除、不迁移、不做结构性改造、不创建 Schema v2；只标注、禁止新代码引用、提供扫描。

---

## 1. 结论

- 11 张规划/采购/供应商表全部标记 `DORMANT`，保留 schema v1 结构；
- 引用扫描：**0 处允许范围外引用**；
- 允许的引用仅存在于 `core:database` 的结构定义（Entity/DAO/Database/Schema）与约束测试；
- 未修改 `1.json`、Entity、DAO、`CulinaryMigrations` 或任何数据库文件；
- 扫描可复现，且注入测试能捕获 `app` 中的违规引用。

## 2. 11 张 DORMANT 表

| # | 表 | 归属 | 处置 | 替代/说明 |
|---:|---|---|---|---|
| 1 | `supplier` | 供应商 | 保留、冻结、只读结构 | 不做供应商管理；`product.supplier_id` 保留可空 |
| 2 | `production_plan` | 计划 | 保留、冻结 | 不建独立计划模块 |
| 3 | `production_plan_item` | 计划 | 保留、冻结 | 同上 |
| 4 | `production_plan_snapshot` | 计划 | 保留、冻结 | 快照语义见 MB-D-017（PROPOSED） |
| 5 | `material_requirement` | 计划/需求 | 保留、冻结 | 需求改由 Domain 计算 |
| 6 | `material_requirement_source` | 计划/需求 | 保留、冻结 | 来源路径不落库 |
| 7 | `procurement_requirement` | 采购 | 保留、冻结 | 缺料清单归 Inventory |
| 8 | `purchase_order` | 采购 | 保留、冻结 | 不做采购单 |
| 9 | `purchase_order_item` | 采购 | 保留、冻结 | 同上 |
| 10 | `goods_receipt` | 采购/收货 | 保留、冻结 | 入库走批次 + 流水 |
| 11 | `goods_receipt_item` | 采购/收货 | 保留、冻结 | 同上 |

机器可读清单：`config/dormant-tables.json`（名称、归属、原因、替代、允许引用根）。

## 3. 规则

1. **不删除**：`fallbackToDestructiveMigration` 禁止；不删表、不删列。
2. **不迁移**：不做数据搬迁、不建 v2、不改 schema。
3. **不改造**：不改 Entity/DAO/索引/触发器/外键。
4. **禁止新代码引用**：`app`、`feature`、`core:data`、`core:domain`、`core:platform`、
   `core:designsystem` 不得引用这些表；只允许 `core:database/src/main`（结构）与
   `core:database/src/test|androidTest`（约束验证）。
5. **不写数据**：除迁移/诊断外，不向休眠表写入。
6. **未来处置另立任务**：若确认永久不需要，需 MB-D-005 之外的正式授权 + 备份 + 迁移测试。

## 4. 引用扫描结果

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-dormant-tables.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-dormant-tables.ps1 -SelfTest
```

实测（2026-11-04，扫描 248 个 Kotlin 文件）：

| 表 | 归属 | main 引用 | test 引用 | 允许范围外 | 状态 |
|---|---|---:|---:|---:|---|
| `supplier` | 供应商 | 6 | 1 | 0 | DORMANT |
| `production_plan` | 计划 | 7 | 1 | 0 | DORMANT |
| `production_plan_item` | 计划 | 4 | 0 | 0 | DORMANT |
| `production_plan_snapshot` | 计划 | 6 | 2 | 0 | DORMANT |
| `material_requirement` | 计划/需求 | 4 | 0 | 0 | DORMANT |
| `material_requirement_source` | 计划/需求 | 4 | 0 | 0 | DORMANT |
| `procurement_requirement` | 采购 | 4 | 0 | 0 | DORMANT |
| `purchase_order` | 采购 | 6 | 0 | 0 | DORMANT |
| `purchase_order_item` | 采购 | 5 | 0 | 0 | DORMANT |
| `goods_receipt` | 采购/收货 | 5 | 0 | 0 | DORMANT |
| `goods_receipt_item` | 采购/收货 | 4 | 0 | 0 | DORMANT |

**结果**：`PASS`；0 处允许范围外引用。

自检：

```text
MISEBASE dormant-table scanner self-test
  injected reference: 1 outside reference(s) detected in app
SELF-TEST PASS: injected dormant-table reference was detected.
```

## 5. 与 Schema 的关系

- 表数量仍为 **31**；休眠表 **11** 张；schema 仍为 **v1**；
- `core/database/schemas/com.culinary.core.database.CulinaryDatabase/1.json` 未改；
- 本阶段 Git 变更中不得出现 `core/database/src/main/**/entity/`、`dao/`、`schema/`、
  `CulinaryMigrations.kt`、`1.json`（除文档扫描脚本/配置外）；
- 文档标注：A05/A06 已加 `DORMANT` 说明；本文件与 `config/dormant-tables.json` 为机器可读来源。

## 6. 风险与未决

| # | 事项 | 状态 |
|---|---|---|
| D-01 | `supplier_id` 外键仍在 `product`，商品编辑未来是否展示供应商字段 | 待商品页面阶段决定；当前不做 UI |
| D-02 | `material_requirement*` 是否未来转为计算快照 | 待 MB-D-017 签收 |
| D-03 | 休眠表长期保留的存储成本 | 低；数据为空/极少，暂不处理 |
| D-04 | 若未来删除，需要 v2 迁移 + 备份 + 回滚 | 未授权，不在本阶段 |

## 7. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 建立 11 表 DORMANT 清单、引用规则、扫描与自检结果；未改表结构 | 接管工程组 |
