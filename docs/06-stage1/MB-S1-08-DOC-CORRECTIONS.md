# MB-S1-08 文档修正记录（MB-D-018）

**文档编号**：MB-S1-08
**版本**：`0.1.0`
**状态**：`Review`（已执行）
**日期**：2026-11-04
**依据**：MB-D-018（ACCEPTED）、MB-D-006、MB-S1-001 任务书 §4.9
**原则**：只改事实与状态；不修改 G2 历史报告正文；A-G2-001 用 Errata；P01/A03A 用修订说明；
README 更新事实；PROPOSED 不得写成 ACCEPTED。

---

## 1. 修正清单

| # | 文档 | 修正前 | 修正后 | 依据 |
|---:|---|---|---|---|
| 1 | `README.md` | 标题 CULINARY；15 模块；188 例；写“数据库尚未创建”；D-004 不得增删 | 标题 MISEBASE；13 模块；197 例；schema v1/31 表；11 张 DORMANT；5 项决策 ACCEPTED；链接 Stage 1 文档 | MB-D-001/002/003/005/018 |
| 2 | `P01-PRODUCT-BASELINE.md` | 0.1.0 Review；四执行模块无 Superseded | 0.1.1；顶部修订说明；§4.2/§6.2/§8 标 Superseded 并重新解释；原文保留 | MB-D-002 |
| 3 | `A03A-MODULE-DECOMPOSITION-REVIEW.md` | 0.3.0 Approved；只讲 15 模块 | 0.3.1；顶部 13 模块修订说明；§4 标历史；新增 §13 目标模块集；G2 正文保留 | MB-D-003 |
| 4 | `A-G2-001-G2-ARCHIVE.md` | §3/§8 写 188 例 | 正文不动；新增 §14 Errata：188→197；R11/R12/S1 说明 | MB-D-006/018 |
| 5 | `MB-S0-07-DECISION-REGISTER.md` | 0.1.1；“全部 PROPOSED，0 ACCEPTED” | 0.2.0；5 项 ACCEPTED（001/002/003/005/018）；13 项 PROPOSED；补 G2-S1；签收表更新 | S0-05 §7 |
| 6 | `DOCUMENT-INDEX.md` | 0.16.0；A03A 描述“推荐 15”；无 A-G2-001/MB-DECISIONS/Stage 1 文档 | 0.17.0；更新 P01 版本/状态；新增 A-G2-001、MB-DECISIONS、8 份 Stage 1 文档 | 索引维护 |
| 7 | `A05-DATA-MODEL-ER.md` | 0.1.0；计划/采购无 DORMANT 标记 | 0.1.1；§4.3/§4.4 标注 11 表 DORMANT | MB-D-005 |
| 8 | `A06-DATA-DICTIONARY.md` | 0.1.0；supplier/§7/§8 无 DORMANT 标记 | 0.1.1；§5.1/§7/§8 标注 DORMANT；字段/表结构未改 | MB-D-005 |

## 2. 历史正文保护

### 2.1 G2 历史报告

`docs/04-readiness/R11`–`R19` 属于 G2 历史报告，**未修改**。验证：

```powershell
git diff --name-status 2b523de..HEAD -- docs/04-readiness
# 期望：无 R11–R19 的 M 行（Stage 0 可能新增其他 readiness 文档，但不得改 R11–R19）
```

### 2.2 A-G2-001

- 文件在 `2b523de` 尚不存在（由 `705e0ca` 新增，属于 G2 收档文档）；
- 本次只在文件末尾追加 §14 Errata；§1–§13 原文与签署表未改；
- 验证：

```powershell
git diff 705e0ca..HEAD -- docs/00-governance/A-G2-001-G2-ARCHIVE.md
# 期望：只有文件末尾新增的 §14 Errata（+ 可能的状态/日期行不变）
```

### 2.3 P01 / A03A

- 原文条目保留；新增的是顶部修订说明、章节标题标注、表格行内删除线/处置说明、§13 目标模块集；
- 没有删除原四模块表、原 15 模块职责表、原成功标准；
- 版本号与状态变化属于修订记录，不是改写历史。

## 3. 决策口径

| 决策 | 状态 | 说明 |
|---|---|---|
| MB-D-001 | ACCEPTED | 接管基线/项目身份 |
| MB-D-002 | ACCEPTED | 范围冻结 |
| MB-D-003 | ACCEPTED | 13 模块 |
| MB-D-005 | ACCEPTED | 11 表 DORMANT |
| MB-D-018 | ACCEPTED | 文档修正 |
| 其余 13 项 | PROPOSED | 不得当作已批准；尤其 MB-D-004/007/008/009 |

## 4. 一致性检查

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-doc-consistency.ps1
```

检查项：

- settings 13 模块，无 planning/procurement/scan/core:model；
- README 含 13 模块、197 例、schema v1/31 表、DORMANT、MB-D-007 PROPOSED，且不含旧的 188/15 模块口径；
- P01 有 Superseded/MB-D-002/采购清单归属；
- A03A 有 13 模块目标与 15 模块历史；
- A-G2-001 有 Errata/197/R11-R12 说明；
- 决策册 5/18 状态与 G2-S1；
- 8 份 Stage 1 文档存在、相对链接可解析。

## 5. 未做

- 未修改 G2 历史报告正文；
- 未把任何 PROPOSED 写成 ACCEPTED；
- 未替负责人签字（只引用 S0-05 已记录的负责人授权）；
- 未修改 `framework-0.1.0` 指向；
- 未删除历史正文。

## 6. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 记录 8 项文档修正、历史正文保护与一致性检查 | 接管工程组 |
