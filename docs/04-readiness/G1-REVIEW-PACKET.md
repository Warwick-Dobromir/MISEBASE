# CULINARY G1 最终复核包

**文档编号**：R05（复核包，不计入 17 项正式交付物）
**版本**：`0.2.0`
**状态**：`Review`
**日期**：2026-11-04
**架构基线**：`0.4.0`
**用途**：项目负责人复核 D-011/E05 0.4.0，并决定是否整体签收 G1

---

## 1. 当前结论

- **G1 = PENDING**，尚未整体签收。
- D-001/002/003/004/006/008/009：已单项接受。
- **D-004：已接受 15 模块 + backup 边界。**
- **D-011：E05 0.4.0 已按 5 项复核意见修订，仍待复核。**
- G5：已接受为发布门禁，不阻塞框架；未创建 keystore。
- 所有正式文档仍为 `Review`（A03A 已 `Approved`）；ADR 仍为 `Proposed`。

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

**D-004 已接受。** 追加边界：`core:platform` 管 SAF/权限；`core:data` 管备份格式/校验/恢复编排，不依赖 Android SAF/Uri，通过 `BackupStoragePort`/`StorageHandle` 桥接。

## 3. D-011：E05 0.4.0 五项复核修订

E05 0.3.0 暂未签收；0.4.0 已按最终意见修订：

| # | 意见 | 修订落点 |
|---:|---|---|
| 1 | manifest 自哈希循环 | 4.2/4.3/5：manifest 不含自身 hash；`manifest.sha256` 独立；`checksums.sha256` 排除自身/manifest |
| 2 | schema 方向 | 4.4/12/12.1/18：`>` 拒绝、`==` 继续、`<` 需完整已测试迁移路径 |
| 3 | 缺失图片 | 7.3/18：默认失败；仅显式继续才 `PARTIAL`；恢复再次确认 |
| 4 | journal/中断 | 13.1–13.4/18：INTENT/DONE + fsync、幂等、DB/WAL/SHM、回滚失败 Recovery |
| 5 | 快照回退 | 6.1–6.3.1/18：checkpoint 返回值、读连接策略、写屏障覆盖后台、源库健康检查、Online API 未验证不标记支持 |

完整文档：[E05 备份与恢复 0.4.0](../03-engineering/E05-BACKUP-RESTORE.md)；逐条证据：[E05A 修订复核包](../03-engineering/E05-REVISION-REVIEW.md)。

**复核问题：** E05 0.4.0 是否已正确修复上述 5 项？

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

- D-004 15 模块已接受；
- 接受 E05 0.4.0 的 5 项修订；
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
| D-004 15 模块 | 已接受 | 2026-11-04 | 含 backup 边界 |
| D-011 E05 0.4.0 |  |  | 5 项修订复核 |
| G1 整体签收 |  |  |  |
| G5 发布门禁 | 已接受 | 2026-11-04 | 不阻塞框架 |

**项目负责人签名**：____________________
**日期**：____________________

## 7. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 建立 G1 最终复核包；汇总 D-004/D-011/G5 证据与签收选项 | 架构组 |
| 0.2.0 | 2026-11-04 | D-004 接受、G5 接受；D-011 更新为 E05 0.4.0 五项复核 | 架构组 |
