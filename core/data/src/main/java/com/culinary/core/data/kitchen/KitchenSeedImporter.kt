package com.culinary.core.data.kitchen

import com.culinary.core.common.AppLogger
import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.database.dao.AppMetadataDao
import com.culinary.core.database.dao.IngredientDao
import com.culinary.core.database.dao.KitchenReferenceDao
import com.culinary.core.database.dao.KitchenRelationDao
import com.culinary.core.database.dao.RecipeDao
import com.culinary.core.database.dao.SauceDao
import com.culinary.core.database.entity.AppMetadataEntity
import com.culinary.core.database.entity.KitchenRelationEntity
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.KitchenReference
import com.culinary.core.domain.model.KitchenReferenceId
import com.culinary.core.domain.model.KitchenReferenceKind
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeCategory
import com.culinary.core.domain.model.RecipeComponent
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.KitchenReferenceRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.SauceRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.rule.common.NameText
import com.culinary.core.domain.transaction.TransactionRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 厨房测试种子导入器。
 *
 * 只走 Repository / 事务写入，不直接伪造 UI 数据；测试记录以 `seed_id/seed_version`
 * 标记，支持幂等初始化、重新导入和单独清理。导入失败时整个事务回滚，不会留下半套数据。
 */
@Singleton
class KitchenSeedImporter
    @Inject
    constructor(
        private val seedReader: KitchenSeedReader,
        private val ingredientRepository: IngredientRepository,
        private val sauceRepository: SauceRepository,
        private val recipeRepository: RecipeRepository,
        private val referenceRepository: KitchenReferenceRepository,
        private val unitRepository: UnitRepository,
        private val transactionRunner: TransactionRunner,
        private val metadataDao: AppMetadataDao,
        private val relationDao: KitchenRelationDao,
        private val sauceDao: SauceDao,
        private val ingredientDao: IngredientDao,
        private val recipeDao: RecipeDao,
        private val referenceDao: KitchenReferenceDao,
        private val clock: Clock,
        private val logger: AppLogger,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        suspend fun status(): KitchenSeedStatus =
            withContext(Dispatchers.IO) {
                val version = metadataDao.find(VERSION_KEY)?.value
                val disabled = metadataDao.find(DISABLED_KEY)?.value == "true"
                val counts =
                    if (version == null) {
                        KitchenSeedCounts(relations = relationDao.countSeed())
                    } else {
                        KitchenSeedCounts(
                            ingredients = ingredientDao.countSeed(version),
                            sauces = sauceDao.countSeed(version),
                            recipes = recipeDao.countSeed(version),
                            references = referenceDao.countSeed(version),
                            relations = relationDao.countSeed(),
                        )
                    }
                KitchenSeedStatus(version = version, disabled = disabled, counts = counts)
            }

        /** 启动时调用；已导入或用户清理过时直接跳过。 */
        suspend fun ensureImported(): Outcome<KitchenSeedImportResult> =
            withContext(Dispatchers.IO) {
                val seed = readSeed()
                val existingVersion = metadataDao.find(VERSION_KEY)?.value
                val disabled = metadataDao.find(DISABLED_KEY)?.value == "true"
                when {
                    disabled ->
                        Outcome.success(
                            KitchenSeedImportResult(
                                imported = false,
                                version = seed.version,
                                counts = status().counts,
                            ),
                        )

                    existingVersion == seed.version ->
                        Outcome.success(
                            KitchenSeedImportResult(
                                imported = false,
                                version = seed.version,
                                counts = status().counts,
                            ),
                        )

                    else -> import(seed, force = false)
                }
            }

        /** 用户在设置页选择重新导入：先清理旧种子，再写整套 v1。 */
        suspend fun reimport(): Outcome<KitchenSeedImportResult> =
            withContext(Dispatchers.IO) {
                import(readSeed(), force = true)
            }

        /** 用户清理测试种子；写入 disabled 标记，避免下次启动自动回填。 */
        suspend fun clear(): Outcome<KitchenSeedStatus> =
            withContext(Dispatchers.IO) {
                val seedVersion = metadataDao.find(VERSION_KEY)?.value ?: SEED_VERSION
                val result =
                    transactionRunner.write {
                        clearSeedRows()
                        metadataDao.delete(VERSION_KEY)
                        metadataDao.put(
                            AppMetadataEntity(
                                key = DISABLED_KEY,
                                value = "true",
                                updatedAt = clock.now().toEpochMilli(),
                            ),
                        )
                        seedVersion
                    }
                when (result) {
                    is Outcome.Success -> Outcome.success(status())
                    is Outcome.Failure -> result
                }
            }

        private suspend fun import(
            seed: KitchenSeedFile,
            force: Boolean,
        ): Outcome<KitchenSeedImportResult> {
            val units = unitRepository.all().associateBy { it.code }
            val data = buildSeedData(seed, units)
            val now = clock.now().toEpochMilli()
            val result =
                transactionRunner.write {
                    clearSeedRows()
                    data.ingredients.forEach { ingredient -> saveOrThrow(ingredientRepository.save(ingredient)) }
                    data.sauces.forEach { sauce ->
                        saveOrThrow(sauceRepository.save(sauce.sauce))
                        saveOrThrow(sauceRepository.savePublished(sauce.version))
                    }
                    data.recipes.forEach { recipe ->
                        saveOrThrow(recipeRepository.save(recipe.recipe))
                        saveOrThrow(recipeRepository.savePublished(recipe.version))
                    }
                    data.references.forEach { reference ->
                        saveOrThrow(referenceRepository.save(reference))
                    }
                    relationDao.insertAll(data.relations)
                    metadataDao.delete(DISABLED_KEY)
                    metadataDao.put(
                        AppMetadataEntity(
                            key = VERSION_KEY,
                            value = seed.version,
                            updatedAt = now,
                        ),
                    )
                    data.counts
                }
            return when (result) {
                is Outcome.Success -> {
                    logger.info(
                        "kitchen_seed_imported",
                        mapOf(
                            "version" to seed.version,
                            "force" to force,
                            "ingredients" to result.value.ingredients,
                            "sauces" to result.value.sauces,
                            "recipes" to result.value.recipes,
                            "references" to result.value.references,
                            "relations" to result.value.relations,
                        ),
                    )
                    Outcome.success(
                        KitchenSeedImportResult(
                            imported = true,
                            version = seed.version,
                            counts = result.value,
                        ),
                    )
                }

                is Outcome.Failure -> {
                    logger.warn("kitchen_seed_import_failed", null, mapOf("version" to seed.version))
                    result
                }
            }
        }

        private suspend fun clearSeedRows() {
            relationDao.deleteAllSeed()
            recipeDao.deleteAllSeed()
            sauceDao.deleteAllSeed()
            referenceDao.deleteAllSeed()
            ingredientDao.deleteAllSeed()
        }

        private suspend fun readSeed(): KitchenSeedFile {
            val text = seedReader.read()
            return json.decodeFromString<KitchenSeedFile>(text)
        }

        @Suppress("LongMethod") // 一次性构造 68 条种子及 260 条关系的纯映射，拆开反而隐藏顺序
        private fun buildSeedData(
            seed: KitchenSeedFile,
            units: Map<String, Unit>,
        ): SeedData {
            fun unit(code: String): Unit =
                units[code] ?: throw SeedAbort(
                    CulinaryError.Validation(field = "kitchenSeed.unit", rule = "missing unit $code"),
                )

            fun id(seedId: String): String =
                UUID.nameUUIDFromBytes("$ID_NAMESPACE:$seedId".toByteArray(Charsets.UTF_8)).toString()

            val names =
                buildMap {
                    seed.ingredients.forEach { put(it.seedId, it.name) }
                    seed.sauces.forEach { put(it.seedId, it.name) }
                    seed.recipes.forEach { put(it.seedId, it.name) }
                    seed.references.forEach { put(it.seedId, it.name) }
                }

            val ingredients =
                seed.ingredients.map { item ->
                    Ingredient(
                        id = IngredientId(id(item.seedId)),
                        name = item.name,
                        normalizedName = NameText.normalize(item.name),
                        defaultUnitId = unit(item.unitCode).id,
                        status = EntityStatus.ACTIVE,
                        category = item.category,
                        storageNotes = item.storageNotes,
                        allergenNotes = item.allergenNotes,
                        englishName = item.englishName,
                        exampleProducts = item.exampleProducts,
                        seedNote = item.seedNote,
                        seedId = item.seedId,
                        seedVersion = seed.version,
                    )
                }

            val sauces =
                seed.sauces.map { item ->
                    val sauceId = SauceId(id(item.seedId))
                    val sauce =
                        Sauce(
                            id = sauceId,
                            name = item.name,
                            normalizedName = NameText.normalize(item.name),
                            status = EntityStatus.ACTIVE,
                            family = item.family,
                            flavorProfile = item.finishedStandard,
                            usageNotes = item.usageNotes,
                            storageNotes = item.storageNotes,
                            englishName = item.englishName,
                            mainIngredients = item.mainIngredients,
                            preparationSummary = item.preparationSummary,
                            keyParameters = item.keyParameters,
                            adjustableNotes = item.adjustableNotes,
                            seedNote = item.seedNote,
                            seedId = item.seedId,
                            seedVersion = seed.version,
                        )
                    val version =
                        SauceVersion(
                            id = SauceVersionId(id("${item.seedId}:V1")),
                            sauceId = sauceId,
                            versionNo = 1,
                            status = VersionStatus.PUBLISHED,
                            yield = Quantity(1.0, unit("portion")),
                            components = emptyList(),
                            isCurrent = true,
                        )
                    SauceSeed(sauce = sauce, version = version)
                }

            val recipes =
                seed.recipes.map { item ->
                    val recipeId = RecipeId(id(item.seedId))
                    val recipe =
                        Recipe(
                            id = recipeId,
                            name = item.name,
                            normalizedName = NameText.normalize(item.name),
                            category = RecipeCategory.valueOf(item.category),
                            status = EntityStatus.ACTIVE,
                            englishName = item.englishName,
                            allergenNotes = item.allergenNotes,
                            ingredientSummary = item.ingredientText,
                            seedId = item.seedId,
                            seedVersion = seed.version,
                        )
                    val components =
                        item.components.mapIndexed { index, component ->
                            RecipeComponent(
                                lineNo = index + 1,
                                target = ComponentTarget.Ingredient(IngredientId(id(component.ingredientSeedId))),
                                quantity = Quantity(component.quantity, unit(component.unitCode)),
                            )
                        }
                    val version =
                        RecipeVersion(
                            id = RecipeVersionId(id("${item.seedId}:V1")),
                            recipeId = recipeId,
                            versionNo = 1,
                            status = VersionStatus.PUBLISHED,
                            yield = Quantity(item.servings.toDouble(), unit(item.yieldUnitCode)),
                            components = components,
                            servings = item.servings,
                            isCurrent = true,
                            instructions = item.instructions,
                        )
                    RecipeSeed(recipe = recipe, version = version)
                }

            val references =
                seed.references.map { item ->
                    KitchenReference(
                        id = KitchenReferenceId(id(item.seedId)),
                        kind = referenceKind(item.kind),
                        name = item.name,
                        normalizedName = NameText.normalize(item.name),
                        category = item.category,
                        description = item.description,
                        status = EntityStatus.ACTIVE,
                        englishName = item.englishName,
                        seedNote = item.seedNote,
                        seedId = item.seedId,
                        seedVersion = seed.version,
                    )
                }

            val now = clock.now().toEpochMilli()
            val relations =
                seed.relations.map { relation ->
                    val sourceKind = recordKind(relation.sourceSeedId)
                    val targetKind = recordKind(relation.targetSeedId)
                    KitchenRelationEntity(
                        id = id("REL:${relation.sourceSeedId}:${relation.targetSeedId}:${relation.relationLabel}"),
                        sourceRecordId = id(relation.sourceSeedId),
                        sourceKind = sourceKind.name,
                        targetRecordId = id(relation.targetSeedId),
                        targetKind = targetKind.name,
                        targetLabel =
                            names[relation.targetSeedId]
                                ?: throw SeedAbort(
                                    CulinaryError.Validation(
                                        field = "kitchenSeed.relation",
                                        rule = "missing target ${relation.targetSeedId}",
                                    ),
                                ),
                        relationLabel = relation.relationLabel,
                        seedVersion = seed.version,
                        createdAt = now,
                    )
                }

            return SeedData(
                ingredients = ingredients,
                sauces = sauces,
                recipes = recipes,
                references = references,
                relations = relations,
                counts =
                    KitchenSeedCounts(
                        ingredients = ingredients.size,
                        sauces = sauces.size,
                        recipes = recipes.size,
                        references = references.size,
                        relations = relations.size,
                    ),
            )
        }

        private fun referenceKind(value: String): KitchenReferenceKind =
            when (value) {
                "TECHNIQUE" -> KitchenReferenceKind.TECHNIQUE
                "PRODUCTION_STANDARD" -> KitchenReferenceKind.PRODUCTION_STANDARD
                "STORAGE_STANDARD" -> KitchenReferenceKind.STORAGE_STANDARD
                else -> throw SeedAbort(
                    CulinaryError.Validation(field = "kitchenSeed.reference.kind", rule = "unknown $value"),
                )
            }

        private fun recordKind(seedId: String): KitchenRecordKind =
            when {
                seedId.startsWith("SAU-") -> KitchenRecordKind.SAUCE
                seedId.startsWith("ING-") -> KitchenRecordKind.INGREDIENT
                seedId.startsWith("REC-") -> KitchenRecordKind.RECIPE
                seedId.startsWith("TECH-") -> KitchenRecordKind.TECHNIQUE
                seedId.startsWith("STD-1") -> KitchenRecordKind.STORAGE_STANDARD
                seedId.startsWith("STD-0") -> KitchenRecordKind.PRODUCTION_STANDARD
                else -> throw SeedAbort(
                    CulinaryError.Validation(field = "kitchenSeed.kind", rule = "unknown $seedId"),
                )
            }

        private fun saveOrThrow(result: Outcome<*>) {
            when (result) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> throw SeedAbort(result.error)
            }
        }

        private data class SauceSeed(
            val sauce: Sauce,
            val version: SauceVersion,
        )

        private data class RecipeSeed(
            val recipe: Recipe,
            val version: RecipeVersion,
        )

        private data class SeedData(
            val ingredients: List<Ingredient>,
            val sauces: List<SauceSeed>,
            val recipes: List<RecipeSeed>,
            val references: List<KitchenReference>,
            val relations: List<KitchenRelationEntity>,
            val counts: KitchenSeedCounts,
        )

        private class SeedAbort(
            val error: CulinaryError,
        ) : IllegalStateException(error.toString())

        private companion object {
            const val SEED_VERSION = "v1"
            const val VERSION_KEY = "kitchen_seed_version"
            const val DISABLED_KEY = "kitchen_seed_disabled"
            const val ID_NAMESPACE = "MISEBASE-KITCHEN-SEED"
        }
    }
