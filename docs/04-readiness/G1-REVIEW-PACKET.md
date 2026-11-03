# CULINARY G1 最终复核包

**文档编号**：R05（复核包，不计入 17 项正式交付物）
**版本**：`0.4.0`
**状态**：`Review`
**日期**：2026-11-04
**架构基线**：`0.4.1`
**用途**：G1 技术/归档复核记录（D-004/D-011/G5）；不替代负责人签收

---

## 1. 当前结论

- **G1 技术准备/归档核验：完成。**
- **G1 正式签收：PENDING**（项目负责人/独立审查方）。
- **G2：NOT AUTHORIZED**；D-012 接受前不得开始阶段 1。
- D-001/002/003/004/006/008/009：已接受。
- **D-004：已接受 15 模块 + backup 边界。**
- **D-011：E05 0.4.1 已复核通过。**
- G5：已接受为发布门禁，不阻塞框架；未创建 keystore。
- A03A、E05 为 `Approved`；其余正式文档保留 `Review` 以跟踪非 G1 的 `PROPOSED`/`OPEN` 项。

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

## 3. D-011：E05 0.4.1 复核通过

E05 0.4.1 已按最终意见完成修订；0.4.1 复核通过：

| # | 意见 | 修订落点 |
|---:|---|---|
| 1 | manifest 自哈希循环 | 4.2/4.3/5：manifest 不含自身 hash；`manifest.sha256` 独立；`checksums.sha256` 排除自身/manifest |
| 2 | schema 方向 | 4.4/12/12.1/18：`>` 拒绝、`==` 继续、`<` 需完整已测试迁移路径 |
| 3 | 缺失图片 | 7.3/18：默认失败；仅显式继续才 `PARTIAL`；恢复再次确认 |
| 4 | journal/中断 | 13.1–13.4/18：INTENT/DONE + fsync、幂等、DB/WAL/SHM、回滚失败 Recovery |
| 5 | 快照回退 | 6.1–6.3.1/18：checkpoint 返回值、读连接策略、写屏障覆盖后台、源库健康检查、Online API 未验证不标记支持 |

完整文档：[E05 备份与恢复 0.4.1](../03-engineering/E05-BACKUP-RESTORE.md)；逐条证据：[E05A 修订复核包](../03-engineering/E05-REVISION-REVIEW.md)。

**复核结果：** E05 0.4.1 已复核通过；D-011 = ACCEPTED。

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

## 5. 复核结论

**技术/归档核验完成；G1 正式签收待定。**

- D-004 15 模块已接受；
- E05 0.4.1 已复核通过；
- G5 已接受为发布门禁；
- 8 项关键决策全部接受；
- **未执行**项目负责人正式签收；
- **G2 未授权**；D-012 接受前不得开始阶段 1。

## 6. 复核记录

| 项 | 决定 | 日期 | 备注 |
|---|---|---|---|
| D-004 15 模块 | 接受 | 2026-11-04 | 含 backup 边界 |
| D-011 E05 0.4.1 | 接受 | 2026-11-04 | 最终复核通过 |
| G1 技术/归档核验 | **完成** | 2026-11-04 | 8 项决策全部接受 |
| G1 正式签收 | **PENDING** | — | 需项目负责人/独立审查方 |
| G2 授权 | **NOT AUTHORIZED** | — | 需 G1 签收 + D-012 接受 |
| G5 发布门禁 | 接受 | 2026-11-04 | 不阻塞框架 |

**项目负责人签名**：____________________
**日期**：____________________

## 7. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 建立 G1 最终复核包；汇总 D-004/D-011/G5 证据与签收选项 | 架构组 |
| 0.2.0 | 2026-11-04 | D-004 接受、G5 接受；D-011 更新为 E05 0.4.0 五项复核 | 架构组 |
| 0.3.0 | 2026-11-04 | E05 0.4.1 复核通过；D-011 接受；G1 整体签收 | 架构组 |
| 0.4.0 | 2026-11-04 | 修正：G1 技术/归档完成但未正式签收；G2 未授权；D-012 为阶段 1 门禁 | 架构组 |
