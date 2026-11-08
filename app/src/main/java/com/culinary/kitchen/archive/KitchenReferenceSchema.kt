package com.culinary.kitchen.archive

import com.culinary.core.data.kitchen.KitchenReferenceType

data class ReferenceFieldSpec(
    val key: String,
    val label: String,
    val hint: String,
    val multiline: Boolean = true,
)

data class ReferenceListSpec(
    val key: String,
    val title: String,
    val itemLabel: String,
)

data class ReferenceSchema(
    val fields: List<ReferenceFieldSpec>,
    val lists: List<ReferenceListSpec>,
)

/**
 * 三个参考资料库各自的专业字段。
 *
 * 字段只在实际有内容时写入/展示；不预填数值，不虚构温度、期限或步骤。
 */
object KitchenReferenceSchema {
    val technique =
        ReferenceSchema(
            fields =
                listOf(
                    ReferenceFieldSpec("definition", "技术定义", "这项技术解决的问题、成品形态"),
                    ReferenceFieldSpec("principle", "技术原理", "为什么这样做有效"),
                    ReferenceFieldSpec("scope", "适用范围", "适用食材、场景与限制"),
                    ReferenceFieldSpec("equipment", "设备准备", "炉具、容器、温度计等"),
                    ReferenceFieldSpec("key_control_points", "关键控制点", "温度、时间、状态判断等"),
                    ReferenceFieldSpec("finished_criteria", "成品判断", "如何判断完成"),
                    ReferenceFieldSpec("common_issues", "常见问题", "失败表现与修正方向"),
                    ReferenceFieldSpec("applications", "应用", "可延伸的菜品或用途"),
                ),
            lists =
                listOf(
                    ReferenceListSpec("steps", "操作步骤", "步骤标题"),
                ),
        )

    val production =
        ReferenceSchema(
            fields =
                listOf(
                    ReferenceFieldSpec("purpose", "标准目的", "为什么需要这项标准"),
                    ReferenceFieldSpec("scope", "适用范围", "覆盖的产品、工序或岗位"),
                    ReferenceFieldSpec("prep_requirements", "原料与准备", "原料、工具、前置条件"),
                    ReferenceFieldSpec("process_control", "工序控制", "关键工序和参数"),
                    ReferenceFieldSpec("quality_acceptance", "质量验收", "验收项目与方式"),
                    ReferenceFieldSpec("qualified_criteria", "合格判定", "可接受/不可接受标准"),
                    ReferenceFieldSpec("exception_handling", "异常处理", "偏离标准时的处理"),
                ),
            lists =
                listOf(
                    ReferenceListSpec("standards", "标准项目", "项目"),
                ),
        )

    val storage =
        ReferenceSchema(
            fields =
                listOf(
                    ReferenceFieldSpec("storage_target", "储存对象", "食材、半成品或成品"),
                    ReferenceFieldSpec("storage_method", "储存方式", "冷藏、冷冻、常温等"),
                    ReferenceFieldSpec("temperature", "温度条件", "需有可靠来源；未确认时标注待填写"),
                    ReferenceFieldSpec("packaging", "包装容器", "容器、覆盖、密封要求"),
                    ReferenceFieldSpec("location", "储存位置", "库位/区域/分区要求"),
                    ReferenceFieldSpec("shelf_life", "储存期限", "需有可靠来源；未确认时标注待填写"),
                    ReferenceFieldSpec("label_requirements", "标签要求", "名称、日期、批次、责任人等"),
                    ReferenceFieldSpec("pre_use_check", "使用前检查", "开封/使用前确认项目"),
                    ReferenceFieldSpec("discard_criteria", "废弃判定", "何时必须废弃"),
                ),
            lists =
                listOf(
                    ReferenceListSpec("checks", "检查项目", "检查项"),
                ),
        )

    fun of(type: KitchenReferenceType): ReferenceSchema =
        when (type) {
            KitchenReferenceType.TECHNIQUE -> technique
            KitchenReferenceType.PRODUCTION_STANDARD -> production
            KitchenReferenceType.STORAGE_STANDARD -> storage
        }
}
