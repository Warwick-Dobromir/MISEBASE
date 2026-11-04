# MB-S5-07 Stage 5 检查点回执

**文档编号**：MB-S5-07
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-05
**标签**：`checkpoint/misebase-stage5-20261105`
**类型**：annotated tag
**对象哈希**：`42e417f2fe0e0521a1ea1409c6fe95d18b818b00`
**解析提交**：`0184a1496c17eb267ffcb6a0002707e9fd8368f2`
**Stage 5 起点**：`checkpoint/misebase-decisions-stage5-20261105` → `450dc9ec61b43c6c6ae202c511c0c07ac3ba02b2`

---

## 1. 回档方式

```powershell
git switch --detach checkpoint/misebase-stage5-20261105^{commit}
# 或只读查看
git show --stat checkpoint/misebase-stage5-20261105
```

回档前必须确认工作树干净；不得在 detached HEAD 上继续提交。返回主线：

```powershell
git switch misebase/main
```

---

## 2. 既有标签

`framework-0.1.0` 仍解析为 `2b523de4b87e37a2eb08b4c7c851549350ad811a`，**未移动**。
全部既有 `checkpoint/*`、`baseline/*` 未移动；Stage 5 起点标签仍解析为 `450dc9e`。

---

## 3. 标签内容

标签指向 Stage 5 文档提交（本报告、页面说明、接口说明、测试矩阵、决策映射、真机记录、README 与索引），
该提交之前是 6 个功能/测试提交：

| # | 提交 | 内容 |
|---|---|---|
| 1 | `f0afc6b` | 新建 `feature:scan`，模块集 13→14，依赖配置与脚本同步 |
| 2 | `ac83f19` | 识别核心（`ScanFrameGate` / `ScanCapture` / ML Kit 解码 / CameraX 会话） |
| 3 | `07757e5` | 扫码页面（权限、扫描框、手电筒、手工输入） |
| 4 | `6fdadcd` | 识别结果接口与导入确认（products/inventory 端口 + 幂等 + 补偿） |
| 5 | `98b8a22` | 扫码入口切换为真实页面（N1 第五入口） |
| 6 | `f0df352` | 真机验证记录与解码用例；真机问题修复（对比度/返回按钮、默认单位） |
| 7 | `0184a14` | Stage 5 文档（本回执为其后补记） |

---

## 4. 变更文件摘要

`git diff --name-status 450dc9e..0184a14`：

| 项 | 值 |
|---|---|
| 变更文件 | **77** |
| 新增（A） | 64 |
| 修改（M） | 13 |
| 删除（D） | 0 |
| 行数 | +5736 / −44 |

按区域：

| 区域 | 文件数 |
|---|---:|
| feature/scan（含 androidTest 资源与 4 张测试图） | 32 |
| docs/12-stage5（7 份文档 + 10 张真机截图 + 授权记录） | 16 |
| core/domain（契约、闸门、用例、测试） | 9 |
| feature/products | 4 |
| app（导航、字符串、占位页、构建、导航测试） | 4 |
| feature/inventory | 3 |
| settings / config / scripts / README / 索引 / 依赖报告 / .gitignore | 9 |

### 4.1 边界核对

| 边界 | 命令 | 结果 |
|---|---|---|
| Schema / Entity / 表结构 / 迁移 / 数据库文件 | `git diff --name-only 450dc9e..HEAD -- core/database` | **空** |
| 备份/恢复实现 | `git diff --name-only 450dc9e..HEAD -- core/data/src/main/java/com/culinary/core/data/backup core/platform/src/main/java/com/culinary/core/platform/work` | **空** |
| 休眠表 | `check-dormant-tables.ps1` | PASS（0 处新引用） |
| `purchase_order*` | 全仓检索 | 无业务引用 |
| 技术栈 | `git diff --name-only 450dc9e..HEAD -- gradle/libs.versions.toml build-logic` | **空** |
| 模块集 | `check-module-deps.ps1` | 14 / 39 条声明边 / DAG 无环 |
| CI / 签名 | 仓库无 CI 配置变更；无 keystore/签名配置 | 未启用 / 未创建 |
| G3-1 | 未恢复、未纳入 | — |

---

## 5. 验证

| 项 | 结果 |
|---|---|
| `test assembleDebug ktlintCheck detekt --console=plain --no-daemon` | PASS |
| 唯一 JVM 用例 / 执行次数 / 失败 | **503 / 801 / 0** |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk`，39,854,267 字节，SHA-256 `3A162036214F1A315E5201550AD8EC80C4E1ABAEDB8AAB4ED2E1C6B639E406F1` |
| 真机 instrumentation | `feature:scan` 4/4（vivo V2429A / API 36） |
| 依赖检查 / 违规注入 / 休眠表 / 文档一致性 | PASS / PASS / PASS / PASS |
| `git status --porcelain` | 空（本回执提交后） |

---

## 6. 状态声明

- Stage 5 状态：**Review**（未关闭）；
- 未进入 Stage 6；
- 未替项目负责人签收任何决策；MB-D-006 / MB-D-010 / MB-D-012 仍为 `PROPOSED`。

---

## 7. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-05 | 补记 Stage 5 标签对象哈希、解析提交、变更范围与验证 | MISEBASE AI 开发工作区 |
