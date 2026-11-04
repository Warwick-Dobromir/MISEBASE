# MB-S3-07 Repository 事务边界说明

**文档编号**：MB-S3-07
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**依据**：E02 §4.2 / §4.3

---

## 1. 规则

1. 事务边界在 **Use Case**，不在 Repository、不在 UI；
2. 所有写操作（新增/修改/归档/发布）必须在 `TransactionRunner.write { }` 内调用；
3. Repository 只提供原子操作片段，不自行 `withTransaction`；
4. `TransactionRunner` 不可重入，禁止嵌套；
5. 事务内不做文件/网络/图片解码；
6. 失败时由 Runner 统一映射错误；不得留下部分写入。

## 2. 本阶段写用例核对

| Use Case | 写操作 | 事务 |
|---|---|---|
| `SaveIngredientUseCase` | 档案保存 | ✅ `write` |
| `ArchiveIngredientUseCase` | 归档 | ✅ `write` |
| `SaveSauceUseCase` | 酱汁档案 | ✅ `write` |
| `SaveSauceDraftUseCase` | 版本 + 组成行 | ✅ `write`（版本号也在事务内计算） |
| `PublishSauceVersionUseCase` | 状态/当前指针 | ✅ `write` |
| `SaveRecipeUseCase` | 菜谱档案 | ✅ `write` |
| `SaveRecipeDraftUseCase` | 版本 + 组成行 + 当前版本解析 | ✅ `write` |
| `PublishRecipeDraftUseCase` | 固定版本 + 校验 + 发布 | ✅ `write` |
| `PublishStoredRecipeVersionUseCase` | 校验 + 发布 | ✅ `write` |

## 3. Repository 行为

- `SauceRepositoryImpl` / `RecipeRepositoryImpl` 不开启事务；
- 发布时先清空旧当前指针，再写新状态/新行，最后设置当前指针（同一事务内）；
- 已发布版本只允许状态/当前指针变化，内容由数据库触发器保护；
- 事务内读取（版本、当前指针、子图预取）与写入使用同一连接。

## 4. 测试

`TransactionBoundaryTest`（4 例）：

1. 食材保存成功路径确实经过 Runner（计数 = 1）；
2. Runner 失败 → 酱汁草稿不落库；
3. Runner 失败 → 菜谱发布不落库；
4. Runner 失败 → 食材不落库。

## 5. 已知边界

- 事务内预取子图使用的是 Repository 读方法，与写在同一事务连接；
- 发布时的"唯一当前版本"最终由部分唯一索引兜底；
- 并发写由 `RoomTransactionRunner` 的应用级 Mutex + 单写执行器串行化。
