# G2 阶段 1 开工授权记录

**文档编号**：R10
**版本**：`1.0.0`
**状态**：`Effective`（负责人已明确授权）
**日期**：2026-11-04
**记录对象**：D-012 接受、G2 阶段 1 授权
**关联**：[DECISION-REGISTER](../00-governance/DECISION-REGISTER.md) 0.8.0、[D012-SUP-01](D012-HILT-SUPPLEMENT.md)、[R01](R01-FRAMEWORK-SEQUENCE.md) 0.2.2

---

## 1. 授权来源

项目负责人在复核 [D012-SUP-01](D012-HILT-SUPPLEMENT.md) 后，于 2026-11-04 给出以下明确确认（原文）：

> 项目负责人正式确认：
>
> 1. D-012 状态由 PROPOSED 改为 ACCEPTED。
> 2. 接受 Hilt 2.57.2 + KSP 2.2.20-2.0.4 作为正式 DI 方案。
> 3. 锁定 Hilt 2.57.2，禁止升级至 2.59 或更高版本。未来如需升级 AGP 9，必须作为独立任务进行依赖升级与完整回归。
> 4. 接受 §11 的 Hilt 插件落点。
> 5. 接受 androidx.hilt:hilt-lifecycle-viewmodel-compose:1.3.0 新坐标及对应新包路径。
> 6. hilt-navigation-compose 仅在确有 Navigation 作用域 ViewModel 需求时引入。
>
> 现正式授权进入 G2 阶段 1：创建已批准的 15 模块工程骨架。
>
> 执行要求：
> - 严格遵循已签收的 D-004、R01 0.2.2 及其他已接受决策。
> - 不得擅自改变模块数量、模块边界和依赖方向。
> - 开始修改前建立 Git 基线和可回档点。
> - 阶段完成后提供实际构建结果、模块结构、版本锁定情况和验证记录。
> - 不得借阶段 1 授权提前进入后续阶段。
> - D-005 等仍为 PROPOSED 的决策继续保持原状态，在对应阶段实施前再提交确认。
>
> 负责人确认完毕，可以开始实施。

## 2. 授权范围

| 项 | 内容 |
|---|---|
| 允许实施 | R01 0.2.2 阶段 1：15 模块骨架、`build-logic` 约定插件、版本目录、最小 Application/Activity |
| 允许的构建验证 | `projects`、`assembleDebug`、`ktlintCheck`、`detekt`、安装与启动 |
| 允许引入的插件/依赖 | 仅 A01/R02 已锁定版本 + 本次确认的 Hilt 2.57.2/KSP；质量工具 ktlint-gradle 与 detekt 1.23.8 按 R02 候选落实 |
| 禁止实施 | 阶段 2 及以后：领域模型、Room 数据库、Repository、平台能力、导航、业务 UI |
| 禁止改动 | D-004 的 15 模块数量/边界/依赖方向；E05；已接受的决策 |
| 仍为 PROPOSED | D-005、D-007、D-013、D-015、D-020、D-021、D-025（保持原状态，不得在阶段 1 实施其方案） |

## 3. Git 基线

- 开始阶段 1 修改前的基线提交：`c6ab758 docs(readiness): add D-012 Hilt compatibility evidence`
- 回档点标签：`checkpoint/g2-d012-accepted-20261104`（指向该提交）
- 阶段 1 完成后另打 `baseline/g2-stage1-20261104`，不覆盖开工标签。

## 4. 阶段 1 实施前的已知环境事实（本次实测）

| 项 | 值 |
|---|---|
| Gradle | 8.14.3（官方分发，SHA-256 `ed1a8d686605fd7c23bdf62c7fc7add1c5b23b2bbc3721e661934ef4a4911d7c`） |
| JDK | JBR 21.0.8；Kotlin/JVM target 17 |
| AGP / Kotlin / KSP | 8.13.0 / 2.2.20 / 2.2.20-2.0.4 |
| 仓库 | 阿里云 google/public/gradle-plugin 优先，官方源回退；JVM 可直连官方端点 |
| 质量工具 | ktlint-gradle `14.2.0`（插件门户）、detekt `1.23.8`（Maven Central 镜像） |
| 模拟器 | `Pixel_5`（`-feature -Vulkan`，headless）已可启动 |

## 5. 阶段 1 退出条件（R01 §4.2）

- [ ] `./gradlew projects` 显示 15 模块
- [ ] `./gradlew assembleDebug` 成功
- [ ] `./gradlew ktlintCheck detekt` 成功
- [ ] 版本全部来自 version catalog，无硬编码
- [ ] 模块依赖符合 A03/A03A（feature 间零依赖）
- [ ] 安装后能打开页面

## 6. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录 D-012 接受与 G2 阶段 1 授权原文、范围、基线与退出条件 | 工程组 |
