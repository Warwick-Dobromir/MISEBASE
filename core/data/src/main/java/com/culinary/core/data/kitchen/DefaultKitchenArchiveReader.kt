package com.culinary.core.data.kitchen

import com.culinary.core.database.dao.ArchiveCategoryAssignmentDao
import com.culinary.core.database.dao.ArchiveCategoryDao
import com.culinary.core.database.entity.ArchiveCategoryEntity
import com.culinary.core.domain.model.ARCHIVE_CATEGORY_TEST_BATCH
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.KitchenReference
import com.culinary.core.domain.model.KitchenReferenceKind
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.usecase.ingredient.ListIngredientsUseCase
import com.culinary.core.domain.usecase.kitchen.ListKitchenReferencesUseCase
import com.culinary.core.domain.usecase.recipe.ListRecipesUseCase
import com.culinary.core.domain.usecase.sauce.ListSaucesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * 六库统一只读投影。
 *
 * 同时读取分类树和 assignment，给每条档案附上真实分类 ID/路径；没有 assignment 的
 * 旧数据回退到旧 category 字段，不让未迁移记录从目录消失。
 */
class DefaultKitchenArchiveReader
    @Inject
    constructor(
        private val listSauces: ListSaucesUseCase,
        private val listIngredients: ListIngredientsUseCase,
        private val listRecipes: ListRecipesUseCase,
        private val listKitchenReferences: ListKitchenReferencesUseCase,
        private val categoryDao: ArchiveCategoryDao,
        private val assignmentDao: ArchiveCategoryAssignmentDao,
    ) : KitchenArchiveReader {
        override fun observeCategories(library: KitchenArchiveKind): Flow<List<KitchenArchiveCategory>> =
            combine(categoryDao.observeAllActive(), assignmentDao.observeAll(), observeEntries()) {
                categories,
                assignments,
                entries,
                ->
                val libraryCategories = categories.filter { it.library == library.name }
                val categoryById = libraryCategories.associateBy { it.id }
                val activeEntryKeys = entries
                    .filter { it.kind.name == library.name && it.testBatch == null }
                    .mapTo(mutableSetOf()) { it.kind.name to it.id }
                val validAssignments = assignments.filter { assignment ->
                    (assignment.recordKind to assignment.recordId) in activeEntryKeys &&
                        categoryById[assignment.categoryId]?.testBatch == null
                }
                val directCounts = validAssignments.groupingBy { it.categoryId }.eachCount()
                val childrenByParent = libraryCategories.filterNot { it.testBatch != null }.groupBy { it.parentId }

                // 一次迭代后序遍历整棵树，避免递归溢出和逐节点重复遍历。
                val descendantCounts = mutableMapOf<String, Int>()
                val visited = mutableSetOf<String>()
                libraryCategories.filterNot { it.testBatch != null }.forEach { start ->
                    if (start.id in visited) return@forEach
                    val pending = ArrayDeque<Pair<String, Boolean>>()
                    pending.addLast(start.id to false)
                    while (pending.isNotEmpty()) {
                        val (id, expanded) = pending.removeLast()
                        if (expanded) {
                            val childTotal = childrenByParent[id].orEmpty().sumOf { child ->
                                descendantCounts[child.id] ?: 0
                            }
                            descendantCounts[id] = (directCounts[id] ?: 0) + childTotal
                        } else if (visited.add(id)) {
                            pending.addLast(id to true)
                            childrenByParent[id].orEmpty().asReversed().forEach { child ->
                                if (child.id !in visited) pending.addLast(child.id to false)
                            }
                        }
                    }
                }

                libraryCategories
                    .filterNot { it.testBatch != null }
                    .sortedWith(compareBy({ it.sortOrder }, { it.name }))
                    .map { entity ->
                        KitchenArchiveCategory(
                            id = entity.id,
                            library = library,
                            parentId = entity.parentId,
                            name = entity.name,
                            englishName = entity.englishName,
                            sortOrder = entity.sortOrder,
                            testBatch = entity.testBatch,
                            directCount = directCounts[entity.id] ?: 0,
                            descendantCount = descendantCounts[entity.id] ?: (directCounts[entity.id] ?: 0),
                            childCount = childrenByParent[entity.id].orEmpty().size,
                        )
                    }
            }

        override fun observeEntries(): Flow<List<KitchenArchiveEntry>> {
            val lists =
                combine(
                    listSauces(),
                    listIngredients(),
                    listRecipes(),
                    listKitchenReferences(),
                ) { sauces, ingredients, recipes, references ->
                    RawLists(sauces, ingredients, recipes, references)
                }
            return combine(lists, categoryDao.observeAllActive(), assignmentDao.observeAll()) {
                raw,
                categories,
                assignments,
                ->
                val categoryById = categories.associateBy { it.id }
                val assignmentByRecord =
                    assignments.associateBy { it.recordKind to it.recordId }
                buildList {
                    raw.sauces.forEach { sauce ->
                        add(sauce.toArchiveEntry(categoryById, assignmentByRecord))
                    }
                    raw.ingredients.forEach { ingredient ->
                        add(ingredient.toArchiveEntry(categoryById, assignmentByRecord))
                    }
                    raw.recipes.forEach { recipe ->
                        add(recipe.toArchiveEntry(categoryById, assignmentByRecord))
                    }
                    raw.references.forEach { reference ->
                        add(reference.toArchiveEntry(categoryById, assignmentByRecord))
                    }
                }
            }
        }

        private data class RawLists(
            val sauces: List<Sauce>,
            val ingredients: List<Ingredient>,
            val recipes: List<Recipe>,
            val references: List<KitchenReference>,
        )

        private fun Sauce.toArchiveEntry(
            categoryById: Map<String, ArchiveCategoryEntity>,
            assignments: Map<Pair<String, String>, com.culinary.core.database.entity.ArchiveCategoryAssignmentEntity>,
        ): KitchenArchiveEntry =
            entry(
                kind = KitchenArchiveKind.SAUCE,
                id = id.value,
                name = name,
                subtitle = flavorProfile ?: usageNotes,
                legacyCategory = family,
                recordTestBatch = seedVersion.takeIf { it == ARCHIVE_CATEGORY_TEST_BATCH },
                searchText =
                    listOfNotNull(
                        name,
                        family,
                        texture,
                        flavorProfile,
                        usageNotes,
                        storageNotes,
                        englishName,
                        mainIngredients,
                        preparationSummary,
                        keyParameters,
                        adjustableNotes,
                        seedNote,
                    ).joinToString(" "),
                categoryById = categoryById,
                assignments = assignments,
            )

        private fun Ingredient.toArchiveEntry(
            categoryById: Map<String, ArchiveCategoryEntity>,
            assignments: Map<Pair<String, String>, com.culinary.core.database.entity.ArchiveCategoryAssignmentEntity>,
        ): KitchenArchiveEntry =
            entry(
                kind = KitchenArchiveKind.INGREDIENT,
                id = id.value,
                name = name,
                subtitle = description ?: exampleProducts ?: storageNotes,
                legacyCategory = category,
                recordTestBatch = seedVersion.takeIf { it == ARCHIVE_CATEGORY_TEST_BATCH },
                searchText =
                    listOfNotNull(
                        name,
                        category,
                        description,
                        englishName,
                        exampleProducts,
                        storageNotes,
                        allergenNotes,
                        seedNote,
                    ).joinToString(" "),
                categoryById = categoryById,
                assignments = assignments,
            )

        private fun KitchenReference.toArchiveEntry(
            categoryById: Map<String, ArchiveCategoryEntity>,
            assignments: Map<Pair<String, String>, com.culinary.core.database.entity.ArchiveCategoryAssignmentEntity>,
        ): KitchenArchiveEntry =
            entry(
                kind = kind.toArchiveKind(),
                id = id.value,
                name = name,
                subtitle = description,
                legacyCategory = category,
                recordTestBatch = seedVersion.takeIf { it == ARCHIVE_CATEGORY_TEST_BATCH },
                searchText = listOfNotNull(name, category, description, englishName, seedNote).joinToString(" "),
                categoryById = categoryById,
                assignments = assignments,
            )

        private fun Recipe.toArchiveEntry(
            categoryById: Map<String, ArchiveCategoryEntity>,
            assignments: Map<Pair<String, String>, com.culinary.core.database.entity.ArchiveCategoryAssignmentEntity>,
        ): KitchenArchiveEntry =
            entry(
                kind = KitchenArchiveKind.RECIPE,
                id = id.value,
                name = name,
                subtitle = englishName,
                legacyCategory = category.name,
                recordTestBatch = seedVersion.takeIf { it == ARCHIVE_CATEGORY_TEST_BATCH },
                searchText =
                    listOfNotNull(
                        name,
                        englishName,
                        allergenNotes,
                        ingredientSummary,
                        seedNote,
                    ).joinToString(" "),
                categoryById = categoryById,
                assignments = assignments,
            )

        private fun entry(
            kind: KitchenArchiveKind,
            id: String,
            name: String,
            subtitle: String?,
            legacyCategory: String?,
            recordTestBatch: String?,
            searchText: String,
            categoryById: Map<String, ArchiveCategoryEntity>,
            assignments: Map<Pair<String, String>, com.culinary.core.database.entity.ArchiveCategoryAssignmentEntity>,
        ): KitchenArchiveEntry {
            val assignment = assignments[kind.name to id]
            val category = assignment?.categoryId?.let(categoryById::get)
            return KitchenArchiveEntry(
                id = id,
                kind = kind,
                name = name,
                subtitle = subtitle,
                category = category?.name ?: legacyCategory,
                searchText = listOfNotNull(searchText, category?.name).joinToString(" "),
                categoryId = category?.id,
                categoryPath = category?.let { buildPath(it, categoryById) }.orEmpty(),
                testBatch = recordTestBatch ?: category?.testBatch,
            )
        }

        private fun buildPath(
            category: ArchiveCategoryEntity,
            categoryById: Map<String, ArchiveCategoryEntity>,
        ): List<String> {
            val path = mutableListOf<String>()
            var cursor: ArchiveCategoryEntity? = category
            val visited = mutableSetOf<String>()
            while (cursor != null && visited.add(cursor.id)) {
                path.add(0, cursor.name)
                cursor = cursor.parentId?.let(categoryById::get)
            }
            return path
        }

        private fun KitchenReferenceKind.toArchiveKind(): KitchenArchiveKind =
            when (this) {
                KitchenReferenceKind.TECHNIQUE -> KitchenArchiveKind.TECHNIQUE
                KitchenReferenceKind.PRODUCTION_STANDARD -> KitchenArchiveKind.PRODUCTION_STANDARD
                KitchenReferenceKind.STORAGE_STANDARD -> KitchenArchiveKind.STORAGE_STANDARD
            }
    }
