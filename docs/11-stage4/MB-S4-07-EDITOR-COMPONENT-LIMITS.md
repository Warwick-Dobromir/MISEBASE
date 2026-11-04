# MB-S4-07 编辑器组成行限制处理说明

**文档编号**：MB-S4-07
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-05
**范围**：酱汁编辑器对子菜谱/子酱汁组成行的只读保护

---

## 1. 问题

Stage 3 的酱汁编辑器只把 `ComponentTarget.Ingredient` 映射到编辑行，保存时只写回食材行。若导入数据包含子菜谱/子酱汁组成行，保存会**静默丢弃**这些行，造成数据损失。

---

## 2. 本阶段处理

采用**只读保护**，不把编辑入口扩展到子菜谱/子酱汁：

- `SauceDraftLineInput` 由 `ingredientId` 扩展为 `ComponentTarget`；
- 编辑器载入全部组成行；
- 子菜谱/子酱汁行显示“只读保留”，不提供数量、可选、删除操作；
- 保存草稿时按原 `target` 原样回写（数量/单位/可选标记保持不变）；
- 页面提示：“子菜谱 / 子酱汁组成行来自导入数据，本阶段只读保留；保存草稿会原样写回，不会丢失。”

---

## 3. 领域契约

```kotlin
data class SauceDraftLineInput(
    val target: ComponentTarget,
    val amount: Double,
    val unit: Unit,
    val isOptional: Boolean,
)
```

保留旧构造器 `SauceDraftLineInput(ingredientId, amount, unit, isOptional)`，兼容 Stage 3 调用方与测试。

`SaveSauceDraftUseCase` 现在直接使用 `line.target`，不再强制转换为食材。

---

## 4. 详情标签

`GetSauceDetailUseCase` 对三类目标解析可读标签：

- 食材：食材名；
- 子菜谱：菜谱名 + 固定版本号；
- 子酱汁：酱汁名 + 固定版本号。

---

## 5. 测试

- Domain：保存含子菜谱/子酱汁的草稿，目标与可选标记保留；
- ViewModel：载入含三类目标的版本，只读标记正确；保存后最新版本仍包含三个目标；
- Compose：编辑器显示只读提示与“只读保留”徽标。

---

## 6. 边界

- 不在本阶段新增“添加子菜谱/子酱汁”入口；
- 只读行不可修改，避免在 UI 层制造无法通过图校验的半成品；
- 正式编辑支持可在后续阶段单独评估，不属于 Stage 4。

---

## 7. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-05 | 组成行只读保护说明 | 接管工程组 |
