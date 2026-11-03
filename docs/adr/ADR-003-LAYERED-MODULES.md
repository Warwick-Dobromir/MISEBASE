# ADR-003：分层 + 按业务能力分模块

**状态**：Proposed
**版本**：`0.2.0`
**日期**：2026-11-04
**决策者**：首席架构师 / 项目负责人
**关联**：D-004、A02、A03

## 背景

需求明确要求 UI 与业务分离、业务与数据库分离、模块边界清晰、避免循环依赖和大型单体文件。

## 决策

采用分层 + 按业务能力分模块，但**模块数量暂不锁定**。

推荐 **13 个 Gradle 模块**：

- `:app` 组合根
- `:core:common`
- `:core:domain`（含领域模型、端口、Use Case、规则）
- `:core:database`
- `:core:data`
- `:core:designsystem`
- `:core:platform`
- `:feature:ingredients`
- `:feature:formulas`（酱汁 + 菜谱）
- `:feature:inventory`（商品 + 库存）
- `:feature:planning`
- `:feature:procurement`
- `:feature:settings`

16 模块作为细粒度参考（拆出 `core:model`、`feature:sauces`/`feature:recipes`、`feature:products`），不是默认方案。详见 [A03A](../02-architecture/A03A-MODULE-DECOMPOSITION-REVIEW.md)。

依赖规则：

```text
app → feature → domain → common
app → data → database
app → platform
feature 之间禁止依赖
domain 不依赖 Android/Room/Compose
database 不依赖 domain
```

## 理由

1. 用 Gradle 边界强制约束，避免“文档分层、代码混写”。
2. 13 个模块对个人项目可管理；不按页面爆炸拆分。
3. domain 纯 Kotlin，可用 JVM 测试核心算法。
4. database/data 隔离，UI 无法绕过事务直接写表。
5. 合并模型到 domain、酱汁/菜谱 UI、商品/库存 UI，减少 3 个模块的构建与导航成本；数据表与 Use Case 仍分开。

## 后果

- 初始构建配置成本高于单模块。
- 需要约定插件与依赖检查，防止边界腐化。
- 跨 feature 协作必须经 domain 或 app 导航，代码略多。

## 替代方案

- 单模块按包分层：边界弱，易被绕过。
- 每 feature 独立 data/domain：跨 feature 事务与依赖复杂。
- 微服务/多进程：个人软件不需要。

## 复审条件

- 模块数量导致构建/维护成本明显超过收益；
- 单 feature 需要独立发布/进程；
- 依赖规则无法自动检查。
