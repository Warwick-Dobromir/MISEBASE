# MB-S3-04 决策映射（Stage 3）

**文档编号**：MB-S3-04
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04

---

## 1. 本阶段直接落地的已签收决策

| 决策 | 状态 | 本阶段落地 | 证据 |
|---|---|---|---|
| MB-D-008 UI 方向 A「专业厨房档案」 | `ACCEPTED` | 暖中性 + Orange 强调 + 暖灰；现代清晰字阶；简洁克制组件；留白/字重/间距层级 | `core:designsystem` token、组件、`DesignSystemTokensTest`；[MB-S3-05](MB-S3-05-DESIGN-SYSTEM.md) |
| MB-D-009 导航 N1 | `ACCEPTED` | 五底部入口（酱汁/食材/菜谱/库存/扫码）+ 顶栏设置；三库独立入口；库存/扫码占位 | `app` 导航、`NavigationShellTest`、[MB-S3-06](MB-S3-06-NAVIGATION.md) |
| MB-D-013 子配方版本固定时点 | `ACCEPTED` | 草稿可指向当前版本；保存/发布解析为明确版本 ID；已发布不跟随 | `RecipeDraftResolver`、`RecipeVersionPinRule`、`RecipeUseCasesTest`、`RecipeRepositoryTest` |
| MB-D-015 可选配料缺料口径 | `ACCEPTED` | `is_optional` 在编辑/详情/展开中标记；展开显式包含；缺口计算不在本阶段 | 字段映射、UI 标记、`ExpandRecipeUseCase` |

## 2. 本阶段遵守的其它已签收决策

| 决策 | 遵守方式 |
|---|---|
| MB-D-001 接管基线与身份 | 基线 `b7f2938`；包名 `com.culinary` 未改 |
| MB-D-002 范围冻结 | 只做三大档案库 + 导航 + 设计系统；库存/扫码占位 |
| MB-D-003 13 模块 | 未新建/删除模块；未建 `feature:scan` |
| MB-D-005 休眠表 | 11 张表结构未动；新业务代码引用 0；旧脚本 3 处文本误报按关闭记录授权清理 |
| MB-D-014 库存扣减 | 未实现库存业务 |
| MB-D-016 采购清单形态 | 未实现采购清单 |
| MB-D-017 计算记录快照 | 表存在但未接线业务 UI/Use Case |
| MB-D-018 文档修正 | 只新增 Stage 3 文档与索引；未改历史报告正文 |

## 3. 仍为 PROPOSED 的边界

| 决策 | 状态 | 本阶段处置 |
|---|---|---|
| MB-D-004 扫码 UI 归属 | `PROPOSED` | 未新建 `feature:scan`；扫码只做底部占位 |
| MB-D-006 验收与测试口径 | `PROPOSED` | 沿用 274 基线口径并报告 344；未替负责人签收 |
| MB-D-007 备份一致性 | `PROPOSED` | 未改 `AutoBackupWorker` / `BackupArchiveWriter` / `RestoreExecutor` / 写屏障 / WAL；不声称一致备份 |
| MB-D-010 远端 CI | `PROPOSED` | 仍为本地门禁 |
| MB-D-011 真机设备 | `PROPOSED` | 未做真机验证 |
| MB-D-012 发布签名 | `PROPOSED` | 未创建/变更签名；Release 不在本阶段 |

## 4. 未把 PROPOSED 写成 ACCEPTED

本阶段新增/修改文档均保持原有状态；`MB-S0-07` 签收记录未改；
没有以任何形式把 MB-D-004/006/007/010/011/012 描述为已批准。

## 5. 与 Stage 4 的接口

| 能力 | Stage 3 提供 | Stage 4 预期 |
|---|---|---|
| 库存 | 无业务页面/流水/计算 | 库存总览、流水、扣减预览 |
| 计算记录 | 表结构就绪，未接 UI/Use Case | 计算确认、快照持久化接线 |
| 采购清单 | 无 | 按需求 - 可用生成 |
| 酱汁展开 | 显式 `CUL-REP-004` | 酱汁图展开与需求汇总 |
