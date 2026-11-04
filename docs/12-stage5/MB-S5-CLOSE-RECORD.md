# MB-S5-CLOSE-001 Stage 5 关闭记录

**文档编号**：MB-S5-CLOSE-001
**版本**：`1.0.1`
**状态**：`CLOSED`
**日期**：2026-11-05
**关闭对象**：Stage 5 扫码导入纵向切片（MB-S5-001）与「同码多次入库」修正（MB-S5-002）
**审查结论**：**通过**
**范围**：仅关闭记录、执行报告状态、S0-04 风险登记与文档索引；不改业务代码、Schema/Entity/迁移、备份/恢复、导航、模块集或 Compose 页面；**不进入 Stage 6**

---

## 1. 审查结论

Stage 5 **通过**：

- **主体完成**：新建第 14 个模块 `feature:scan`；扫码页（CameraX 预览、扫描框、手电筒、返回、权限说明/拒绝/无相机三态、手工输入兜底）、ML Kit 17.3.0 Bundled 条码/二维码识别、相同码去抖与一次性消费、识别结果接口（`feature:products` / `feature:inventory`）、导入确认与幂等、N1 第五入口切换全部落地；
- **无重大范围偏离**：未修改 Schema/Entity/表结构/迁移/数据库文件；未修改备份/恢复实现；未使用 `purchase_order*`；休眠表 0 处新引用；未启用 CI、未创建签名；
- **缺陷已修复**：审查指出的"同一条码只能导入一次"（MB-S5-01 首版 R1）已由 MB-S5-002 修复为**事件级幂等键**，并经 JVM/UI/真机复验，无需回退；
- **质量门禁通过**：**817 次 JVM 测试执行 / 514 唯一用例 / 0 失败**；Debug APK 构建成功；`ktlintCheck`、`detekt` 全仓通过；模块依赖、违规注入、休眠表、文档一致性脚本 PASS；
- **真机验证完成**：vivo V2429A / Android 16 / API 36，覆盖相机权限三态、条码/二维码解码、连续识别、重复识别、再次入库、取消、快速连点与异常；
- **保留项已记录并接受**，见 §4；
- **Stage 6 前置未满足**：MB-D-006 / MB-D-010 / MB-D-012 仍为 `PROPOSED`，见 §6。

本结论把 Stage 5 置为 `CLOSED`，**不构成 Stage 6 开发授权**。

---

## 2. 关闭前 HEAD

| 项 | 值 |
|---|---|
| 分支 | `misebase/main` |
| 关闭前实际 HEAD（执行前） | `e2c7e9866ee3a4625424c8f491af069402898481` |
| 该 HEAD 提交信息 | `docs(stage5): 重复入库修正报告与证据（MB-S5-002）` |
| 该 HEAD 状态 | MB-S5-01 为 `Review`（1.0.1）；MB-S5-02/03/04/05/06/07/08 均为 `Review`；Stage 5 检查点 `checkpoint/misebase-stage5-20261105` 已创建；工作树为空 |
| 该 HEAD 的 Schema | v2，33 表；未变更 |
| 该 HEAD 的模块集 | 14（含 `feature:scan`） |
| 该 HEAD 的测试口径 | 817 执行 / 514 唯一 JVM 用例 / 0 失败 |

关闭动作在此 HEAD 之上只增加文档；不改变代码、Schema、备份、导航或模块集。

---

## 3. 关闭提交与标签

### 3.1 关闭提交

| 项 | 值 |
|---|---|
| 关闭提交完整哈希 | `9b773da142746c08348bfc22ed5fe6d6e31a6305` |
| 提交信息 | `docs(stage5): close stage 5 after review` |
| 提交内容 | 本关闭记录、MB-S5-01 状态 `CLOSED` 与审查补记、S0-04 风险更新、文档索引 |
| 变更类型 | 仅文档 |

### 3.2 关闭标签

| 项 | 值 |
|---|---|
| 关闭标签 | `checkpoint/misebase-stage5-closed-20261105` |
| Stage 6 起点标签 | `checkpoint/misebase-stage6-start-20261105` |
| 类型 | annotated tag |
| 对象哈希（关闭标签） | `12e82e87574c0c97cb532cb73035ba118f2bbf27` |
| 解析提交（关闭标签） | `9b773da142746c08348bfc22ed5fe6d6e31a6305` |
| 对象哈希（Stage 6 起点） | `7646eca9ce465a6130c99d215266fa1e5395c6ea` |
| 解析提交（Stage 6 起点） | `9b773da142746c08348bfc22ed5fe6d6e31a6305` |

两个标签均标记「Stage 5 审查关闭」这一文档提交坐标；**Stage 6 起点标签不表示 Stage 6 已启动或已获授权**。

### 3.3 标签完整性（关闭前核对）

| 项 | 值 |
|---|---|
| `framework-0.1.0` | `2b523de4b87e37a2eb08b4c7c851549350ad811a`，未移动 |
| `checkpoint/misebase-decisions-stage5-20261105` | `450dc9ec61b43c6c6ae202c511c0c07ac3ba02b2`，未移动 |
| `checkpoint/misebase-stage5-20261105` | `0184a1496c17eb267ffcb6a0002707e9fd8368f2`（对象 `42e417f2fe0e0521a1ea1409c6fe95d18b818b00`），未移动 |
| `checkpoint/misebase-stage4-closed-20261105` | 未移动 |
| 既有 `checkpoint/*` / `baseline/*` | 全部未移动（关闭只新增两个标签） |

### 3.4 补记（提交与标签回填）

> 本节由关闭提交之后的 `docs(stage5): record stage 5 close commit and tags` 提交回填；
> 关闭记录正文（§1–§3.3）与关闭标签指向同一提交坐标（`9b773da`）。

| 项 | 值 |
|---|---|
| 关闭提交完整哈希 | `9b773da142746c08348bfc22ed5fe6d6e31a6305` |
| 关闭标签对象哈希 | `12e82e87574c0c97cb532cb73035ba118f2bbf27` |
| 关闭标签解析提交 | `9b773da142746c08348bfc22ed5fe6d6e31a6305` |
| Stage 6 起点标签对象哈希 | `7646eca9ce465a6130c99d215266fa1e5395c6ea` |
| Stage 6 起点标签解析提交 | `9b773da142746c08348bfc22ed5fe6d6e31a6305` |
| 打标签时间 | 2026-11-05 02:37:23 +0800（关闭标签）、02:37:24 +0800（Stage 6 起点） |
| 标签数量 | 关闭前 50 → 关闭后 52（仅新增两个标签） |

---

## 4. 保留项

| # | 项 | 状态 | 说明 / 后续 |
|---|---|---|---|
| R1 | 同一条码只能导入一次 | **已修复**（MB-S5-002，2026-11-05） | 事件级幂等键 `scan-import:<条码>:<operation_id>`；同一次确认重试复用、不同次确认新键；真机验证再次入库、取消、连点 |
| F1 | 进程被杀丢失 `operation_id` | 保留（低） | `operation_id` 仅在 ViewModel 内存；重启后用新键重试。若上一次其实已写库，用户再确认会新增一条（语义等同"又买了一件"）。后续可存 `SavedStateHandle`，需另行授权 |
| F2 | 写前查重为全表扫描 | 保留（低） | `LookupScanImportUseCase` 按前缀过滤 `ledger()`；数据量增长后改为前缀索引查询（涉及 `core:data`/Schema，需另立任务） |
| F3 | 备份调度测试偶发 | 已登记（中） | `WorkManagerBackupSchedulerTest > enqueues an immediate backup and returns its id` 偶发 `FAILED`；根因为测试未安装 fake `AutoBackupRunner`、worker 以 `runner_unavailable` 返回 failure，结果取决于 worker 是否先于断言完成。Stage 4 已登记，Stage 5 复现（检查点 `0184a14` 上同样可复现），**未修改备份/平台实现**；建议单独立项，最小修复是按 `SchedulerWorkerIntegrationTest` 用 `AutoBackupWorkerDependencies.installForTest(...)` 注入 fake runner |
| F4 | 二维码实物取景未人工复测 | 保留（低） | 解码能力用同一 ML Kit Bundled 客户端在真机验证 4/4（EAN-13 / Code128 / QR 文本 / QR URL）；"把实物二维码举到镜头前"留待 Stage 6 真机验收复测 |

其他沿用项（不属 Stage 5 新增）：条码→商品映射未持久化（`barcode` 表 0 行，需新授权）、跨模块未用单事务（明确补偿）、新建食材默认单位 `g`、`feature:scan` 与 `core:platform` 解码器存在少量适配重复。

---

## 5. 关闭前验证

| 项 | 结果 |
|---|---|
| `.\gradlew.bat test assembleDebug ktlintCheck detekt --console=plain --no-daemon` | BUILD SUCCESSFUL |
| 唯一 JVM / 执行 / 失败 | **514 / 817 / 0** |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk`，39,854,267 字节，SHA-256 `F1EB9F09F07FFAE535921F38EBFE7B7F5444814F069A30F0B146B46978BB4F10` |
| 真机（vivo V2429A / API 36） | 权限三态、相机预览、手电筒、条码识别（EAN-13）、手工输入、确认/再次入库、取消、快速连点、返回、稳定性 全部通过；解码 instrumentation 4/4 |
| 模块依赖 / 违规注入 / 休眠表 / 文档一致性 | PASS / PASS / PASS / PASS |
| Schema / 备份 / 技术栈 / 模块集变更 | 无（`git diff` 为空） |

---

## 6. Stage 6 前置条件（必须签收后才能启动）

| # | 前置 | 当前状态 | 说明 |
|---|---|---|---|
| 1 | **MB-D-006 验收与测试口径** | `PROPOSED`（未签收） | Stage 6 的验收口径、测试计数方式、历史报告追认方式需负责人签字；未签收前不得改写历史口径 |
| 2 | **MB-D-010 远端 CI** | `PROPOSED`（未签收） | 未启用任何远端 CI；如 Stage 6 需要门禁自动化，必须先签收并单独授权 |
| 3 | **MB-D-012 发布签名策略** | `PROPOSED`（未签收） | 未创建/未变更 Release 签名；正式发布前必须签收 |
| 4 | MB-D-011 真机验证 | `ACCEPTED`（已执行） | Stage 5 真机记录在案；Stage 6 仍需按阶段重新验证并保留记录 |
| 5 | F3 备份测试偶发 | 已登记 | 建议 Stage 6 稳定性/回归任务中处理 |

> Stage 6 起点标签仅记录坐标，不构成上表任何一项的签收或豁免。

---

## 7. 停止声明

Stage 5 已关闭（`CLOSED`），**未进入 Stage 6**，**未替负责人签收**任何决策；
MB-D-006 / MB-D-010 / MB-D-012 仍为 `PROPOSED`。

---

## 8. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-05 | 记录 Stage 5 审查通过、关闭前 HEAD、关闭标签、保留项（R1/F1–F4）与 Stage 6 前置条件 | MISEBASE AI 开发工作区 |
| 1.0.1 | 2026-11-05 | 回填关闭提交哈希与两个标签对象哈希/解析提交 | MISEBASE AI 开发工作区 |

