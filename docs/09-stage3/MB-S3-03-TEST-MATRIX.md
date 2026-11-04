# MB-S3-03 测试矩阵

**文档编号**：MB-S3-03
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04

---

## 1. 汇总

| 项 | 值 |
|---|---|
| 基线唯一 JVM 测试 | 274 |
| Stage 3 后唯一 JVM 测试 | **344** |
| 新增 | **70** |
| 失败 | **0** |
| Debug APK | 构建成功 |

分布（唯一用例）：

| 模块 | 数量 |
|---|---:|
| app | 12 |
| core:common | 9 |
| core:domain | 118 |
| core:database | 81 |
| core:data | 65 |
| core:designsystem | 9 |
| core:platform | 19 |
| feature:ingredients | 11 |
| feature:recipes | 11 |
| feature:sauces | 9 |
| **合计** | **344** |

---

## 2. 新增测试套件

| 套件 | 模块 | 例数 | 覆盖 |
|---|---|---:|---|
| `DesignSystemTokensTest` | designsystem | 9 | 色值、亮/暗对比度、字阶、等宽数量、4dp 栅格、圆角 |
| `SauceUseCasesTest` | domain | 6 | 名称校验、草稿行号/可选、非法产量/数量、发布/重复发布、详情标签 |
| `SauceRepositoryTest` | data | 5 | 草稿往返、版本号递增、发布当前版本、已发布不可变、缺失实体 |
| `SaucesViewModelTest` | feature:sauces | 6 | 列表、详情、编辑器创建、保存并发布、空名校验、重复发布 |
| `SaucesUiTest` | feature:sauces | 3 | 列表、空状态、详情组成/可选/发布入口 |
| `SaveIngredientUseCaseTest`（扩展） | domain | +2 | 分类/描述/单位保存、超长拒绝 |
| `IngredientsViewModelTest` | feature:ingredients | 5 | 单位标签、详情换算、编辑分类/描述/单位、空名、归档 |
| `IngredientsUiTest` | feature:ingredients | 3 | 列表分类/单位、编辑器字段、详情换算/归档 |
| `RecipeUseCasesTest` | domain | 6 | 发布固定当前版本、草稿固定、循环拒绝、份数展开/可选、酱汁目标拒绝、非法输入 |
| `RecipeRepositoryTest` | data | 4 | 版本列表/servings、发布草稿/不可变、子菜谱固定、酱汁映射 |
| `RecipesViewModelTest` | feature:recipes | 8 | 列表、详情自动展开、改份数、创建草稿、子菜谱发布固定、酱汁固定、空名、发布草稿 |
| `RecipesUiTest` | feature:recipes | 3 | 列表分类、详情组成/可选/展开、编辑器份数与三类添加按钮 |
| `NavigationShellTest` | app | 4 | 五入口、设置、返回、库存占位 |
| `TransactionBoundaryTest` | domain | 4 | 成功经 Runner、失败不产生部分写入（食材/酱汁/菜谱） |

说明：Compose UI 测试放在 `src/testDebug`，仅 debug 变体执行；release 变体不运行 Robolectric Compose 测试。

---

## 3. 需求覆盖映射

| 要求 | 覆盖 |
|---|---|
| 酱汁列表/详情/创建/编辑 | ViewModel + Compose UI |
| 版本发布、已发布不可变 | 领域用例 + Room 触发器测试 |
| 酱汁组成行映射 | Repository 往返 + 详情标签 |
| 食材列表/详情/创建/编辑 | ViewModel + Compose UI |
| 单位、分类 | 领域校验 + ViewModel + UI |
| 菜谱列表/详情/创建/编辑 | ViewModel + Compose UI |
| 草稿、发布 | 领域 + ViewModel + Repository |
| 配方展开 | 领域按份数展开 + 详情 UI |
| 子配方版本固定 | 领域发布/草稿固定 + Repository + ViewModel |
| `servings` 使用 | 领域展开 + Repository 往返 + ViewModel |
| 可选配料标记 | 领域展开/用例 + UI 断言 |
| 导航五入口 + 设置 | Compose UI + 路由契约测试 |
| 库存/扫码占位 | Compose UI（库存）+ 路由契约 |
| 设计系统 A 方向 | token 测试（色值/对比/字阶/栅格） |
| Repository 事务边界 | 4 例边界测试 |
| 负库存拒绝 | 既有 `RepositoryContractTest` 保留（本阶段不涉及库存业务） |

---

## 4. 执行命令与结果

```powershell
.\gradlew.bat test assembleDebug ktlintCheck detekt --console=plain --no-daemon
```

- `test`：344 例 / 0 失败；
- `assembleDebug`：成功；
- `ktlintCheck`：通过；
- `detekt`：通过。

补充脚本：

| 脚本 | 结果 |
|---|---|
| `check-module-deps.ps1` | PASS（13 模块 / DAG） |
| `test-module-deps.ps1` | PASS（违规注入全部检出） |
| `check-doc-consistency.ps1` | PASS |
| `check-dormant-tables.ps1` | PASS（11 张表 0 处允许范围外引用） |

---

## 5. 未覆盖与后续

- 真机/模拟器 androidTest 未运行（无设备；MB-D-011 仍 PROPOSED）；
- 视觉验收（大字体、暗色、360dp 截图）留 Stage 6；
- 库存/采购/计算记录业务不在本阶段，不设测试；
- 备份/恢复一致性未变更，不设新测试。
