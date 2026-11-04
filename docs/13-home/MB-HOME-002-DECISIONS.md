# MB-HOME-002 决策记录：香槟金批准与 N3 导航重构

**文档编号**：MB-HOME-002-ADR  
**版本**：`1.0.0`  
**状态**：`ACCEPTED`（2026-11-05，项目负责人在 MB-HOME-002 任务书中明确决定）  
**日期**：2026-11-05  
**适用范围**：MISEBASE 首页与全局底部导航  
**关联**：MB-HOME-001；MB-D-008；MB-D-009；UI-07；[MB-HOME-002 执行报告](MB-HOME-002-EXECUTION-REPORT.md)

---

## 1. 背景

MB-HOME-001 已实现独立首页、Champagne Gold 视觉和真实数据展示，并提交验收。  
项目负责人在 MB-HOME-002 中作出以下最终决定：

1. 正式批准 Champagne Gold 替换 Orange。
2. “最近工作”“低库存预警”“计算记录”无真实数据源，保持隐藏。
3. “需关注”继续采用“可用量 ≤ 0”的现有领域规则。
4. **推翻 N1 五入口平铺结构，改为 N3：首页 / 知识库 / 扫码（中央凸起）/ 库存 / 设置。**
5. 三个档案库合并为知识库聚合页的 Tab，但底层模块与内部路由保持独立。

---

## 2. MB-D-008 Rev.1：Champagne Gold 正式批准

| 项 | 内容 |
|---|---|
| 决策 ID | **MB-D-008（Rev.1）** |
| 状态 | `ACCEPTED`（2026-11-05 修订） |
| 原决策 | 方向 A「专业厨房档案」，暖中性 + Orange `#B4551F` 强调（2026-11-04） |
| 修订决定 | 以 **Champagne Gold `#8A6A2B`** 替换 Orange，作为全局主强调色 |
| 保留 | 暖中性底色、暖灰辅助、清晰字阶、克制组件与留白 |
| 约束 | 浅香槟金 `#F5E7CC` / `#E8CD97` 只做容器/反相强调，不作为亮色正文；正文使用 `#241F19` 或深香槟金 |
| 证据 | `DesignSystemTokensTest` 对比度断言全部 ≥4.5:1；真机截图见 MB-HOME-002 报告 |
| 影响 | `core:designsystem` 的 Color/Theme 与全部页面；既有 Orange token 由 Champagne token 取代 |
| 回滚 | 恢复 `Color.kt`/`Theme.kt` 与测试；无数据库影响 |

> 本修订不新增决策 ID，MB-D-008 的接受计数不变；旧 Orange 值作为历史记录保留在 MB-HOME-001 与设计书变更记录中。

---

## 3. MB-D-009 Rev.2：N3 导航结构

| 项 | 内容 |
|---|---|
| 决策 ID | **MB-D-009（Rev.2）** |
| 状态 | `ACCEPTED`（2026-11-05 修订；N1 被 N3 取代） |
| 原决策 | N1：酱汁 / 食材 / 菜谱 / 库存 / 扫码 + 顶栏设置（2026-11-04） |
| 新结构 | **N3：首页 / 知识库 / 扫码（中央凸起）/ 库存 / 设置** |
| 设置 | 从顶栏移到底部第 5 项；首页 Hero 与业务页顶栏都不再显示设置图标 |
| 扫码 | 底部中央凸起圆形主按钮（FAB 样式），金色主色、阴影 + 表面色描边，点击进入原 `feature:scan` |
| 知识库 | 一个底部入口；聚合页内用 Segment/Tab 切换酱汁 / 食材 / 菜谱 |
| 模块边界 | **不合并 `feature:sauces`、`feature:ingredients`、`feature:recipes`**；不修改其内部路由、ViewModel、Repository |
| 知识库路由 | `library`（默认酱汁）+ `library/{tab}`（首页数字直达）；详情/编辑仍走既有独立路由 |
| 首页 | 仍是 Kitchen Dashboard，作为 N3 第 1 项和冷启动默认页 |
| 允许调整 | 图标、文案、视觉表现、底栏布局 |
| 不允许 | 合并 feature 代码、改 Schema、增加业务功能、改变详情/编辑路由 |

### 3.1 底栏顺序与交互

```text
[首页]   [知识库]   ( 扫码 )   [库存]   [设置]
                     凸起圆形
```

- 五个入口均可选中；中央扫码始终是最高视觉权重。
- 扫码按钮触控区域 ≥48dp；视觉圆形 56dp。
- 底栏高度 96dp（含 16dp 凸起空间），并处理系统导航栏 inset。
- 从任一入口进入详情/编辑页时沿用既有返回栈规则；知识库 Tab 在返回时保留。

### 3.2 知识库聚合边界

```text
app 路由: library / library/{tab}
  └─ LibraryScreen（仅 Tab 容器）
       ├─ feature:sauces     SaucesRoute     → sauces/{id} / sauces/{id}/edit
       ├─ feature:ingredients IngredientsRoute → ingredients/{id} / ...
       └─ feature:recipes    RecipesRoute    → recipes/{id} / ...
```

- 聚合只发生在外层 `app`；
- 三个 feature 的列表、详情、编辑路由与数据逻辑保持不变；
- 不复制列表 UI，不建立第二套 ViewModel/Repository。

---

## 4. 验收决定

| 项 | 决定 | 处理 |
|---|---|---|
| 最近工作 | 保持隐藏 | 无生产 `change_log` 写入，无可信操作记录 |
| 低库存预警 | 保持隐藏 | 无预警字段/阈值；不得伪造 |
| 计算记录 | 保持隐藏 | 无首页全量读取端口；本次不扩业务 |
| 需关注 | 可用量 ≤ 0 | 复用 `InventoryOverviewRow.isLow`，不是新阈值 |
| 视觉 | Champagne Gold 批准 | 见 §2 |
| 导航 | N3 批准 | 见 §3 |

---

## 5. 影响与回滚

### 影响

- `app` 导航宿主、底栏、知识库容器、Home Hero、相关测试与文档；
- `core:designsystem` 的图标集新增 `Home`、`Library`；
- 不修改 Schema、Entity、DAO、迁移、备份；
- 不修改五个 feature 的业务逻辑与内部路由。

### 回滚

1. 恢复 `CulinaryDestination.kt`、`CulinaryNavHost.kt`、`CulinaryBottomBar.kt`、`LibraryScreen.kt`、`LibraryRoute.kt`、`HomeScreen.kt`、字符串与测试；
2. 删除 `app/src/main/java/com/culinary/library`；
3. 恢复设计书与决策登记册；
4. 无数据库回滚；重新安装旧 APK 即可，数据不变。

---

## 6. 签收

| 日期 | 决策 | 结论 | 说明 | 来源 |
|---|---|---|---|---|
| 2026-11-05 | MB-D-008 Rev.1 | **ACCEPTED** | Champagne Gold 替换 Orange；浅金禁作正文 | MB-HOME-002 任务书 §1.1 |
| 2026-11-05 | MB-D-009 Rev.2 | **ACCEPTED** | N3：首页/知识库/扫码凸起/库存/设置；设置入底栏；知识库聚合 | MB-HOME-002 任务书 §2 |
| 2026-11-05 | 隐藏模块 | **维持** | 最近工作/低库存预警/计算记录隐藏 | MB-HOME-002 任务书 §1.2 |
| 2026-11-05 | 需关注口径 | **维持** | 可用量 ≤ 0 | MB-HOME-002 任务书 §1.3 |

---

## 7. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-05 | 建立香槟金批准与 N3 导航决策记录；更新 MB-D-008/009 修订状态 | 开发 AI |
