# MB-HOME-CLOSE-001 首页与导航重构最终关闭记录

**文档编号**：MB-HOME-CLOSE-001  
**任务**：首页与导航重构最终关闭（MB-HOME-002 收尾）  
**状态**：`CLOSED`（2026-11-05，项目负责人审查通过）  
**日期**：2026-11-05  
**适用基线**：MB-HOME-001 + MB-HOME-002（N3 / Champagne Gold）  
**关闭标签**：`checkpoint/misebase-home-closed-20261105`  
**Stage 6 起点标签**：`checkpoint/misebase-stage6-start-20261105`（已存在，未移动；见 §5）

---

## 1. 审查结论

**通过。**

项目负责人已确认 MB-HOME-002 的全部设计与实现：

- N3 底部五项：首页 / 知识库 / 扫码（中央凸起）/ 库存 / 设置；
- 设置从顶栏移到底部第 5 项，顶栏与首页 Hero 不再显示设置图标；
- 知识库聚合页以 Tab 切换酱汁 / 食材 / 菜谱，三个 feature 模块与详情/编辑路由未合并；
- Champagne Gold 正式替换 Orange（MB-D-008 Rev.1）；
- 首页真实数据、隐藏模块与“需关注”口径保持不变；
- 全量测试、ktlint、detekt、模块依赖、休眠表与文档一致性检查通过。

| 验收条件 | 结果 |
|---|---|
| 首页、香槟金、数据真实性要求 | **PASS** |
| 底部只有五项（首页/知识库/扫码凸起/库存/设置） | **PASS** |
| 知识库聚合三大业务且切换正常 | **PASS** |
| 详情/编辑路由未被破坏 | **PASS** |
| 测试通过、测试数不下降 | **PASS**（857 次执行 / 540 唯一用例 / 0 失败） |
| 真机截图 | **PASS**（见 MB-HOME-002 报告） |
| 不进入 Stage 6 开发 | **遵守** |

> MB-HOME-002 执行报告状态已由 `Review` 更新为 `CLOSED`：
> [MB-HOME-002-EXECUTION-REPORT.md](MB-HOME-002-EXECUTION-REPORT.md)。

---

## 2. 关闭前状态（执行记录）

执行本关闭任务前记录，未修改任何代码。

### 2.1 HEAD

```text
e90cb1fe7cc4318788ac5a221dd6db5e0b5a407d
```

### 2.2 `git status --porcelain`

```text
 M README.md
 M app/build.gradle.kts
 M app/src/main/java/com/culinary/navigation/CulinaryDestination.kt
 M app/src/main/java/com/culinary/navigation/CulinaryNavHost.kt
 M app/src/main/res/values/strings.xml
 M app/src/test/java/com/culinary/navigation/CulinaryDestinationTest.kt
 M app/src/testDebug/java/com/culinary/navigation/NavigationShellTest.kt
 M core/designsystem/src/main/java/com/culinary/core/designsystem/icon/CulinaryIcons.kt
 M core/designsystem/src/main/java/com/culinary/core/designsystem/theme/Color.kt
 M core/designsystem/src/main/java/com/culinary/core/designsystem/theme/Theme.kt
 M core/designsystem/src/test/java/com/culinary/core/designsystem/theme/DesignSystemTokensTest.kt
 M docs/00-governance/DOCUMENT-INDEX.md
 M docs/05-handover/MB-S0-07-DECISION-REGISTER.md
 M docs/design/MISEBASE-UI-DESIGN-SPEC.md
?? app/src/main/java/com/culinary/home/
?? app/src/main/java/com/culinary/library/
?? app/src/main/java/com/culinary/navigation/CulinaryBottomBar.kt
?? app/src/test/java/com/culinary/home/
?? app/src/test/java/com/culinary/library/
?? app/src/testDebug/java/com/culinary/home/
?? app/src/testDebug/java/com/culinary/library/
?? core/data/src/main/java/com/culinary/core/data/di/HomeModule.kt
?? core/data/src/main/java/com/culinary/core/data/home/
?? core/data/src/test/java/com/culinary/core/data/home/
?? docs/13-home/
```

说明：上述未提交内容包含 MB-HOME-001/002 的既有实现与文档。本关闭任务**未修改任何业务代码**；
关闭提交一次性纳入这些既有变更与本次关闭文档。

---

## 3. 关闭提交

| 项 | 内容 |
|---|---|
| 提交信息 | `docs(home): close home dashboard and navigation refactor` |
| 关闭前 HEAD | `e90cb1fe7cc4318788ac5a221dd6db5e0b5a407d` |
| 关闭提交 | 本记录所在提交；完整哈希见交付回执 |
| 变更性质 | 既有 MB-HOME-001/002 实现 + 关闭/索引/状态文档；无新业务代码 |
| 工作树 | 提交后 clean |

> 关闭提交的完整哈希、标签对象哈希与解析提交在关闭后由 git 回执提供，
> 避免“文档记录自身提交哈希”造成的循环依赖。

---

## 4. 关闭检查点

| 标签 | 类型 | 解析目标 | 说明 |
|---|---|---|---|
| `checkpoint/misebase-home-closed-20261105` | annotated tag（本次创建） | 关闭提交 | 首页与 N3 导航正式关闭 |
| `checkpoint/misebase-stage6-start-20261105` | annotated tag（已存在） | `9b773da142746c08348bfc22ed5fe6d6e31a6305` | 见 §5，未移动 |

> 标签对象哈希、解析提交与 `git show-ref` 证据见交付回执；标签创建后不再移动。

---

## 5. 标签冲突与处理（必须记录）

任务书要求创建：

1. `checkpoint/misebase-home-closed-20261105`
2. `checkpoint/misebase-stage6-start-20261105`

但执行前检查发现 **`checkpoint/misebase-stage6-start-20261105` 已存在**：

```text
refs/tags/checkpoint/misebase-stage6-start-20261105
  type: annotated tag
  tag object: 7646eca9ce465a6130c99d215266fa1e5395c6ea
  peeled commit: 9b773da142746c08348bfc22ed5fe6d6e31a6305
  commit subject: docs(stage5): close stage 5 after review
```

任务书同时规定：**不得移动 `framework-0.1.0` 及所有既有 checkpoint 标签**。

因此：

- **未创建同名新标签、未删除、未移动、未重打** `checkpoint/misebase-stage6-start-20261105`；
- 该标签仍解析到 Stage 5 关闭提交 `9b773da`，**不指向本次首页关闭提交**；
- `checkpoint/misebase-home-closed-20261105` 已创建并指向本次关闭提交；
- 任务验收条件“两个新标签都解析到关闭提交”因此**无法在不违反既有标签禁改约束的前提下成立**。

**需要负责人决定的事项**：

- 若希望 Stage 6 起点指向首页关闭提交，需授权移动/重建
  `checkpoint/misebase-stage6-start-20261105`；本任务未获此授权，故保持原样。
- 在此之前，Stage 6 的真正起点应以 `checkpoint/misebase-home-closed-20261105` 为准，
  或由负责人另立新的 Stage 6 起点标签。

---

## 6. 旧标签未移动（抽查）

| 标签 | 解析提交（关闭前已知） | 状态 |
|---|---|---|
| `framework-0.1.0` | `2b523de4b87e37a2eb08b4c7c851549350ad811a` | 未移动 |
| `checkpoint/misebase-stage5-closed-20261105` | tag object `12e82e87574c0c97cb532cb73035ba118f2bbf27` → `9b773da142746c08348bfc22ed5fe6d6e31a6305` | 未移动 |
| `checkpoint/misebase-stage6-start-20261105` | tag object `7646eca9ce465a6130c99d215266fa1e5395c6ea` → `9b773da142746c08348bfc22ed5fe6d6e31a6305` | 未移动 |

> 其他既有标签未做任何删除、重打或强制更新。

---

## 7. 未修改范围声明

本次关闭任务只改文档：

- 修改 `docs/13-home/MB-HOME-002-EXECUTION-REPORT.md`（状态 `CLOSED`、关闭说明）；
- 新增本关闭记录；
- 修改 `docs/00-governance/DOCUMENT-INDEX.md`（关闭记录索引）；
- 未修改业务代码、Schema/Entity/迁移、备份/恢复实现、模块集、Compose 页面；
- 未移动既有标签。

---

## 8. 停止条件

- MB-HOME-002 状态：`CLOSED`；
- 关闭记录完成；
- 关闭提交创建、`checkpoint/misebase-home-closed-20261105` 创建；
- Stage 6 起点标签冲突已记录，等待负责人决定；
- **不进入 Stage 6 开发，不开展其他业务。**

---

## 9. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-05 | 建立首页/N3 导航最终关闭记录；记录审查通过、关闭前 HEAD/status、关闭标签与 Stage 6 起点冲突 | 开发 AI |
