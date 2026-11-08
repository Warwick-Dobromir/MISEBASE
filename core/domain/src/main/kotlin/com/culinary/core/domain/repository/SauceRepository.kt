package com.culinary.core.domain.repository

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import kotlinx.coroutines.flow.Flow

/**
 * 酱汁聚合的读写端口（与 [RecipeRepository] 对称）。
 *
 * 方法只做原子操作片段；事务边界由 Use Case 通过 `TransactionRunner` 控制。
 */
interface SauceRepository {
    fun observeActive(): Flow<List<Sauce>>

    suspend fun get(id: SauceId): Outcome<Sauce>

    suspend fun getVersion(id: SauceVersionId): Outcome<SauceVersion>

    /** 按版本号倒序返回全部版本（含草稿与已发布）。 */
    suspend fun listVersions(sauceId: SauceId): List<SauceVersion>

    suspend fun findCurrentVersion(sauceId: SauceId): SauceVersion?

    /** 下一个可用的版本号（从不复用，已发布版本号不回填）。 */
    suspend fun nextVersionNo(sauceId: SauceId): Int

    suspend fun save(sauce: Sauce): Outcome<SauceId>

    suspend fun saveDraft(version: SauceVersion): Outcome<SauceVersionId>

    /**
     * 发布版本：写入 `PUBLISHED` 并切换"当前版本"。
     *
     * 已发布的行只允许状态/当前指针变化；内容字段由数据库触发器保护。
     */
    suspend fun savePublished(version: SauceVersion): Outcome<SauceVersionId>

    /** 归档档案；不物理删除，历史版本与引用关系保持。 */
    suspend fun archive(id: SauceId): Outcome<kotlin.Unit>
}
