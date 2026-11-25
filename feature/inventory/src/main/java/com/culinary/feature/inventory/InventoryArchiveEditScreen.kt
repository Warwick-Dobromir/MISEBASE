package com.culinary.feature.inventory

import android.net.Uri
import android.graphics.BitmapFactory
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.designsystem.component.ArchiveEditorInlineField
import com.culinary.core.designsystem.component.ArchiveEditorSectionHeader
import com.culinary.core.designsystem.component.ArchiveDetailHeading
import com.culinary.core.designsystem.component.ArchiveEditorTopBar
import com.culinary.core.designsystem.component.ArchiveWideImage
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.InlineError
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.ArchiveKind
import com.culinary.core.domain.model.InventoryArchive
import com.culinary.core.domain.model.InventoryArchiveDetail
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.Unit as StockUnit
import com.culinary.core.domain.usecase.inventory.ArchiveFormOptions
import com.culinary.core.domain.usecase.inventory.GetInventoryArchiveDetailUseCase
import com.culinary.core.domain.usecase.inventory.ListArchiveFormOptionsUseCase
import com.culinary.core.domain.usecase.inventory.LinkArchiveProductUseCase
import com.culinary.core.domain.usecase.inventory.SaveInventoryArchiveUseCase
import com.culinary.core.domain.usecase.inventory.UnlinkArchiveProductUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 新建/编辑共用的表单状态；字段错误按字段名保存在 map 里，避免一处错误刷新整页。 */
data class ArchiveEditUiState(
    val archiveId: String? = null,
    val detail: InventoryArchiveDetail? = null,
    val isNew: Boolean = true,
    val kind: ArchiveKind = ArchiveKind.INGREDIENT,
    val options: ArchiveFormOptions = ArchiveFormOptions(),
    val name: String = "",
    val englishName: String = "",
    val categoryId: String? = null,
    val unitId: String = "",
    val minimumStock: String = "",
    val targetStock: String = "",
    val storageMethod: String = "",
    val storageTemperature: String = "",
    val defaultLocationId: String? = null,
    val description: String = "",
    val imageUri: String = "",
    val sauceId: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedId: String? = null,
    val linkProductId: String? = null,
    val linkRatio: String = "1",
    val linkIsDefault: Boolean = false,
    val isLinking: Boolean = false,
    val linkError: String? = null,
    val linkMessage: String? = null,
) {
    val categoryLabel: String?
        get() =
            options.categories
                .firstOrNull { it.id == categoryId }
                ?.pathLabel

    val unitLabel: String?
        get() =
            options.units
                .firstOrNull { it.id.value == unitId }
                ?.let { "${it.name} (${it.symbol})" }

    val locationLabel: String?
        get() =
            options.locations
                .firstOrNull { it.id.value == defaultLocationId }
                ?.name

    val sauceLabel: String?
        get() =
            options.sauces
                .firstOrNull { it.id == sauceId }
                ?.name

    val linkProductLabel: String?
        get() = options.products.firstOrNull { it.id.value == linkProductId }?.let { product ->
            product.sku?.let { "${product.name} · $it" } ?: product.name
        }
}

@HiltViewModel
class ArchiveEditViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val formOptions: ListArchiveFormOptionsUseCase,
        private val saveArchive: SaveInventoryArchiveUseCase,
        private val archiveDetail: GetInventoryArchiveDetailUseCase,
        private val linkArchiveProduct: LinkArchiveProductUseCase,
        private val unlinkArchiveProduct: UnlinkArchiveProductUseCase,
    ) : ViewModel() {
        private val archiveId: String? =
            savedStateHandle.get<String>(InventoryRoutes.ARG_ARCHIVE_ID)?.takeIf { it.isNotBlank() }
        private val kindParam: String = savedStateHandle.get<String>(InventoryRoutes.ARG_KIND).orEmpty()
        private val sauceIdParam: String? =
            savedStateHandle.get<String>(InventoryRoutes.ARG_SAUCE_ID)?.takeIf { it.isNotBlank() }

        private val _uiState = MutableStateFlow(ArchiveEditUiState())
        val uiState: StateFlow<ArchiveEditUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                when (val result = read()) {
                    is Outcome.Success -> _uiState.value = result.value
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                        }
                }
            }
        }

        private suspend fun read(): Outcome<ArchiveEditUiState> {
            val options =
                when (val result = formOptions()) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> result.value
                }
            val existing =
                archiveId?.let { id ->
                    when (val result = archiveDetail(id)) {
                        is Outcome.Failure -> return result
                        is Outcome.Success -> result.value
                    }
                }
            val archive = existing?.archive
            val kind =
                archive?.kind
                    ?: if (kindParam.equals(ArchiveKind.SAUCE.name, ignoreCase = true)) {
                        ArchiveKind.SAUCE
                    } else {
                        ArchiveKind.INGREDIENT
                    }
            return Outcome.success(
                ArchiveEditUiState(
                    archiveId = archive?.id ?: archiveId,
                    detail = existing,
                    isNew = archive == null,
                    kind = kind,
                    options = options,
                    name = archive?.name.orEmpty(),
                    englishName = archive?.englishName.orEmpty(),
                    categoryId = archive?.categoryId,
                    unitId = archive?.stockUnit?.id?.value.orEmpty(),
                    minimumStock = archive?.minimumStock?.let(::formatAmount).orEmpty(),
                    targetStock = archive?.targetStock?.let(::formatAmount).orEmpty(),
                    storageMethod = archive?.storageMethod.orEmpty(),
                    storageTemperature = archive?.storageTemperature.orEmpty(),
                    defaultLocationId = archive?.defaultLocationId?.value,
                    description = archive?.description.orEmpty(),
                    imageUri = archive?.imageUri.orEmpty(),
                    sauceId = archive?.sauceId ?: sauceIdParam,
                    isLoading = false,
                ),
            )
        }

        fun onNameChange(value: String) = update("name") { it.copy(name = value) }

        fun onEnglishNameChange(value: String) = update("englishName") { it.copy(englishName = value) }

        fun onCategoryChange(categoryId: String?) = update("category") { it.copy(categoryId = categoryId) }

        fun onUnitChange(unitId: String) = update("unit") { it.copy(unitId = unitId) }

        fun onMinimumStockChange(value: String) = update("minimum") { it.copy(minimumStock = value) }

        fun onTargetStockChange(value: String) = update("target") { it.copy(targetStock = value) }

        fun onStorageMethodChange(value: String) = update("storageMethod") { it.copy(storageMethod = value) }

        fun onStorageTemperatureChange(value: String) = update("storageTemperature") {
            it.copy(storageTemperature = value)
        }

        fun onLocationChange(locationId: String?) = update("location") { it.copy(defaultLocationId = locationId) }

        fun onDescriptionChange(value: String) = update("description") { it.copy(description = value) }

        fun onImageUriChange(value: String) = update("imageUri") { it.copy(imageUri = value) }

        fun onSauceChange(sauceId: String?) = update("sauce") { it.copy(sauceId = sauceId) }

        fun onLinkProductSelected(productId: String?) {
            _uiState.update { it.copy(linkProductId = productId, linkError = null, linkMessage = null) }
        }

        fun onLinkRatioChange(value: String) {
            _uiState.update { it.copy(linkRatio = value, linkError = null, linkMessage = null) }
        }

        fun onLinkDefaultChange(value: Boolean) {
            _uiState.update { it.copy(linkIsDefault = value, linkError = null, linkMessage = null) }
        }

        fun linkProduct() {
            val state = _uiState.value
            val archiveId = state.archiveId?.takeIf { it.isNotBlank() }
            if (state.isLinking) return
            if (archiveId == null) {
                _uiState.update { it.copy(linkError = "请先保存库存档案，再关联采购商品。") }
                return
            }
            val productId = state.linkProductId
            if (productId.isNullOrBlank()) {
                _uiState.update { it.copy(linkError = "请选择采购商品。") }
                return
            }
            val ratio = state.linkRatio.trim().toDoubleOrNull()
            if (ratio == null || !ratio.isFinite() || ratio <= 0.0) {
                _uiState.update { it.copy(linkError = "换算比必须是大于 0 的数字。") }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isLinking = true, linkError = null, linkMessage = null) }
                when (val result = linkArchiveProduct(archiveId, productId, ratio, state.linkIsDefault)) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isLinking = false, linkError = InventoryErrorMessages.describe(result.error))
                        }
                    is Outcome.Success -> refreshLinks(archiveId, "已关联采购商品。")
                }
            }
        }

        fun unlinkProduct(productId: String) {
            val state = _uiState.value
            val archiveId = state.archiveId?.takeIf { it.isNotBlank() } ?: return
            if (state.isLinking) return
            viewModelScope.launch {
                _uiState.update { it.copy(isLinking = true, linkError = null, linkMessage = null) }
                when (val result = unlinkArchiveProduct(archiveId, productId)) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isLinking = false, linkError = InventoryErrorMessages.describe(result.error))
                        }
                    is Outcome.Success -> refreshLinks(archiveId, "已解除关联。")
                }
            }
        }

        private suspend fun refreshLinks(archiveId: String, message: String) {
            when (val result = archiveDetail(archiveId)) {
                is Outcome.Success ->
                    _uiState.update {
                        it.copy(
                            detail = result.value,
                            isLinking = false,
                            linkProductId = null,
                            linkRatio = "1",
                            linkIsDefault = false,
                            linkMessage = message,
                        )
                    }
                is Outcome.Failure ->
                    _uiState.update {
                        it.copy(isLinking = false, linkError = InventoryErrorMessages.describe(result.error))
                    }
            }
        }

        private fun update(
            field: String,
            transform: (ArchiveEditUiState) -> ArchiveEditUiState,
        ) {
            _uiState.update { state -> transform(state).copy(fieldErrors = state.fieldErrors - field) }
        }

        fun save() {
            val state = _uiState.value
            if (state.isSaving) return
            val errors = validate(state)
            if (errors.isNotEmpty()) {
                _uiState.update { it.copy(fieldErrors = errors, error = null) }
                return
            }
            val unit = state.options.units.firstOrNull { it.id.value == state.unitId }
            if (unit == null) {
                _uiState.update { it.copy(fieldErrors = mapOf("unit" to "请选择库存单位。")) }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, fieldErrors = emptyMap(), error = null) }
                when (val result = saveArchive(state.toArchive(unit))) {
                    is Outcome.Success ->
                        _uiState.update { it.copy(isSaving = false, savedId = result.value) }

                    is Outcome.Failure -> {
                        val fieldError = fieldErrorOf(result.error)
                        if (fieldError == null) {
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    error = InventoryErrorMessages.describe(result.error),
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(isSaving = false, fieldErrors = mapOf(fieldError))
                            }
                        }
                    }
                }
            }
        }

        private fun validate(state: ArchiveEditUiState): Map<String, String> {
            val errors = mutableMapOf<String, String>()
            val name = state.name.trim()
            when {
                name.isEmpty() -> errors["name"] = "请输入中文名称。"
                name.length > 40 -> errors["name"] = "中文名称最多 40 个字。"
            }
            if (state.unitId.isBlank()) errors["unit"] = "请选择库存单位。"
            val minimum = amountError(state.minimumStock, "最低库存", errors, "minimum")
            val target = amountError(state.targetStock, "目标库存", errors, "target")
            if (minimum != null && target != null && target < minimum) {
                errors["target"] = "目标库存不能低于最低库存。"
            }
            if (state.kind == ArchiveKind.SAUCE && state.sauceId.isNullOrBlank()) {
                errors["sauce"] = "酱汁档案必须选择一个酱汁。"
            }
            return errors
        }

        /** 数量字段校验：空值合法（表示未设置），非数字或负数给出字段级文案。 */
        private fun amountError(
            raw: String,
            label: String,
            errors: MutableMap<String, String>,
            field: String,
        ): Double? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            val value = text.toDoubleOrNull()
            when {
                value == null -> errors[field] = "${label}必须是数字。"
                !value.isFinite() || value < 0.0 -> errors[field] = "${label}不能为负数。"
            }
            return value
        }

        private fun fieldErrorOf(error: CulinaryError): Pair<String, String>? =
            when (error) {
                is CulinaryError.Validation ->
                    when (error.field) {
                        "name" ->
                            "name" to
                                if (error.rule.contains("already exists")) {
                                    "已存在同名档案，请换一个名称。"
                                } else if (error.rule.contains("40")) {
                                    "中文名称最多 40 个字。"
                                } else {
                                    "请输入中文名称。"
                                }

                        "minimumStock" -> "minimum" to "最低库存必须是大于等于 0 的数字。"
                        "targetStock" ->
                            "target" to
                                if (error.rule.contains("below minimum")) {
                                    "目标库存不能低于最低库存。"
                                } else {
                                    "目标库存必须是大于等于 0 的数字。"
                                }

                        "sauceId" -> "sauce" to "酱汁档案必须选择一个酱汁。"
                        "categoryId" -> "category" to "所选分类不属于库存分类，请重新选择。"
                        else -> null
                    }

                else -> null
            }
    }

private fun ArchiveEditUiState.toArchive(unit: StockUnit): InventoryArchive =
    InventoryArchive(
        id = archiveId.orEmpty(),
        name = name.trim(),
        englishName = englishName.trim().takeIf { it.isNotEmpty() },
        categoryId = categoryId,
        kind = kind,
        sauceId = sauceId?.takeIf { it.isNotBlank() },
        stockUnit = unit,
        minimumStock = minimumStock.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull(),
        targetStock = targetStock.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull(),
        imageUri = imageUri.trim().takeIf { it.isNotEmpty() },
        storageMethod = storageMethod.trim().takeIf { it.isNotEmpty() },
        storageTemperature = storageTemperature.trim().takeIf { it.isNotEmpty() },
        defaultLocationId = defaultLocationId?.let { LocationId(it) },
        description = description.trim().takeIf { it.isNotEmpty() },
    )

@Composable
fun ArchiveEditRoute(
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ArchiveEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedId) {
        state.savedId?.let(onSaved)
    }
    ArchiveEditScreen(
        state = state,
        onBack = onBack,
        onSave = viewModel::save,
        onNameChange = viewModel::onNameChange,
        onEnglishNameChange = viewModel::onEnglishNameChange,
        onCategoryChange = viewModel::onCategoryChange,
        onUnitChange = viewModel::onUnitChange,
        onMinimumStockChange = viewModel::onMinimumStockChange,
        onTargetStockChange = viewModel::onTargetStockChange,
        onStorageMethodChange = viewModel::onStorageMethodChange,
        onStorageTemperatureChange = viewModel::onStorageTemperatureChange,
        onLocationChange = viewModel::onLocationChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onImageUriChange = viewModel::onImageUriChange,
        onSauceChange = viewModel::onSauceChange,
        onLinkProductSelected = viewModel::onLinkProductSelected,
        onLinkRatioChange = viewModel::onLinkRatioChange,
        onLinkDefaultChange = viewModel::onLinkDefaultChange,
        onLinkProduct = viewModel::linkProduct,
        onUnlinkProduct = viewModel::unlinkProduct,
    )
}

private sealed interface EditPicker {
    data object Category : EditPicker

    data object Unit : EditPicker

    data object Location : EditPicker

    data object Sauce : EditPicker

    data object Product : EditPicker
}

private data class PickerOption(
    val id: String,
    val label: String,
)

@Suppress("LongMethod", "LongParameterList") // 表单字段与回调一一对应，拆开只会增加传递层级。
@Composable
private fun ArchiveEditScreen(
    state: ArchiveEditUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onEnglishNameChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onUnitChange: (String) -> Unit,
    onMinimumStockChange: (String) -> Unit,
    onTargetStockChange: (String) -> Unit,
    onStorageMethodChange: (String) -> Unit,
    onStorageTemperatureChange: (String) -> Unit,
    onLocationChange: (String?) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onImageUriChange: (String) -> Unit,
    onSauceChange: (String?) -> Unit,
    onLinkProductSelected: (String?) -> Unit,
    onLinkRatioChange: (String) -> Unit,
    onLinkDefaultChange: (Boolean) -> Unit,
    onLinkProduct: () -> Unit,
    onUnlinkProduct: (String) -> Unit,
) {
    var picker by remember { mutableStateOf<EditPicker?>(null) }
    val context = LocalContext.current.applicationContext
    val imageUri = state.imageUri.takeIf { it.isNotBlank() }
    val imageBytes by produceState<ByteArray?>(initialValue = null, key1 = imageUri) {
        value = withContext(Dispatchers.IO) {
            imageUri?.let { uri ->
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
                }.getOrNull()
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        ArchiveEditorTopBar(
            title = if (state.isNew) "新建库存档案" else "编辑库存档案",
            onBack = onBack,
            onCancel = onBack,
            onSave = onSave,
            isSaving = state.isSaving,
            canSave = !state.isLoading,
        )
        state.error?.let { message ->
            ErrorBanner(
                message = message,
                modifier =
                    Modifier.padding(
                        start = CulinarySpacing.pageHorizontal,
                        end = CulinarySpacing.pageHorizontal,
                        top = CulinarySpacing.sm,
                    ),
            )
        }
        if (state.isLoading) {
            LoadingRows()
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.sm),
            ) {
                InventoryArchiveHeading(
                    categoryLabel = state.categoryLabel.orEmpty(),
                    chineseTitle = state.name,
                    englishTitle = state.englishName,
                    description = state.description,
                )
                val launchImagePicker = rememberInventoryArchiveImagePicker(onImageUriChange)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.md),
                ) {
                    val bitmap = remember(imageBytes) {
                        imageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "物品图片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(76.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Text("物品图片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(76.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(CulinarySpacing.xxs)) {
                        Text("物品图片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm)) {
                            CulinaryTextButton(text = if (imageUri == null) "添加图片" else "更换图片", onClick = launchImagePicker)
                            if (imageUri != null) CulinaryTextButton(text = "移除图片", onClick = { onImageUriChange("") }, danger = true)
                        }
                    }
                }
                ArchiveEditorSectionHeader(
                    index = "",
                    english = "BASIC INFORMATION",
                    title = "基本信息",
                    showRule = false,
                    topPadding = 0.dp,
                    titleTopPadding = CulinarySpacing.sm,
                )
                InventoryArchiveEditField("中文名称", state.name, onNameChange, "输入中文名称", state.fieldErrors["name"])
                InventoryArchiveEditField("英文名称", state.englishName, onEnglishNameChange, "输入英文名称")
                EditPickerField(
                    label = "所属分类",
                    value = state.categoryLabel,
                    placeholder = "未指定分类",
                    error = state.fieldErrors["category"],
                    onClick = { picker = EditPicker.Category },
                )
                ArchiveEditReadOnlyRow("档案类型", if (state.kind == ArchiveKind.SAUCE) "酱汁现货档案" else "食材库存档案")
                state.detail?.links?.firstOrNull()?.let { link ->
                    ArchiveEditReadOnlyRow("品牌", link.product.brand ?: "未设置")
                    ArchiveEditReadOnlyRow("商品规格", link.product.packageLabel ?: "${formatAmount(link.product.contentQty)} ${link.product.contentUnit.symbol}")
                    ArchiveEditReadOnlyRow("商品编码", link.product.sku ?: "未生成")
                    ArchiveEditReadOnlyRow("条码", "商品条码需在扫码关联后显示")
                }
                if (state.kind == ArchiveKind.SAUCE) {
                    EditPickerField(
                        label = "关联酱汁",
                        value = state.sauceLabel,
                        placeholder = "请选择酱汁",
                        error = state.fieldErrors["sauce"],
                        onClick = { picker = EditPicker.Sauce },
                    )
                }
                ArchiveEditorSectionHeader(index = "", english = "UNIT & MEASUREMENT", title = "规格与计量")
                EditPickerField(
                    label = "库存基本单位",
                    value = state.unitLabel,
                    placeholder = "请选择库存单位",
                    error = state.fieldErrors["unit"],
                    onClick = { picker = EditPicker.Unit },
                )
                state.options.units.firstOrNull { it.id.value == state.unitId }?.let { unit ->
                    ArchiveEditReadOnlyRow("计量维度", unit.dimension.name)
                    if (unit.isApproximate) ArchiveEditReadOnlyRow("换算属性", "近似换算")
                }
                state.detail?.links?.firstOrNull()?.let { link ->
                    ArchiveEditReadOnlyRow("采购单位", link.product.contentUnit.name)
                    ArchiveEditReadOnlyRow("采购规格", "${formatAmount(link.product.contentQty)} ${link.product.contentUnit.symbol}")
                    ArchiveEditReadOnlyRow("单位换算", "1 件 = ${formatAmount(link.conversionRatio)} ${link.product.stockUnit.symbol}")
                    ArchiveEditReadOnlyRow("计量精度", "${link.product.stockUnit.displayPrecision} 位小数")
                }
                ArchiveEditorSectionHeader(index = "", english = "INVENTORY MANAGEMENT", title = "库存管理")
                Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.md)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("最低库存", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ArchiveEditorInlineField(value = state.minimumStock, onValueChange = onMinimumStockChange, placeholder = "未设置", keyboardType = KeyboardType.Decimal)
                        state.fieldErrors["minimum"]?.let { InlineError(message = it) }
                        CulinaryRule()
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("目标库存", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ArchiveEditorInlineField(value = state.targetStock, onValueChange = onTargetStockChange, placeholder = "未设置", keyboardType = KeyboardType.Decimal)
                        state.fieldErrors["target"]?.let { InlineError(message = it) }
                        CulinaryRule()
                    }
                }
                EditPickerField(
                    label = "默认库位",
                    value = state.locationLabel,
                    placeholder = "未指定库位",
                    error = state.fieldErrors["location"],
                    onClick = { picker = EditPicker.Location },
                )
                ArchiveEditorSectionHeader(index = "", english = "SHELF LIFE & STORAGE", title = "保质期与储存")
                InventoryArchiveEditField("储存方式", state.storageMethod, onStorageMethodChange, "例如：密封、避光、冷藏")
                InventoryArchiveEditField("储存温度", state.storageTemperature, onStorageTemperatureChange, "例如：2–8 ℃、常温")
                state.detail?.links?.firstOrNull()?.product?.shelfLifeDays?.let {
                    ArchiveEditReadOnlyRow("保质期规则", "生产日期起算")
                    ArchiveEditReadOnlyRow("保质期时长", "$it 天")
                }
                state.detail?.links?.firstOrNull()?.product?.storageNote?.let {
                    ArchiveEditReadOnlyRow("开封后储存", it)
                }
                ArchiveEditReadOnlyRow("最近到期", state.detail?.row?.nearestExpiry?.let(::formatDate) ?: "未登记")
                ArchiveEditorSectionHeader(index = "", english = "PURCHASING INFORMATION", title = "采购信息")
                val archiveDetail = state.detail
                if (!archiveDetail?.links.isNullOrEmpty()) {
                    archiveDetail!!.links.forEach { link ->
                        ArchiveEditReadOnlyRow("默认供应商", link.product.supplierId ?: "未设置")
                        ArchiveEditReadOnlyRow("采购价格", link.product.lastUnitPriceMinor?.let { "¥${"%.2f".format(it / 100.0)}" } ?: "未记录")
                        ArchiveEditReadOnlyRow("价格单位", "元 / ${link.product.contentUnit.symbol}")
                    }
                }
                if (archiveDetail?.links.isNullOrEmpty()) {
                    ArchiveEditReadOnlyRow("关联采购商品", "尚未关联")
                } else {
                    archiveDetail!!.links.forEach { link ->
                        link.product.sku?.let { ArchiveEditReadOnlyRow("商品编码", it) }
                        link.product.brand?.let { ArchiveEditReadOnlyRow("品牌", it) }
                        link.product.packageLabel?.let { ArchiveEditReadOnlyRow("包装规格", it) }
                        link.product.shelfLifeDays?.let { ArchiveEditReadOnlyRow("商品保质期", "$it 天") }
                        link.product.storageNote?.let { ArchiveEditReadOnlyRow("商品储存备注", it) }
                        ArchiveEditReadOnlyRow(
                            link.product.name,
                            "${formatAmount(link.product.contentQty)} ${link.product.contentUnit.symbol} · 换算 ${formatAmount(link.conversionRatio)} ${archiveDetail.archive.stockUnit.symbol}" +
                                if (link.isDefault) " · 默认商品" else "",
                        )
                        CulinaryTextButton(
                            text = "解除关联",
                            onClick = { onUnlinkProduct(link.product.id.value) },
                            enabled = !state.isLinking,
                            danger = true,
                        )
                    }
                }
                if (state.isNew) {
                    ArchiveEditReadOnlyRow("商品关联", "保存档案后可关联采购商品")
                } else {
                    EditPickerField(
                        label = "选择采购商品",
                        value = state.linkProductLabel,
                        placeholder = "请选择未关联商品",
                        error = state.linkError,
                        onClick = { picker = EditPicker.Product },
                    )
                    ArchiveEditorInlineField(
                        value = state.linkRatio,
                        onValueChange = onLinkRatioChange,
                        placeholder = "换算比，默认 1",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("设为默认采购商品", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "默认商品用于入库时预选",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.linkIsDefault, onCheckedChange = onLinkDefaultChange)
                    }
                    CulinaryTextButton(
                        text = if (state.isLinking) "处理中" else "＋ 关联采购商品",
                        onClick = onLinkProduct,
                        enabled = !state.isLinking && state.linkProductId != null,
                    )
                }
                state.linkMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = CulinarySpacing.xs),
                    )
                }
                ArchiveEditorSectionHeader(index = "", english = "ARCHIVE NOTES", title = "备注")
                ArchiveEditorInlineField(
                    value = state.description,
                    onValueChange = onDescriptionChange,
                    placeholder = "添加档案备注",
                    singleLine = false,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            }
        }
    }

    when (val current = picker) {
        null -> Unit

        is EditPicker.Category ->
            OptionPickerDialog(
                title = "选择分类",
                options = state.options.categories.sortedBy { it.pathLabel }.map { PickerOption(it.id, it.pathLabel) },
                selectedId = state.categoryId,
                allowNone = true,
                onDismiss = { picker = null },
                onSelect = { id ->
                    onCategoryChange(id)
                    picker = null
                },
            )

        is EditPicker.Unit ->
            OptionPickerDialog(
                title = "选择库存单位",
                options = state.options.units.map { PickerOption(it.id.value, "${it.name} (${it.symbol})") },
                selectedId = state.unitId,
                allowNone = false,
                onDismiss = { picker = null },
                onSelect = { id ->
                    id?.let(onUnitChange)
                    picker = null
                },
            )

        is EditPicker.Location ->
            OptionPickerDialog(
                title = "选择默认库位",
                options = state.options.locations.sortedBy { it.name }.map { PickerOption(it.id.value, it.name) },
                selectedId = state.defaultLocationId,
                allowNone = true,
                onDismiss = { picker = null },
                onSelect = { id ->
                    onLocationChange(id)
                    picker = null
                },
            )

        is EditPicker.Product ->
            OptionPickerDialog(
                title = "选择采购商品",
                options = state.options.products
                    .filterNot { product -> state.detail?.links?.any { it.product.id == product.id } == true }
                    .map { product ->
                        PickerOption(
                            product.id.value,
                            product.sku?.let { "${product.name} · $it" } ?: product.name,
                        )
                    },
                selectedId = state.linkProductId,
                allowNone = false,
                onDismiss = { picker = null },
                onSelect = { id ->
                    onLinkProductSelected(id)
                    picker = null
                },
            )

        is EditPicker.Sauce ->
            OptionPickerDialog(
                title = "选择酱汁",
                options = state.options.sauces.sortedBy { it.name }.map { PickerOption(it.id, it.name) },
                selectedId = state.sauceId,
                allowNone = false,
                onDismiss = { picker = null },
                onSelect = { id ->
                    onSauceChange(id)
                    picker = null
                },
            )
    }
}

@Composable
private fun InventoryArchiveHeading(
    categoryLabel: String,
    chineseTitle: String,
    englishTitle: String,
    description: String?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (categoryLabel.isNotBlank()) {
            Text(categoryLabel.uppercase(), style = CulinaryTextStyles.eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        }
        Text(chineseTitle, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        if (englishTitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
            Text(englishTitle.uppercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        description?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(CulinarySpacing.xs))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.md))
    }
}

@Composable
private fun rememberInventoryArchiveImagePicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                onPicked(uri.toString())
            }
        }
    return {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

@Composable
private fun ArchiveEditReadOnlyRow(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        CulinaryRule()
    }
}

@Composable
private fun InventoryArchiveEditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    error: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
        ArchiveEditorInlineField(value = value, onValueChange = onValueChange, placeholder = placeholder, modifier = Modifier.fillMaxWidth())
        error?.let { InlineError(message = it) }
        CulinaryRule()
    }
}

@Composable
private fun EditPickerField(
    label: String,
    value: String?,
    placeholder: String,
    error: String?,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onClick)
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(vertical = CulinarySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value ?: placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (value == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
            }
            Spacer(modifier = Modifier.width(CulinarySpacing.xs))
            Icon(
                imageVector = CulinaryIcons.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        error?.let { InlineError(message = it) }
        CulinaryRule()
    }
}

@Composable
private fun OptionPickerDialog(
    title: String,
    options: List<PickerOption>,
    selectedId: String?,
    allowNone: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraSmall,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                if (allowNone) {
                    PickerRow(
                        label = "不指定",
                        selected = selectedId == null,
                        onClick = { onSelect(null) },
                    )
                }
                if (options.isEmpty()) {
                    Text(
                        text = "暂无可选项。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = CulinarySpacing.sm),
                    )
                }
                options.forEach { option ->
                    PickerRow(
                        label = option.label,
                        selected = option.id == selectedId,
                        onClick = { onSelect(option.id) },
                    )
                }
            }
        },
        confirmButton = {
            CulinaryTextButton(text = "取消", onClick = onDismiss)
        },
    )
}

@Composable
private fun PickerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .defaultMinSize(minHeight = 48.dp)
                .padding(vertical = CulinarySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text(
                text = "已选",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
