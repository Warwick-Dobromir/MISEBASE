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
 * 分类正式数据与分类测试数据的启动 seed。
 *
 * 正式分类从现有档案分类字符串生成；测试分类/测试档案使用独立 batch 标记，
 * 不挂真实档案，并可单独清理。
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
                        if (testState(db) != TEST_STATE_DISABLED) {
                            seedTestCategories(db)
                        }
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

        suspend fun reseedTestData(): Outcome<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val db = databaseProvider.get().openHelper.writableDatabase
                    db.beginTransaction()
                    try {
                        deleteTestData(db)
                        seedTestCategories(db)
                        putMetadata(db, TEST_STATE_KEY, TEST_STATE_SEEDED)
                        db.setTransactionSuccessful()
                        Outcome.success(Unit)
                    } finally {
                        db.endTransaction()
                    }
                }.getOrElse {
                    Outcome.failure(CulinaryError.Data(message = "category test reseed failed", cause = it))
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

        @Suppress("LongMethod")
        private fun seedTestCategories(db: SupportSQLiteDatabase) {
            val now = clock.now().toEpochMilli()
            val categories =
                listOf(
                    TestCategory("test-cat-sauce-basic", "SAUCE", null, "测试 · 基础酱汁", 100),
                    TestCategory("test-cat-sauce-mother", "SAUCE", "test-cat-sauce-basic", "测试 · 法式母酱", 0),
                    TestCategory(
                        "test-cat-sauce-white",
                        "SAUCE",
                        "test-cat-sauce-mother",
                        "测试 · 白色母酱",
                        0,
                    ),
                    TestCategory("test-cat-sauce-brown", "SAUCE", "test-cat-sauce-mother", "测试 · 褐色母酱", 1),
                    TestCategory("test-cat-sauce-fish", "SAUCE", "test-cat-sauce-mother", "测试 · 鱼类母酱", 2),
                    TestCategory("test-cat-sauce-butter", "SAUCE", "test-cat-sauce-basic", "测试 · 黄油乳化酱", 1),
                    TestCategory(
                        "test-cat-sauce-butter-hot",
                        "SAUCE",
                        "test-cat-sauce-butter",
                        "测试 · 热制黄油乳化酱",
                        0,
                    ),
                    TestCategory(
                        "test-cat-sauce-butter-cold",
                        "SAUCE",
                        "test-cat-sauce-butter",
                        "测试 · 冷制黄油乳化酱",
                        1,
                    ),
                    TestCategory("test-cat-sauce-empty", "SAUCE", null, "测试 · 空分类", 101),
                    TestCategory("test-cat-ingredient", "INGREDIENT", null, "测试 · 食材分类", 100),
                    TestCategory(
                        "test-cat-ingredient-herb",
                        "INGREDIENT",
                        "test-cat-ingredient",
                        "测试 · 香草与香料",
                        0,
                    ),
                    TestCategory("test-cat-recipe", "RECIPE", null, "测试 · 菜谱分类", 100),
                    TestCategory("test-cat-technique", "TECHNIQUE", null, "测试 · 技术分类", 100),
                    TestCategory(
                        "test-cat-technique-hot",
                        "TECHNIQUE",
                        "test-cat-technique",
                        "测试 · 热加工",
                        0,
                    ),
                    TestCategory("test-cat-production", "PRODUCTION_STANDARD", null, "测试 · 标准分类", 100),
                    TestCategory("test-cat-storage", "STORAGE_STANDARD", null, "测试 · 储存分类", 100),
                    TestCategory(
                        "test-cat-storage-cold",
                        "STORAGE_STANDARD",
                        "test-cat-storage",
                        "测试 · 冷藏",
                        0,
                    ),
                )
            categories.forEach { category ->
                insertCategory(
                    db = db,
                    id = category.id,
                    library = category.library,
                    parentId = category.parentId,
                    name = category.name,
                    normalizedName = normalize(category.name),
                    englishName = null,
                    sortOrder = category.sortOrder,
                    testBatch = TEST_BATCH,
                    now = now,
                )
            }
            insertTestSauce(
                db = db,
                id = "test-sauce-category-parent",
                name = "测试 · 分类同时含子分类与档案",
                family = "测试 · 法式母酱",
                categoryId = "test-cat-sauce-mother",
                now = now,
            )
            insertTestSauce(
                db = db,
                id = "test-sauce-category-leaf",
                name = "测试 · 末级分类档案",
                family = "测试 · 白色母酱",
                categoryId = "test-cat-sauce-white",
                now = now,
            )
            putMetadata(db, TEST_STATE_KEY, TEST_STATE_SEEDED)
        }

        private fun insertTestSauce(
            db: SupportSQLiteDatabase,
            id: String,
            name: String,
            family: String,
            categoryId: String,
            now: Long,
        ) {
            val exists =
                db
                    .query("SELECT 1 FROM sauce WHERE id = ? LIMIT 1", arrayOf(id))
                    .useRows { it.moveToFirst() }
            if (!exists) {
                val unitId =
                    db
                        .query("SELECT id FROM unit WHERE code = 'g' LIMIT 1")
                        .useRows { if (it.moveToFirst()) it.getString(0) else null }
                        ?: return
                db.execSQL(
                    "INSERT INTO sauce (id,name,normalized_name,family,status,created_at,updated_at," +
                        "revision,english_name,description,seed_id,seed_version,seed_note) " +
                        "VALUES (?,?,?,?, 'ACTIVE', ?, ?, 1, ?, ?, ?, ?, ?)",
                    arrayOf(
                        id,
                        name,
                        normalize(name),
                        family,
                        now,
                        now,
                        "UI Category Test",
                        "分类测试档案（可清理）",
                        id,
                        TEST_BATCH,
                        TEST_BATCH,
                    ),
                )
                db.execSQL(
                    "INSERT INTO sauce_version (id,sauce_id,version_no,status,yield_qty,yield_unit_id," +
                        "instructions_md,steps_json,is_current,created_at,published_at) " +
                        "VALUES (?,?,1,'PUBLISHED',250,?,?,NULL,1,?,?)",
                    arrayOf("$id-version", id, unitId, "分类测试档案", now, now),
                )
            }
            insertAssignment(db, "SAUCE", id, categoryId)
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

        private fun testState(db: SupportSQLiteDatabase): String? =
            db
                .query("SELECT value FROM app_metadata WHERE key = ?", arrayOf(TEST_STATE_KEY))
                .useRows { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

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

        private data class TestCategory(
            val id: String,
            val library: String,
            val parentId: String?,
            val name: String,
            val sortOrder: Int,
        )

        private companion object {
            const val TEST_BATCH = "UI-CATEGORY-TEST-V1"
            const val TEST_STATE_KEY = "archive_category_test_state"
            const val TEST_STATE_SEEDED = "seeded"
            const val TEST_STATE_DISABLED = "disabled"
        }
    }
