package com.culinary.feature.sauces

import com.culinary.core.common.CulinaryError
import com.culinary.core.domain.model.ArchiveCategory
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.KitchenRelatedRecord
import com.culinary.core.domain.model.PendingSauceImage
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceDetail
import com.culinary.core.domain.model.SauceDifficulty
import com.culinary.core.domain.model.SauceImage
import com.culinary.core.domain.model.Unit as UnitModel

/** 酱汁列表状态。 */
data class SaucesListUiState(
    val items: List<Sauce> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** 酱汁详情状态。 */
data class SauceDetailUiState(
    val detail: SauceDetail? = null,
    val categoryPath: List<String> = emptyList(),
    val imageBytes: ByteArray? = null,
    val isLoading: Boolean = true,
    val isPublishing: Boolean = false,
    val error: String? = null,
    val relations: List<KitchenRelatedRecord> = emptyList(),
)

/** 编辑器中的组成行；名称覆盖只作用于当前酱汁配方。 */
data class SauceLineEditor(
    val key: String,
    val target: ComponentTarget,
    val name: String,
    val nameEn: String,
    val originalName: String,
    val originalNameEn: String,
    val amount: String,
    val unit: UnitModel,
    val isOptional: Boolean = false,
) {
    val isReadOnly: Boolean get() = target !is ComponentTarget.Ingredient

    val kindLabel: String
        get() =
            when (target) {
                is ComponentTarget.Ingredient -> "食材"
                is ComponentTarget.Recipe -> "子菜谱"
                is ComponentTarget.Sauce -> "子酱汁"
            }

    val labelOverride: String?
        get() = name.trim().takeIf { it.isNotEmpty() && it != originalName }

    val labelOverrideEn: String?
        get() = nameEn.trim().takeIf { it.isNotEmpty() && it != originalNameEn }
}

/** 制作步骤的可编辑状态；图片与步骤序号分离，拖动后按当前序号重写。 */
data class SauceStepEditor(
    val key: String,
    val title: String = "",
    val body: String = "",
    val image: SauceImage? = null,
    val pendingImage: PendingSauceImage? = null,
)

/** 衍生酱汁关系（只存当前 UI 状态；保存时统一写入关系表）。 */
data class SauceRelationEditor(
    val sauceId: String,
    val name: String,
    val nameEn: String,
    val relationLabel: String = "衍生酱汁",
)

/** 酱汁编辑器状态。 */
data class SauceEditorUiState(
    val sauceId: String? = null,
    val name: String = "",
    val family: String = "",
    val englishName: String = "",
    val description: String = "",
    val difficulty: SauceDifficulty? = null,
    val yieldAmount: String = "",
    val yieldUnit: UnitModel? = null,
    val units: List<UnitModel> = emptyList(),
    val ingredients: List<Ingredient> = emptyList(),
    val categories: List<ArchiveCategory> = emptyList(),
    val categoryPathIds: List<String> = emptyList(),
    val isTestRecord: Boolean = false,
    val versionDirty: Boolean = false,
    val sauceOptions: List<Sauce> = emptyList(),
    val lines: List<SauceLineEditor> = emptyList(),
    val steps: List<SauceStepEditor> = emptyList(),
    val application: String = "",
    val relations: List<SauceRelationEditor> = emptyList(),
    val notes: String = "",
    val heroImage: SauceImage? = null,
    val heroImagePending: PendingSauceImage? = null,
    val heroCaption: String = "",
    val noteImages: List<SauceImage> = emptyList(),
    val noteImagesPending: List<PendingSauceImage> = emptyList(),
    val imageBytes: Map<String, ByteArray> = emptyMap(),
    val removedImageIds: Set<String> = emptySet(),
    val updatedAtEpochMillis: Long? = null,
    val editingDraftId: String? = null,
    val editingVersionNo: Int? = null,
    val hasPublishedVersion: Boolean = false,
    val isDirty: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedSauceId: String? = null,
    val archived: Boolean = false,
) {
    val isNew: Boolean get() = sauceId == null
    val isEditingPublished: Boolean get() = hasPublishedVersion
}

/** 把领域错误转成面向用户的中文提示；未知错误保留错误码，不伪装成业务错误。 */
object SauceErrorMessages {
    fun describe(error: CulinaryError): String = withCode(error, friendlyMessage(error))

    private fun friendlyMessage(error: CulinaryError): String =
        when (error) {
            is CulinaryError.Validation -> validationMessage(error)
            is CulinaryError.Conflict -> "该版本已发布，不能原地修改。"
            is CulinaryError.NotFound -> "酱汁或版本不存在，可能已被删除。"
            else -> "操作失败，请重试。"
        }

    private fun withCode(
        error: CulinaryError,
        message: String,
    ): String = "$message（${error.code.value}）"

    private fun validationMessage(error: CulinaryError.Validation): String =
        when {
            error.field == "sauce.name" && error.rule.contains("blank") -> "中文名称不能为空。"
            error.field == "sauce.name" -> "名称过长，请控制在 120 个字符以内。"
            error.field == "sauce.yield" -> "标准产量必须是大于 0 的数字。"
            error.field == "sauce.version" -> "草稿状态已变化，请重新打开编辑。"
            error.field == "sauce.image" -> "图片不符合要求，请重新选择。"
            error.field.startsWith("sauce.component") -> "原料数量必须是大于 0 的数字。"
            else -> "输入不合法。"
        }
}
