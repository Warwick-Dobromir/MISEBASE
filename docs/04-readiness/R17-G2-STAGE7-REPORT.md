# G2 阶段 7（纵向切片：食材）完成报告

**文档编号**：R17
**版本**：`1.1.0`
**状态**：`Approved`（项目负责人 2026-11-04 验收通过）
**日期**：2026-11-04
**实施依据**：[R01 0.2.2](R01-FRAMEWORK-SEQUENCE.md) §10、A03A §4.9、A06 §3.2、E02、E03
**回档点**：`baseline/g2-stage6-20261104`（`f1dc6ab`）

---

## 1. 任务目标

食材最小可用 UI，打通 UI → ViewModel → Use Case → Repository → Room 全链路。

## 2. 交付内容

| 层 | 内容 |
|---|---|
| 领域 | `IngredientNames`（NFKC/trim/空白折叠/小写）、`SaveIngredientUseCase`（校验 + 事务）、`ArchiveIngredientUseCase` |
| 表现 | `IngredientsViewModel`（列表 Flow + 保存/归档 + 错误）、`IngredientsScreen`（列表/空/加载/错误、新建/编辑对话框、归档） |
| 导航 | `CulinaryNavHost` 用真实 `IngredientsRoute` 替换占位页 |
| 装配 | Hilt 提供食材 Use Case 与 `IngredientDao`/`UnitDao`（DAO 之前缺失，本轮补上） |
| 校验 | 名称非空、≤120 字符；规范化用于查重；重复名称由数据库部分唯一索引拒绝并映射 `CUL-CON-001` |
| 图片 | 阶段 7 不做图片区；无图时**没有空占位** |

## 3. 设备实测（模拟器）

| 场景 | 结果 |
|---|---|
| 新建 “Soy Sauce” | 列表出现；数据库 `name=Soy Sauce`、`normalized_name=soy sauce`、`status=ACTIVE` |
| 重启 | 列表仍显示该食材（持久化） |
| 归档 | 行消失、恢复空状态；数据库 `status=ARCHIVED` |
| 新建 “Butter” 后编辑为 “Butter Unsalted” | 编辑对话框预填、保存后列表更新；数据库 `normalized_name=butter unsalted`、`revision=2` |
| 重复名称 | 显示“名称已存在，请换一个名称。（CUL-CON-001）”，原行保留 |

## 4. 测试

| 测试类 | 数量 | 覆盖 |
|---|---:|---|
| `IngredientNamesTest` | 4 | NFKC、空白、大小写、中文 |
| `SaveIngredientUseCaseTest` | 5 | 空名/超长拒绝、规范化、编辑保留 ID、归档委托 |
| `IngredientErrorMessagesTest` | 3 | 空名/超长/未知错误码 |

工程总测试 **167 例**（domain 24 + database 77 + data 38 + platform 19 + app 6 + feature 3）；
`ktlintCheck`、`detekt`、`assembleDebug` 全绿。

## 5. 退出条件核对（R01 §10.2）

| # | 条件 | 状态 |
|---|---|---|
| 1 | 新建食材 → 列表可见 → 重启仍在 | **PASS**（设备） |
| 2 | 名称重复有冲突提示 | **PASS**（设备，含错误码） |
| 3 | 无图时无空占位 | **PASS**（未实现图片区） |
| 4 | 保存失败显示错误码与恢复动作 | **PASS**（错误码 + 关闭后重试） |
| 5 | UI 不 import `core:database`/`core:data` 实现 | **PASS**（grep 与依赖检查） |
| 6 | 端到端测试通过 | **部分**：手工设备 E2E 完成；**自动化 UI/E2E 测试尚未实现** |

## 6. 偏差与遗留

- “详情页”未单独实现：列表 + 新建/编辑对话框覆盖最小 CRUD；详情属后续增强；
- 图片未实现（阶段 7 允许可选），因此无空占位；
- 无自动化 Compose UI 测试与仪器化 E2E：已在 §5 标注为“部分”，未声称通过；
- 归档后不提供“取消归档”入口。

## 7. Git

| 项 | 值 |
|---|---|
| 阶段 6 基线 | `baseline/g2-stage6-20261104`（`f1dc6ab`） |
| 阶段 7 提交 | `343fee8`（用例）、`011b58e`（UI/导航）、`09fea58`（错误码） |
| 阶段 7 基线标签 | 验收通过后打 `baseline/g2-stage7-20261104` |

## 8. 结论

食材纵向切片端到端可用，五条退出条件有设备证据；第 6 条自动化 E2E 尚未实现，已在报告中如实标注。

**请项目负责人验收阶段 7。** 验收通过后进入阶段 8（算法验证：配方图/单位换算/汇总）。

## 9. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录食材切片、设备证据、167 例测试与遗留项 | 工程组 |
| 1.1.0 | 2026-11-04 | 项目负责人验收通过阶段 7；状态改为 Approved | 工程组 |
