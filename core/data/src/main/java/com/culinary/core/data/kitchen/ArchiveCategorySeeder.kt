package com.culinary.core.data.kitchen

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.database.CulinaryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * 分类正式数据初始化与历史测试分类清理。
 *
 * 正式分类从现有档案分类字符串生成；升级启动时删除遗留测试分类及其测试档案，
 * 并保留所有正式档案与非测试分类。
 */
@Singleton
class ArchiveCategorySeeder
    @Inject
    constructor(
        private val databaseProvider: Provider<CulinaryDatabase>,
        private val clock: Clock,
    ) {
        suspend fun ensureSeeded(): Outcome<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val db = databaseProvider.get().openHelper.writableDatabase
                    db.beginTransaction()
                    try {
                        seedFormalCategories(db)
                        // 正式启动时清除历史测试分类及其测试档案，且不再自动回填。
                        deleteTestData(db)
                        putMetadata(db, TEST_STATE_KEY, TEST_STATE_DISABLED)
                        db.setTransactionSuccessful()
                        Outcome.success(Unit)
                    } finally {
                        db.endTransaction()
                    }
                }.getOrElse {
                    Outcome.failure(
                        CulinaryError.Data(
                            message = "archive category seed failed",
                            cause = it,
                        ),
                    )
                }
            }

        suspend fun clearTestData(): Outcome<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val db = databaseProvider.get().openHelper.writableDatabase
                    db.beginTransaction()
                    try {
                        deleteTestData(db)
                        putMetadata(db, TEST_STATE_KEY, TEST_STATE_DISABLED)
                        db.setTransactionSuccessful()
                        Outcome.success(Unit)
                    } finally {
                        db.endTransaction()
                    }
                }.getOrElse {
                    Outcome.failure(CulinaryError.Data(message = "category test cleanup failed", cause = it))
                }
            }

        private fun seedFormalCategories(db: SupportSQLiteDatabase) {
            seedLibrary(
                db = db,
                library = "SAUCE",
                query =
                    "SELECT family, MIN(rowid) AS first_row FROM sauce " +
                        "WHERE family IS NOT NULL AND trim(family) <> '' " +
                        "AND (seed_version IS NULL OR seed_version <> '$TEST_BATCH') " +
                        "GROUP BY family ORDER BY first_row",
                recordQuery =
                    "SELECT id, family FROM sauce WHERE family IS NOT NULL " +
                        "AND (seed_version IS NULL OR seed_version <> '$TEST_BATCH')",
                recordKind = "SAUCE",
                categoryValueIndex = 1,
            )
            seedLibrary(
                db = db,
                library = "INGREDIENT",
                query =
                    "SELECT category, MIN(rowid) AS first_row FROM ingredient " +
                        "WHERE category IS NOT NULL AND trim(category) <> '' " +
                        "AND (seed_version IS NULL OR seed_version <> '$TEST_BATCH') " +
                        "GROUP BY category ORDER BY first_row",
                recordQuery =
                    "SELECT id, category FROM ingredient WHERE category IS NOT NULL " +
                        "AND (seed_version IS NULL OR seed_version <> '$TEST_BATCH')",
                recordKind = "INGREDIENT",
                categoryValueIndex = 1,
            )
            seedRecipeCategories(db)
            seedReferenceCategories(db)
        }

        private fun seedRecipeCategories(db: SupportSQLiteDatabase) {
            val rows =
                db
                    .query(
                        "SELECT category, MIN(rowid) AS first_row FROM recipe " +
                            "WHERE (seed_version IS NULL OR seed_version <> '$TEST_BATCH') " +
                            "GROUP BY category ORDER BY first_row",
                    ).useRows { cursor ->
                        buildList {
                            while (cursor.moveToNext()) {
                                add(cursor.getString(0) to cursor.getLong(1))
                            }
                        }
                    }
            val categoryIds = linkedMapOf<String, String>()
            rows.forEachIndexed { index, (raw, _) ->
                val id = categoryId("RECIPE", raw)
                categoryIds[raw] = id
                insertCategory(
                    db = db,
                    id = id,
                    library = "RECIPE",
                    parentId = null,
                    name = recipeCategoryLabel(raw),
                    normalizedName = normalize(raw),
                    englishName = raw,
                    sortOrder = index,
                )
            }
            db
                .query(
                    "SELECT id, category FROM recipe " +
                        "WHERE (seed_version IS NULL OR seed_version <> '$TEST_BATCH')",
                ).useRows { cursor ->
                    while (cursor.moveToNext()) {
                        val kind = "RECIPE"
                        val recordId = cursor.getString(0)
                        val categoryId = categoryIds[cursor.getString(1)] ?: continue
                        insertAssignment(db, kind, recordId, categoryId)
                    }
                }
        }

        private fun seedReferenceCategories(db: SupportSQLiteDatabase) {
            val rows =
                db
                    .query(
                        "SELECT kind, category, MIN(rowid) AS first_row FROM kitchen_reference " +
                            "WHERE category IS NOT NULL AND trim(category) <> '' " +
                            "AND (seed_version IS NULL OR seed_version <> '$TEST_BATCH') " +
                            "GROUP BY kind, category ORDER BY first_row",
                    ).useRows { cursor ->
                        buildList {
                            while (cursor.moveToNext()) {
                                add(Triple(cursor.getString(0), cursor.getString(1), cursor.getLong(2)))
                            }
                        }
                    }
            val categoryIds = mutableMapOf<Pair<String, String>, String>()
            rows.forEachIndexed { index, (kind, name, _) ->
                val id = categoryId(kind, name)
                categoryIds[kind to name] = id
                insertCategory(
                    db = db,
                    id = id,
                    library = kind,
                    parentId = null,
                    name = name,
                    normalizedName = normalize(name),
                    englishName = null,
                    sortOrder = index,
                )
            }
            db
                .query(
                    "SELECT id, kind, category FROM kitchen_reference " +
                        "WHERE category IS NOT NULL " +
                        "AND (seed_version IS NULL OR seed_version <> '$TEST_BATCH')",
                ).useRows { cursor ->
                    while (cursor.moveToNext()) {
                        val recordId = cursor.getString(0)
                        val kind = cursor.getString(1)
                        val category = cursor.getString(2)
                        val categoryId = categoryIds[kind to category] ?: continue
                        insertAssignment(db, kind, recordId, categoryId)
                    }
                }
        }

        private fun seedLibrary(
            db: SupportSQLiteDatabase,
            library: String,
            query: String,
            recordQuery: String,
            recordKind: String,
            categoryValueIndex: Int,
        ) {
            val rows =
                db.query(query).useRows { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(cursor.getString(0) to cursor.getLong(1))
                        }
                    }
                }
            val categoryIds = linkedMapOf<String, String>()
            rows.forEachIndexed { index, (name, _) ->
                val id = categoryId(library, name)
                categoryIds[name] = id
                insertCategory(
                    db = db,
                    id = id,
                    library = library,
                    parentId = null,
                    name = name,
                    normalizedName = normalize(name),
                    englishName = null,
                    sortOrder = index,
                )
            }
            db.query(recordQuery).useRows { cursor ->
                while (cursor.moveToNext()) {
                    val recordId = cursor.getString(0)
                    val category = cursor.getString(categoryValueIndex)
                    val categoryId = categoryIds[category] ?: continue
                    insertAssignment(db, recordKind, recordId, categoryId)
                }
            }
        }

        private fun deleteTestData(db: SupportSQLiteDatabase) {
            val sauceIds =
                db
                    .query("SELECT id FROM sauce WHERE seed_version = ?", arrayOf(TEST_BATCH))
                    .useRows { cursor ->
                        buildList {
                            while (cursor.moveToNext()) add(cursor.getString(0))
                        }
                    }
            sauceIds.forEach { id ->
                db.execSQL("DELETE FROM sauce WHERE id = ?", arrayOf(id))
                db.execSQL(
                    "DELETE FROM archive_category_assignment WHERE record_kind = 'SAUCE' AND record_id = ?",
                    arrayOf(id),
                )
            }
            db.execSQL(
                "DELETE FROM archive_category_assignment WHERE category_id IN " +
                    "(SELECT id FROM archive_category WHERE test_batch = ?)",
                arrayOf(TEST_BATCH),
            )
            // 先断开测试树的父子引用，避免自引用外键 RESTRICT 阻止整批删除。
            db.execSQL(
                "UPDATE archive_category SET parent_id = NULL WHERE test_batch = ?",
                arrayOf(TEST_BATCH),
            )
            db.execSQL("DELETE FROM archive_category WHERE test_batch = ?", arrayOf(TEST_BATCH))
            deleteCategoriesWithTestPrefix(db)
        }

        /**
         * 清除历史遗留的“测试…”分类（不限于当前 test_batch）。
         * 若测试分类下意外存在正式子分类，先将其逐级提升到最近的非测试祖先，
         * 不删除正式分类，也不触碰其档案本身。
         */
        private fun deleteCategoriesWithTestPrefix(db: SupportSQLiteDatabase) {
            db.execSQL(
                "DELETE FROM archive_category_assignment WHERE category_id IN " +
                    "(SELECT id FROM archive_category WHERE name LIKE '测试%')",
            )
            while (true) {
                val hasNonTestChild =
                    db.query(
                        "SELECT 1 FROM archive_category child " +
                            "JOIN archive_category parent ON child.parent_id = parent.id " +
                            "WHERE parent.name LIKE '测试%' AND child.name NOT LIKE '测试%' LIMIT 1",
                    )
                        .useRows { it.moveToFirst() }
                if (!hasNonTestChild) break
                db.execSQL(
                    "UPDATE archive_category SET parent_id = " +
                        "(SELECT parent_id FROM archive_category parent " +
                        "WHERE parent.id = archive_category.parent_id) " +
                        "WHERE parent_id IN " +
                        "(SELECT id FROM archive_category WHERE name LIKE '测试%') " +
                        "AND name NOT LIKE '测试%'",
                )
            }
            db.execSQL("UPDATE archive_category SET parent_id = NULL WHERE name LIKE '测试%'")
            db.execSQL("DELETE FROM archive_category WHERE name LIKE '测试%'")
        }

        private fun insertCategory(
            db: SupportSQLiteDatabase,
            id: String,
            library: String,
            parentId: String?,
            name: String,
            normalizedName: String,
            englishName: String?,
            sortOrder: Int,
            testBatch: String? = null,
            now: Long = clock.now().toEpochMilli(),
        ) {
            db.execSQL(
                "INSERT OR IGNORE INTO archive_category " +
                    "(id,library,parent_id,name,normalized_name,english_name,sort_order,status," +
                    "test_batch,created_at,updated_at,revision) " +
                    "VALUES (?,?,?,?,?,?,?,'ACTIVE',?,?,?,1)",
                arrayOf(
                    id,
                    library,
                    parentId,
                    name,
                    normalizedName,
                    englishName,
                    sortOrder,
                    testBatch,
                    now,
                    now,
                ),
            )
        }

        private fun insertAssignment(
            db: SupportSQLiteDatabase,
            recordKind: String,
            recordId: String,
            categoryId: String,
        ) {
            val now = clock.now().toEpochMilli()
            val id =
                UUID
                    .nameUUIDFromBytes("MISEBASE-CATEGORY-ASSIGN:$recordKind:$recordId".toByteArray())
                    .toString()
            db.execSQL(
                "INSERT OR IGNORE INTO archive_category_assignment " +
                    "(id,record_kind,record_id,category_id,created_at,updated_at) VALUES (?,?,?,?,?,?)",
                arrayOf(id, recordKind, recordId, categoryId, now, now),
            )
        }

        private fun categoryId(
            library: String,
            value: String,
        ): String = UUID.nameUUIDFromBytes("MISEBASE-CATEGORY:$library:${normalize(value)}".toByteArray()).toString()

        private fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)

        private fun recipeCategoryLabel(raw: String): String =
            when (raw) {
                "STOCK" -> "汤底 / 高汤"
                "PREP" -> "预制件"
                "COMPONENT" -> "组件"
                "DISH" -> "菜品"
                "OTHER" -> "其他"
                else -> raw
            }

        private fun putMetadata(
            db: SupportSQLiteDatabase,
            key: String,
            value: String,
        ) {
            db.execSQL(
                "INSERT OR REPLACE INTO app_metadata (key,value,updated_at) VALUES (?,?,?)",
                arrayOf(key, value, clock.now().toEpochMilli()),
            )
        }

        private fun <T> Cursor.useRows(block: (Cursor) -> T): T = use(block)

        private companion object {
            const val TEST_BATCH = "UI-CATEGORY-TEST-V1"
            const val TEST_STATE_KEY = "archive_category_test_state"
            const val TEST_STATE_DISABLED = "disabled"
        }
    }
