# CULINARY G1 最终复核包

**文档编号**：R05（复核包，不计入 17 项正式交付物）
**版本**：`0.1.0`
**状态**：`Review`
**日期**：2026-11-04
**架构基线**：`0.3.0`
**用途**：项目负责人对 D-004、D-011 做最终复核，并决定是否整体签收 G1

---

## 1. 当前结论

- **G1 = PENDING**，尚未整体签收。
- D-001/002/003/006/008/009：已单项接受。
- D-004：已按“15 模块”修订，待复核。
- D-011：已按 7 项备份标准硬化，待复核。
- G5（Release 签名）：已转为发布门禁，不阻塞框架；**未创建 keystore，未标记完成**。
- 所有正式文档仍为 `Review`；ADR 仍为 `Proposed`。

## 2. D-004：15 模块方案

完整职责表、依赖图、无环检查见 [A03A](../02-architecture/A03A-MODULE-DECOMPOSITION-REVIEW.md)。

```text
app
core:common
core:domain          # 含 model / ports / usecases / rules
core:database
core:data
core:designsystem
core:platform
feature:ingredients
feature:sauces
feature:recipes
feature:products
feature:inventory
feature:planning
feature:procurement
feature:settings
```

关键边界：

- `core:model` 并入 `core:domain`，用包级 `model/rule/usecase/repository` 保持边界；
- 酱汁、菜谱保持独立 feature，通过 domain 端口与版本引用交互；
- 商品、库存保持独立 feature，通过 `product_id` 与 domain 端口交互；
- feature 之间零依赖；UI 不依赖 data/database；
- database 不依赖 domain；domain 不依赖 Android/Room/Compose。

检查结果：

- 15 节点、43 条依赖边；
- 拓扑排序成功，**无环（DAG）**；
- 证据：[module-dependency-check.txt](../02-architecture/module-dependency-check.txt)。

**复核问题：** 是否接受 15 模块、model 并入 domain、两对业务模块保持独立？

## 3. D-011：备份 7 项标准落点

| 要求 | E05 落点 | 关键设计 |
|---|---|---|
| 数据库一致性快照 | 第 6 节 | 优先 SQLite Online Backup API；否则写屏障 + checkpoint + 复制；不允许写入中直接复制 |
| 图片及附件完整性 | 第 7 节 | 逐文件路径/大小/MIME/SHA-256；缺失/损坏/孤立分别处理，不静默跳过 |
| 备份清单 manifest | 第 4 节 | 格式/软件/schema/时间/DB 清单/附件清单/大小/hash/数量/加密参数/完整性状态 |
| manifest 自身完整性 | 第 4.3 节 | `manifest.sha256` + `checksums_sha256`；加密时 GCM tag |
| 版本兼容 | 第 4.4、12.1 节 | 格式与 schema 独立；旧备份需已验证迁移路径；未来版本拒绝 |
| 校验机制 | 第 5 节 | 逐文件 SHA-256；加密 GCM 认证；校验顺序固定 |
| 恢复前验证 | 第 12 节 | 结构/路径/大小/版本/迁移/哈希/DB/附件/空间 |
| 失败回滚 | 第 13 节 | 预恢复备份 + staging + journal + rollback；迁移失败不破坏当前库 |

完整文档：[E05 备份与恢复](../03-engineering/E05-BACKUP-RESTORE.md)。

**复核问题：** 这 7 项（含 manifest 自校验）是否满足你的标准？

## 4. G5：发布门禁（不阻塞框架）

| 规则 | 状态 |
|---|---|
| Debug/Release 严格隔离 | 已写入 E04/R02 |
| Release keystore 不入 Git | 已写入 |
| 密码不硬编码 | 已写入 |
| 发布前创建 keystore | 已写入 |
| 至少两份独立加密备份 | 已写入 |
| 验证密钥/密码/证书指纹 | 已写入 |
| Release 签名验证纳入发布清单 | 已写入 |
| 当前是否已创建 | **否**；未标记完成 |

## 5. 签收选项

### 选项 A：整体签收 G1（推荐）

条件：

- 接受 15 模块方案；
- 接受 E05 的 7 项备份合规矩阵；
- 接受 G5 为发布门禁。

签收后动作：

1. 在 [决策登记册](../00-governance/DECISION-REGISTER.md) 记录 G1 整体签收；
2. 把 G1 标为 `PASS`；
3. 更新 R03/R04/DELIVERY-MANIFEST；
4. 打标签 `checkpoint/g1-approved-<date>`；
5. 进入 R01 阶段 1。

### 选项 B：有条件签收

- 指出仍需修改的文件/条款；
- G1 保持 `PENDING`；
- 修订后再复核。

### 选项 C：暂不签收

- 保持 `Review`/`PENDING`；
- 不创建正式工程。

## 6. 签收记录

| 项 | 决定（接受/修改/拒绝） | 日期 | 备注 |
|---|---|---|---|
| D-004 15 模块 |  |  |  |
| D-011 E05 7 项 |  |  |  |
| G1 整体签收 |  |  |  |
| G5 发布门禁 |  |  |  |

**项目负责人签名**：____________________
**日期**：____________________

## 7. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 建立 G1 最终复核包；汇总 D-004/D-011/G5 证据与签收选项 | 架构组 |
