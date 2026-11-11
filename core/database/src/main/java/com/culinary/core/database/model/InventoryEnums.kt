package com.culinary.core.database.model

/** 库存档案类型（v9）：普通食材档案，或绑定酱汁档案的现货档案。 */
enum class ArchiveKindDb {
    INGREDIENT,
    SAUCE,
}

/** 出库单来源（v9）：手工 / 菜谱 / 酱汁补足。 */
enum class IssueSlipTypeDb {
    MANUAL,
    RECIPE,
    SAUCE,
}

/** 出库单状态（v9）：只有确认成功才会落行，保留 DRAFT/VOID 供后续扩展。 */
enum class IssueSlipStatusDb {
    DRAFT,
    CONFIRMED,
    VOID,
}

/** 打印任务类型（v9）：入库单 / 出库单 / 批次标签。 */
enum class PrintJobKindDb {
    RECEIPT,
    ISSUE,
    LABEL,
}

/** 打印任务状态（v9）；失败可重打，库存结果不受影响。 */
enum class PrintJobStatusDb {
    PENDING,
    PRINTED,
    FAILED,
}

/** 冻结记录动作（v9）。 */
enum class FreezeActionDb {
    FREEZE,
    UNFREEZE,
}

/** 盘点范围（v9）：全部 / 分类 / 库位 / 单一档案。 */
enum class StocktakeScopeDb {
    ALL,
    CATEGORY,
    LOCATION,
    ARCHIVE,
}

/** 盘点状态（v9）：草稿（报告）→ 已确认（调整生效）。 */
enum class StocktakeStatusDb {
    DRAFT,
    CONFIRMED,
}
