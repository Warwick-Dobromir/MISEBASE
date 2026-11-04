# S0-03 决策状态审查报告

**任务编号**：MB-S0-001
**文档编号**：S0-03
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**审查对象**：MB-DECISIONS 0.1.0（18 项建议决策）+ G2 DECISION-REGISTER 0.20.0（D-001–D-025）

---

## 1. 审查原则

本报告严格区分：

| 状态 | 含义 |
|---|---|
| `INHERITED` | G2 已接受/确认，MISEBASE 直接继承 |
| `REVISED` | 继承原则，但范围/模块/实现被新决策修订 |
| `PROPOSED` | 接管方建议；**未签字不生效** |
| `PENDING` | 已提交，等待负责人决定 |
| `ACCEPTED` | 项目负责人已签字接受 |
| `DEFERRED` | 明确不在当前阶段处理 |
| `SUPERSEDED` | 被新决策取代，保留原文 |
| `N/A` | 新范围不适用 |

**关键结论：MB-D-001 至 MB-D-018 目前全部是 `PROPOSED`，没有一项 `ACCEPTED`。** 本报告不替负责人签字，也不把建议写成已确认需求。

---

## 2. 继承的 G2 决策（D-001–D-025）

| ID | 主题 | G2 状态 | MISEBASE 处置 | 说明 |
|---|---|---|---|---|
| D-001 | 原生 Android | ACCEPTED | **INHERITED** | Kotlin + Compose |
| D-002 | SDK 基线 | ACCEPTED | **INHERITED** | min 26 / compile & target 36 |
| D-003 | Room + 平台 SQLite | ACCEPTED | **INHERITED** | WAL、单进程、统一访问 |
| D-004 | 15 模块 DAG | ACCEPTED | **REVISED** | 原则继承；模块集由 MB-D-003 修订 |
| D-005 | TEXT 小写 UUID v4 主键 | ACCEPTED | **INHERITED** | 31 表统一 |
| D-006 | 不可变已发布配方版本 | ACCEPTED | **INHERITED** | 酱汁/菜谱；子配方固定版本 |
| D-007 | 计划项固定 recipe_version_id + 快照 | ACCEPTED | **REVISED** | 语义继承；不建独立计划模块，落地见 MB-D-017 |
| D-008 | 单位换算 | ACCEPTED | **INHERITED** | 同维度基准因子；跨维度食材级显式 |
| D-009 | 库存批次 + 位置 + 追加流水 | ACCEPTED | **INHERITED** | 禁止直接改库存数字 |
| D-010 | 预留时机 | OPEN | **PENDING** | 由 MB-D-014 决定 |
| D-011 | 备份格式 E05 0.4.1 | ACCEPTED | **INHERITED** | 需补 checkpoint（MB-D-007） |
| D-012 | Hilt 2.57.2 | ACCEPTED | **INHERITED** | 禁止 2.59+ |
| D-013 | CameraX + ML Kit Bundled | ACCEPTED | **INHERITED** | 本地识别；无 INTERNET |
| D-014 | 不引入云/AI/协作 | CONFIRMED | **INHERITED** | 继续排除 |
| D-015 | 图片私有目录 + 元数据 | ACCEPTED | **INHERITED** | 不存 BLOB |
| D-016 | 数据库加密 | OPEN | **DEFERRED** | 发布前决定；当前不引入 SQLCipher |
| D-017 | 包名 `com.culinary` | OPEN | **PENDING** | 由 MB-D-001 决定品牌/包名 |
| D-018 | 可选配料 | OPEN | **PENDING** | 由 MB-D-015 决定缺料口径 |
| D-019 | 超量收货 | OPEN | **N/A** | 新范围不做采购收货 |
| D-020 | 含库存商品删除 | PROPOSED | **DEFERRED** | 商品删除 UI 前确认；默认只能归档 |
| D-021 | 计划改版 | PROPOSED | **N/A** | 不做独立计划 |
| D-022 | 搜索（规范化 + LIKE） | PROPOSED | **PROPOSED** | 建议接受；不引入 FTS |
| D-023 | 真机验收 | OPEN | **PENDING** | 由 MB-D-011 确认 |
| D-024 | 界面语言简体中文 | OPEN | **PROPOSED** | 建议接受 |
| D-025 | 配方图深度 20 / 节点 500 | PROPOSED | **PROPOSED** | 建议接受 |

### 2.1 常见误读纠正

- D-005、D-007、D-013、D-015 **已经是 ACCEPTED**，不是 PROPOSED；README 仍写 PROPOSED，属于文档失真。
- D-004 的“15 模块不得增删”只对 G2 历史有效；MISEBASE 要改，必须由 MB-D-003 明确修订，不能“默认沿用”也不能“默认删除”。
- D-007 的“计划快照”语义不能因为删除 planning 模块而消失；必须用其他载体保证旧计算结果稳定（MB-D-017）。
- D-011 继承的是**备份格式**，不是“备份一致性已实现”；E02 要求的 checkpoint 仍缺失。

---

## 3. MB-DECISIONS 0.1.0 逐项审查

### 3.1 全部 18 项

| ID | 主题 | 当前状态 | 是否阻塞 | 建议默认值 | 备注 |
|---|---|---|---|---|---|
| MB-D-001 | 接管基线与项目身份 | **PROPOSED** | Stage 0 签署 | `705e0ca`；MISEBASE；包名暂留 `com.culinary` | 事实工作基线已是此提交 |
| MB-D-002 | 范围冻结 | **PROPOSED** | **Stage 1** | 五项功能；P01 执行模块部分 Superseded | 最高优先级 |
| MB-D-003 | 目标模块集 13 | **PROPOSED** | **Stage 1** | 删 planning/procurement；products 缩减 | 需同步改导航/依赖检查 |
| MB-D-004 | 扫码 UI 归属 | **PROPOSED** | Stage 1 决定 / Stage 5 实现 | 暂放 `feature:products` | 可延后到 Stage 5 前 |
| MB-D-005 | 数据库残留处置 | **PROPOSED** | **Stage 1** | 冻结 v1；11 表 DORMANT；不删表 | 需逐表定性 |
| MB-D-006 | 验收与测试口径 | **PROPOSED** | Stage 0 确认 | 197；188 为历史漏计；R11/R12 补签或说明 | 不阻塞代码 |
| MB-D-007 | 备份一致性优先 | **PROPOSED** | **Stage 1** | 先补 checkpoint + 写屏障 + WAL 测试 | 数据安全最高风险 |
| MB-D-008 | UI 视觉方向 A/B/C | **PROPOSED** | Stage 3 | 方向 A（暖中性 + 橙） | 需参考图/品牌 |
| MB-D-009 | 导航 N1/N2 | **PROPOSED** | Stage 3 | N1（5 底部入口 + 设置） | 需交互确认 |
| MB-D-010 | 远端 CI | **PROPOSED** | Stage 1 可选 | 有仓库/凭据则启用 | 否则本地门禁 + BLOCKED |
| MB-D-011 | 真机验证设备 | **PROPOSED** | Stage 5 | 至少一台 API 26+ 真机 | 扫码/SAF 必须 |
| MB-D-012 | 发布签名 | **PROPOSED** | Stage 6 | 独立 release keystore，仓库外双备份 | 当前 unsigned |
| MB-D-013 | 子配方版本固定时点 | **PROPOSED** | Stage 2 | 发布时解析并写入明确版本 | 旧结果稳定 |
| MB-D-014 | 库存扣减模式 | **PROPOSED** | Stage 4 | 先预览，确认后写流水 | 默认不预留 |
| MB-D-015 | 可选配料缺料口径 | **PROPOSED** | Stage 2 | 默认不计入缺口，详情可见 | D-018 OPEN |
| MB-D-016 | 采购清单形态 | **PROPOSED** | Stage 4 | 不落采购单表；计算/导出 | 采购模块不回流 |
| MB-D-017 | 计划快照替代 | **PROPOSED** | Stage 2 | 计算记录 + 菜谱版本 + 引擎版本 | 保留 D-007 语义 |
| MB-D-018 | 文档修正授权 | **PROPOSED** | Stage 0 | 授权纯文档修正 README/P01/A03A | 不改历史报告正文 |

### 3.2 可以绕开的决策

即使未签字，以下**不带产品行为变化**的工作可以准备（但仍应在 Stage 1 授权范围内）：

- 补充现有行为的单元测试（不改 API/DB/UI）；
- 重写依赖检查脚本为配置化（不改模块集）；
- 增加构建/测试证据采集；
- 整理文档修订草案（不提交生效）。

### 3.3 不能绕开的决策

以下必须签字后才能动代码：

| 决策 | 原因 |
|---|---|
| MB-D-002 | 决定做什么/不做什么 |
| MB-D-003 | 删模块、改导航、改依赖检查白名单 |
| MB-D-005 | 数据库休眠表/迁移 |
| MB-D-007 | 备份实现方式 |
| MB-D-013/014/015/016/017 | 业务规则与数据语义 |
| MB-D-008/009 | UI 页面与导航 |
| MB-D-018 | 修改历史文档 |

---

## 4. 决策依赖

```text
MB-D-001 基线
  └─ MB-D-002 范围
       ├─ MB-D-003 模块集
       │    └─ MB-D-004 扫码归属
       ├─ MB-D-005 数据库残留
       ├─ MB-D-013 子配方版本
       ├─ MB-D-015 可选配料
       ├─ MB-D-016 采购清单
       └─ MB-D-017 快照替代

MB-D-006 验收口径（独立）
MB-D-007 备份一致性（Stage 1 前置）
MB-D-008/009 UI（Stage 3 前置）
MB-D-010 CI（可选）
MB-D-011 真机（Stage 5 前置）
MB-D-012 签名（Stage 6 前置）
MB-D-014 库存扣减（Stage 4 前置）
MB-D-018 文档修正（Stage 0 可选）
```

---

## 5. 需要负责人签收的最小集合

**要进入 Stage 1（范围清理），至少需要：**

1. MB-D-001 接管基线；
2. MB-D-002 范围冻结；
3. MB-D-003 目标模块集；
4. MB-D-005 数据库残留处置；
5. MB-D-018 文档修正授权。

**要进入 Stage 2（核心规则），还需要：**

6. MB-D-013 子配方版本固定；
7. MB-D-015 可选配料缺料；
8. MB-D-017 计划快照替代；
9. MB-D-014 库存扣减模式（与 Stage 4 相关，可稍后）。

**要进入 Stage 3（页面），还需要：**

10. MB-D-008 视觉方向；
11. MB-D-009 导航方案。

**要进入 Stage 5/6，还需要：**

12. MB-D-004 扫码归属（Stage 5 前）；
13. MB-D-011 真机；
14. MB-D-012 签名。

---

## 6. 审查结论

| 项 | 结论 |
|---|---|
| 新决策总数 | 18 |
| 已接受 | **0** |
| 阻塞 Stage 1 | 5 项（001/002/003/005/018） |
| 高风险未决 | MB-D-007（备份一致性）、MB-D-005（残留表） |
| 可延后 | UI/真机/签名/CI |
| 需要负责人明确“拒绝/修改”的 | UI 方向、导航、扣减模式、可选配料、子配方固定、备份方案 |

**下一步**：使用 `S0-05-OWNER-SIGNOFF.md` 逐项签收。未签收前，Stage 1 不得开始代码清理。

---

## 7. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 完成 D-001–D-025 继承审查与 MB-D-001–018 状态审查 | 接管工程组 |
