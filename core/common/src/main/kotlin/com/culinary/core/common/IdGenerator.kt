package com.culinary.core.common

/**
 * 主键生成端口。
 *
 * 只承诺返回一个非空不透明字符串。具体格式与生成算法（UUID v4）属于 D-005，
 * 该决策仍为 `PROPOSED`，因此在 D-005 被接受、阶段 3 实施前**不提供实现**，
 * 也不在领域模型里解析或校验 UUID 结构。
 */
fun interface IdGenerator {
    fun newId(): String
}
