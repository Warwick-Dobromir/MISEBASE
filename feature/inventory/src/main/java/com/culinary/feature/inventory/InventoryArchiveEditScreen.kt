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
import androidx.compose.material3.IconButton
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
import com.culinary.core.designsystem.component.ArchiveEditorTopBar
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.InlineError
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles
import com.culinary.core.domain.model.ArchiveKind
import com.culinary.core.domain.model.InventoryArchive
import com.culinary.core.domain.model.InventoryArchiveDetail
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.repository.PendingStockDraft
import com.culinary.core.domain.repository.PendingStockRepository
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
    val capacityUnitPerStockUnit: String = "1",
    val minimumStock: String = "",
    val targetStock: String = "",
    val storageMethod: String = "",
    val storageTemperature: String = "",
    val defaultLocationId: String? = null,
    val description: String = "",
    val imageUri: String = "",
    val sauceId: String? = null,
    val brand: String = "",
    val packageLabel: String = "",
    val supplierId: String = "",
    val shelfLifeDays: String = "",
    val storageNote: String = "",
    val purchasePrice: String = "",
    val productCode: String = "",
    val barcode: String = "",
    val pendingScannedBarcode: String? = null,
    val purchaseUnitId: String = "",
    val purchaseSpecification: String = "",
    val unitConversion: String = "",
    val measurementPrecision: String = "",
    val batchManagementEnabled: Boolean = true,
    val defaultShelfLifeValue: String = "",
    val defaultShelfLifeUnit: String = "MONTH",
    val shelfLifeEnabled: Boolean = false,
    val shelfLifeCalculationMethod: String = "",
    val openedShelfLifeDays: String = "",
    val trackOpenedDate: Boolean = false,
    val supplierName: String = "",
    val purchasePriceUnit: String = "",
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

    val purchaseUnitLabel: String?
        get() = options.units.firstOrNull { it.id.value == purchaseUnitId }?.let { it.name + " (" + it.symbol + ")" }

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
        private val inventoryRepository: InventoryRepository,
        private val pendingStockRepository: PendingStockRepository? = null,
    ) : ViewModel() {
        private val archiveId: String? =
            savedStateHandle.get<String>(InventoryRoutes.ARG_ARCHIVE_ID)?.takeIf { it.isNotBlank() }
        private val kindParam: String = savedStateHandle.get<String>(InventoryRoutes.ARG_KIND).orEmpty()
        private val sauceIdParam: String? =
            savedStateHandle.get<String>(InventoryRoutes.ARG_SAUCE_ID)?.takeIf { it.isNotBlank() }
        private val barcodeParam: String? =
            savedStateHandle.get<String>(InventoryRoutes.ARG_BARCODE)?.takeIf { it.isNotBlank() }

        private val _uiState = MutableStateFlow(ArchiveEditUiState())
        val uiState: StateFlow<ArchiveEditUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                savedStateHandle.getStateFlow<Boolean>("archive_barcode_duplicate", false).collect { duplicate ->
                    if (duplicate) {
                        savedStateHandle["archive_barcode_duplicate"] = false
                        _uiState.update { it.copy(error = "该条码已关联其他库存档案，不能重复使用。") }
                    }
                }
            }
            viewModelScope.launch {
                savedStateHandle.getStateFlow<String?>("archive_barcode_scan_result", null).collect { scanned ->
                    if (!scanned.isNullOrBlank()) {
                        savedStateHandle["archive_barcode_scan_result"] = null
                        if (_uiState.value.barcode.isBlank() || _uiState.value.barcode == scanned) onBarcodeChange(scanned)
                        else _uiState.update { it.copy(pendingScannedBarcode = scanned) }
                    }
                }
            }
            load()
        }

        fun confirmScannedBarcodeReplacement() {
            val scanned = _uiState.value.pendingScannedBarcode ?: return
            onBarcodeChange(scanned)
            _uiState.update { it.copy(pendingScannedBarcode = null) }
        }
        fun dismissScannedBarcodeReplacement() { _uiState.update { it.copy(pendingScannedBarcode = null) } }

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
                    capacityUnitPerStockUnit = archive?.capacityUnitPerStockUnit?.let(::formatAmount) ?: "1",
                    minimumStock = archive?.minimumStock?.let(::formatAmount).orEmpty(),
                    targetStock = archive?.targetStock?.let(::formatAmount).orEmpty(),
                    storageMethod = archive?.storageMethod.orEmpty(),
                    storageTemperature = archive?.storageTemperature.orEmpty(),
                    defaultLocationId = archive?.defaultLocationId?.value,
                    description = archive?.description.orEmpty(),
                    imageUri = archive?.imageUri.orEmpty(),
                    sauceId = archive?.sauceId ?: sauceIdParam,
                    supplierId = existing?.links?.firstOrNull()?.product?.supplierId.orEmpty(),
                    shelfLifeDays = archive?.shelfLifeDays?.toString().orEmpty(),
                    purchasePrice = archive?.purchasePriceMinor?.let { "%.2f".format(it / 100.0) }.orEmpty(),
                    brand = archive?.brand.orEmpty(),
                    packageLabel = archive?.productSpecification.orEmpty(),
                    productCode = archive?.productCode.orEmpty(),
                    barcode = archive?.barcode ?: barcodeParam.orEmpty(),
                    purchaseUnitId = archive?.purchaseUnitId.orEmpty(),
                    purchaseSpecification = archive?.purchaseSpecification.orEmpty(),
                    unitConversion = archive?.unitConversion.orEmpty(),
                    measurementPrecision = archive?.measurementPrecision?.toString().orEmpty(),
                    batchManagementEnabled = archive?.batchManagementEnabled ?: true,
                    defaultShelfLifeValue = archive?.defaultShelfLifeValue?.toString().orEmpty(),
                    defaultShelfLifeUnit = archive?.defaultShelfLifeUnit?.name ?: "MONTH",
                    shelfLifeEnabled = archive?.shelfLifeEnabled ?: false,
                    shelfLifeCalculationMethod = archive?.shelfLifeCalculationMethod.orEmpty(),
                    openedShelfLifeDays = archive?.openedShelfLifeDays?.toString().orEmpty(),
                    trackOpenedDate = archive?.trackOpenedDate ?: false,
                    supplierName = archive?.supplierName.orEmpty(),
                    purchasePriceUnit = archive?.purchasePriceUnit.orEmpty(),
                    isLoading = false,
                ),
            )
        }

        fun onNameChange(value: String) = update("name") { it.copy(name = value) }

        fun onEnglishNameChange(value: String) = update("englishName") { it.copy(englishName = value) }

        fun onCategoryChange(categoryId: String?) = update("category") { it.copy(categoryId = categoryId) }

        fun onUnitChange(unitId: String) = update("unit") { it.copy(unitId = unitId) }

        fun onCapacityUnitPerStockUnitChange(value: String) =
            update("capacityUnitPerStockUnit") { it.copy(capacityUnitPerStockUnit = value) }

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

        fun onBrandChange(value: String) = update("brand") { it.copy(brand = value) }

        fun onPackageLabelChange(value: String) = update("packageLabel") { it.copy(packageLabel = value) }


        fun onShelfLifeChange(value: String) = update("shelfLife") { it.copy(shelfLifeDays = value) }

        fun onStorageNoteChange(value: String) = update("storageNote") { it.copy(storageNote = value) }

        fun onPurchasePriceChange(value: String) = update("price") { it.copy(purchasePrice = value) }
        fun onProductCodeChange(value: String) = update("productCode") { it.copy(productCode = value) }
        fun onBarcodeChange(value: String) = update("barcode") { it.copy(barcode = value) }
        fun onPurchaseUnitIdChange(value: String) = update("purchaseUnit") { it.copy(purchaseUnitId = value) }
        fun onPurchaseSpecificationChange(value: String) = update("purchaseSpecification") { it.copy(purchaseSpecification = value) }
        fun onUnitConversionChange(value: String) = update("unitConversion") { it.copy(unitConversion = value) }
        fun onMeasurementPrecisionChange(value: String) = update("measurementPrecision") { it.copy(measurementPrecision = value) }
        fun onBatchManagementChange(value: Boolean) { _uiState.update { it.copy(batchManagementEnabled = value) } }
        fun onDefaultShelfLifeValueChange(value: String) = update("defaultShelfLife") { it.copy(defaultShelfLifeValue = value) }
        fun onDefaultShelfLifeUnitChange(value: String) = update("defaultShelfLifeUnit") { it.copy(defaultShelfLifeUnit = value) }
        fun onShelfLifeEnabledChange(value: Boolean) { _uiState.update { it.copy(shelfLifeEnabled = value) } }
        fun onShelfLifeCalculationMethodChange(value: String) = update("shelfLifeMethod") { it.copy(shelfLifeCalculationMethod = value) }
        fun onOpenedShelfLifeChange(value: String) = update("openedShelfLife") { it.copy(openedShelfLifeDays = value) }
        fun onTrackOpenedDateChange(value: Boolean) { _uiState.update { it.copy(trackOpenedDate = value) } }
        fun onSupplierNameChange(value: String) = update("supplierName") { it.copy(supplierName = value) }
        fun onPurchasePriceUnitChange(value: String) = update("purchasePriceUnit") { it.copy(purchasePriceUnit = value) }

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
                    is Outcome.Success -> {
                        val product = state.detail?.links?.firstOrNull()?.product
                        if (product == null) {
                            val queued = enqueueScannedArchive(state, result.value, unit)
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    archiveId = if (queued) it.archiveId else result.value,
                                    savedId = result.value.takeIf { queued },
                                    error = if (queued) null else "库存档案已保存，但加入待入库清单失败，请重试。",
                                )
                            }
                        } else {
                            val updatedProduct = product.copy(
                                brand = state.brand.trim().takeIf { it.isNotEmpty() },
                                packageLabel = state.packageLabel.trim().takeIf { it.isNotEmpty() },
                                supplierId = state.supplierId.trim().takeIf { it.isNotEmpty() },
                                shelfLifeDays = state.shelfLifeDays.trim().toIntOrNull(),
                                storageNote = state.storageNote.trim().takeIf { it.isNotEmpty() },
                                lastUnitPriceMinor = state.purchasePrice.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.let { (it * 100.0).toLong() },
                            )
                            when (val productResult = inventoryRepository.saveProduct(updatedProduct)) {
                                is Outcome.Success -> {
                                    val queued = enqueueScannedArchive(state, result.value, unit)
                                    _uiState.update {
                                        it.copy(
                                            isSaving = false,
                                            archiveId = if (queued) it.archiveId else result.value,
                                            savedId = result.value.takeIf { queued },
                                            error = if (queued) null else "库存档案已保存，但加入待入库清单失败，请重试。",
                                        )
                                    }
                                }
                                is Outcome.Failure -> _uiState.update {
                                    it.copy(isSaving = false, error = InventoryErrorMessages.describe(productResult.error))
                                }
                            }
                        }
                    }

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

        private suspend fun enqueueScannedArchive(
            state: ArchiveEditUiState,
            archiveId: String,
            unit: StockUnit,
        ): Boolean {
            if (barcodeParam.isNullOrBlank()) return true
            val repository = pendingStockRepository ?: return false
            return runCatching {
                repository.addOrIncrement(
                    PendingStockDraft(
                        archiveId = archiveId,
                        name = state.name.trim(),
                        brand = state.brand.trim().takeIf { it.isNotEmpty() },
                        categoryName = state.categoryLabel,
                        specification = state.packageLabel.trim().takeIf { it.isNotEmpty() },
                        imageUri = state.imageUri.trim().takeIf { it.isNotEmpty() },
                        stockUnitId = unit.id.value,
                    ),
                )
            }.isSuccess
        }

        private fun validate(state: ArchiveEditUiState): Map<String, String> {
            val errors = mutableMapOf<String, String>()
            val name = state.name.trim()
            when {
                name.isEmpty() -> errors["name"] = "请输入中文名称。"
                name.length > 40 -> errors["name"] = "中文名称最多 40 个字。"
            }
            if (state.unitId.isBlank()) errors["unit"] = "请选择库存单位。"
            val cu = state.capacityUnitPerStockUnit.trim().toDoubleOrNull()
            if (cu == null || !cu.isFinite() || cu <= 0.0) errors["capacityUnitPerStockUnit"] = "CU 系数必须是大于 0 的数字。"
            val defaultShelfLife = state.defaultShelfLifeValue.trim()
            if (defaultShelfLife.isNotEmpty() && (defaultShelfLife.toIntOrNull() == null || defaultShelfLife.toInt() <= 0)) {
                errors["defaultShelfLife"] = "默认保质期必须是大于 0 的整数。"
            }
            val shelfLife = state.shelfLifeDays.trim()
            if (shelfLife.isNotEmpty() && (shelfLife.toIntOrNull() == null || shelfLife.toInt() < 0)) {
                errors["shelfLife"] = "保质期必须是大于等于 0 的整数。"
            }
            val price = state.purchasePrice.trim()
            if (price.isNotEmpty() && (price.toDoubleOrNull() == null || !price.toDouble().isFinite() || price.toDouble() < 0.0)) {
                errors["price"] = "采购价格必须是大于等于 0 的数字。"
            }
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
        capacityUnitPerStockUnit = capacityUnitPerStockUnit.trim().toDoubleOrNull() ?: 1.0,
        minimumStock = minimumStock.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull(),
        targetStock = targetStock.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull(),
        imageUri = imageUri.trim().takeIf { it.isNotEmpty() },
        storageMethod = storageMethod.trim().takeIf { it.isNotEmpty() },
        storageTemperature = storageTemperature.trim().takeIf { it.isNotEmpty() },
        defaultLocationId = defaultLocationId?.let { LocationId(it) },
        description = description.trim().takeIf { it.isNotEmpty() },
        brand = brand.trim().takeIf { it.isNotEmpty() },
        productSpecification = packageLabel.trim().takeIf { it.isNotEmpty() },
        productCode = productCode.trim().takeIf { it.isNotEmpty() },
        barcode = barcode.trim().takeIf { it.isNotEmpty() },
        purchaseUnitId = purchaseUnitId.trim().takeIf { it.isNotEmpty() },
        purchaseSpecification = purchaseSpecification.trim().takeIf { it.isNotEmpty() },
        unitConversion = unitConversion.trim().takeIf { it.isNotEmpty() },
        measurementPrecision = measurementPrecision.trim().toIntOrNull(),
        batchManagementEnabled = batchManagementEnabled,
        defaultShelfLifeValue = defaultShelfLifeValue.trim().toIntOrNull(),
        defaultShelfLifeUnit = if (defaultShelfLifeValue.isBlank()) null else com.culinary.core.domain.model.DefaultShelfLifeUnit.entries.firstOrNull { it.name == defaultShelfLifeUnit },
        shelfLifeEnabled = shelfLifeEnabled,
        shelfLifeCalculationMethod = shelfLifeCalculationMethod.trim().takeIf { it.isNotEmpty() },
        shelfLifeDays = shelfLifeDays.trim().toIntOrNull(),
        openedShelfLifeDays = openedShelfLifeDays.trim().toIntOrNull(),
        trackOpenedDate = trackOpenedDate,
        supplierName = supplierName.trim().takeIf { it.isNotEmpty() },
        purchasePriceMinor = purchasePrice.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.let { (it * 100.0).toLong() },
        purchasePriceUnit = purchasePriceUnit.trim().takeIf { it.isNotEmpty() },
    )

@Composable
fun ArchiveEditRoute(
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
    onScanBarcode: () -> Unit,
    viewModel: ArchiveEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedId) {
        state.savedId?.let(onSaved)
    }
    ArchiveEditScreen(
        state = state,
        onBack = onBack,
        onScanBarcode = onScanBarcode,
        onSave = viewModel::save,
        onNameChange = viewModel::onNameChange,
        onEnglishNameChange = viewModel::onEnglishNameChange,
        onCategoryChange = viewModel::onCategoryChange,
        onUnitChange = viewModel::onUnitChange,
        onCapacityUnitPerStockUnitChange = viewModel::onCapacityUnitPerStockUnitChange,
        onMinimumStockChange = viewModel::onMinimumStockChange,
        onTargetStockChange = viewModel::onTargetStockChange,
        onStorageMethodChange = viewModel::onStorageMethodChange,
        onStorageTemperatureChange = viewModel::onStorageTemperatureChange,
        onLocationChange = viewModel::onLocationChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onImageUriChange = viewModel::onImageUriChange,
        onSauceChange = viewModel::onSauceChange,
        onBrandChange = viewModel::onBrandChange,
        onPackageLabelChange = viewModel::onPackageLabelChange,
        onShelfLifeChange = viewModel::onShelfLifeChange,
        onDefaultShelfLifeValueChange = viewModel::onDefaultShelfLifeValueChange,
        onDefaultShelfLifeUnitChange = viewModel::onDefaultShelfLifeUnitChange,
        onStorageNoteChange = viewModel::onStorageNoteChange,
        onPurchasePriceChange = viewModel::onPurchasePriceChange,
        onProductCodeChange = viewModel::onProductCodeChange,
        onBarcodeChange = viewModel::onBarcodeChange,
        onConfirmBarcodeReplacement = viewModel::confirmScannedBarcodeReplacement,
        onDismissBarcodeReplacement = viewModel::dismissScannedBarcodeReplacement,
        onPurchaseUnitIdChange = viewModel::onPurchaseUnitIdChange,
        onPurchaseSpecificationChange = viewModel::onPurchaseSpecificationChange,
        onUnitConversionChange = viewModel::onUnitConversionChange,
        onMeasurementPrecisionChange = viewModel::onMeasurementPrecisionChange,
        onBatchManagementChange = viewModel::onBatchManagementChange,
        onShelfLifeEnabledChange = viewModel::onShelfLifeEnabledChange,
        onShelfLifeCalculationMethodChange = viewModel::onShelfLifeCalculationMethodChange,
        onOpenedShelfLifeChange = viewModel::onOpenedShelfLifeChange,
        onTrackOpenedDateChange = viewModel::onTrackOpenedDateChange,
        onSupplierNameChange = viewModel::onSupplierNameChange,
        onPurchasePriceUnitChange = viewModel::onPurchasePriceUnitChange,
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

    data object PurchaseUnit : EditPicker

    data object ShelfLifeCalculation : EditPicker

    data object DefaultShelfLifeUnit : EditPicker
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
    onScanBarcode: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onEnglishNameChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onUnitChange: (String) -> Unit,
    onCapacityUnitPerStockUnitChange: (String) -> Unit,
    onMinimumStockChange: (String) -> Unit,
    onTargetStockChange: (String) -> Unit,
    onStorageMethodChange: (String) -> Unit,
    onStorageTemperatureChange: (String) -> Unit,
    onLocationChange: (String?) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onImageUriChange: (String) -> Unit,
    onSauceChange: (String?) -> Unit,
    onBrandChange: (String) -> Unit,
    onPackageLabelChange: (String) -> Unit,
    onShelfLifeChange: (String) -> Unit,
    onDefaultShelfLifeValueChange: (String) -> Unit,
    onDefaultShelfLifeUnitChange: (String) -> Unit,
    onStorageNoteChange: (String) -> Unit,
    onPurchasePriceChange: (String) -> Unit,
    onProductCodeChange: (String) -> Unit,
    onBarcodeChange: (String) -> Unit,
    onConfirmBarcodeReplacement: () -> Unit,
    onDismissBarcodeReplacement: () -> Unit,
    onPurchaseUnitIdChange: (String) -> Unit,
    onPurchaseSpecificationChange: (String) -> Unit,
    onUnitConversionChange: (String) -> Unit,
    onMeasurementPrecisionChange: (String) -> Unit,
    onBatchManagementChange: (Boolean) -> Unit,
    onShelfLifeEnabledChange: (Boolean) -> Unit,
    onShelfLifeCalculationMethodChange: (String) -> Unit,
    onOpenedShelfLifeChange: (String) -> Unit,
    onTrackOpenedDateChange: (Boolean) -> Unit,
    onSupplierNameChange: (String) -> Unit,
    onPurchasePriceUnitChange: (String) -> Unit,
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
                    description = null,
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
                            modifier = Modifier.size(112.dp).clip(RoundedCornerShape(6.dp)),
                        )
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
                InventoryArchiveEditField("品牌", state.brand, onBrandChange, "输入品牌")
                EditPickerField(
                    label = "所属分类",
                    value = state.categoryLabel,
                    placeholder = "未指定分类",
                    error = state.fieldErrors["category"],
                    onClick = { picker = EditPicker.Category },
                )
                InventoryArchiveEditField("商品规格", state.packageLabel, onPackageLabelChange, "例如：1 kg / 块")
                InventoryArchiveEditField("容量系数（CU / 库存单位）", state.capacityUnitPerStockUnit, onCapacityUnitPerStockUnitChange, "例如：1", state.fieldErrors["capacityUnitPerStockUnit"])
                ArchiveEditReadOnlyRow("商品编码", state.productCode.ifBlank { "保存档案后自动生成" })
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text("条码", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(104.dp))
                    ArchiveEditorInlineField(value = state.barcode, onValueChange = onBarcodeChange, placeholder = "填写商品条码", modifier = Modifier.weight(1f))
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
                    label = "库存单位",
                    value = state.unitLabel,
                    placeholder = "请选择库存单位",
                    error = state.fieldErrors["unit"],
                    onClick = { picker = EditPicker.Unit },
                )
                state.options.units.firstOrNull { it.id.value == state.unitId }?.let { unit ->
                    ArchiveEditReadOnlyRow("计量维度", unit.dimension.name)
                    if (unit.isApproximate) ArchiveEditReadOnlyRow("换算属性", "近似换算")
                }
                EditPickerField(
                    label = "采购单位",
                    value = state.purchaseUnitLabel,
                    placeholder = "请选择采购单位",
                    error = state.fieldErrors["purchaseUnit"],
                    onClick = { picker = EditPicker.PurchaseUnit },
                )
                InventoryArchiveEditField("采购规格", state.purchaseSpecification, onPurchaseSpecificationChange, "例如：1 kg / 块")
                InventoryArchiveEditField("单位换算", state.unitConversion, onUnitConversionChange, "例如：1 块 = 1 kg")
                InventoryArchiveEditField("计量精度", state.measurementPrecision, onMeasurementPrecisionChange, "小数位数")
                ArchiveEditorSectionHeader(index = "", english = "INVENTORY MANAGEMENT", title = "库存管理")
                Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.md)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("最低库存", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ArchiveEditorInlineField(value = state.minimumStock, onValueChange = onMinimumStockChange, placeholder = "未设置", keyboardType = KeyboardType.Decimal)
                        state.fieldErrors["minimum"]?.let { InlineError(message = it) }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("目标库存", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ArchiveEditorInlineField(value = state.targetStock, onValueChange = onTargetStockChange, placeholder = "未设置", keyboardType = KeyboardType.Decimal)
                        state.fieldErrors["target"]?.let { InlineError(message = it) }
                    }
                }
                EditPickerField(
                    label = "默认存放位置",
                    value = state.locationLabel,
                    placeholder = "未指定库位",
                    error = state.fieldErrors["location"],
                    onClick = { picker = EditPicker.Location },
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("批次管理", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = state.batchManagementEnabled, onCheckedChange = onBatchManagementChange)
                }
                ArchiveEditorSectionHeader(index = "", english = "SHELF LIFE & STORAGE", title = "保质期与储存")
                InventoryArchiveEditField("默认保质期", state.defaultShelfLifeValue, onDefaultShelfLifeValueChange, "输入期限数值", state.fieldErrors["defaultShelfLife"])
                EditPickerField(
                    label = "期限单位",
                    value = when (state.defaultShelfLifeUnit) { "DAY" -> "天"; "YEAR" -> "年"; else -> "个月" },
                    placeholder = "选择单位", error = null,
                    onClick = { picker = EditPicker.DefaultShelfLifeUnit },
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("保质期管理", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = state.shelfLifeEnabled, onCheckedChange = onShelfLifeEnabledChange)
                }
                EditPickerField(
                    label = "计算方式",
                    value = state.shelfLifeCalculationMethod.takeIf { it.isNotBlank() },
                    placeholder = "请选择计算方式",
                    error = state.fieldErrors["shelfLifeMethod"],
                    onClick = { picker = EditPicker.ShelfLifeCalculation },
                )
                InventoryArchiveEditField("保质期时长", state.shelfLifeDays, onShelfLifeChange, "输入天数", state.fieldErrors["shelfLife"])
                InventoryArchiveEditField("开封后保质期", state.openedShelfLifeDays, onOpenedShelfLifeChange, "输入天数")
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("记录开封日期", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = state.trackOpenedDate, onCheckedChange = onTrackOpenedDateChange)
                }
                InventoryArchiveEditField("储存方式", state.storageMethod, onStorageMethodChange, "例如：冷藏")
                InventoryArchiveEditField("储存温度", state.storageTemperature, onStorageTemperatureChange, "例如：0–4°C")
                ArchiveEditorSectionHeader(index = "", english = "PURCHASING INFORMATION", title = "采购信息")
                InventoryArchiveEditField("默认供应商", state.supplierName, onSupplierNameChange, "输入供应商名称")
                InventoryArchiveEditField("采购价格", state.purchasePrice, onPurchasePriceChange, "输入单价", state.fieldErrors["price"])
                InventoryArchiveEditField("价格单位", state.purchasePriceUnit, onPurchasePriceUnitChange, "例如：元 / 块")
                val archiveDetail = state.detail
                ArchiveEditorSectionHeader(index = "", english = "LINKED PRODUCTS", title = "关联采购商品")
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

        is EditPicker.PurchaseUnit ->
            OptionPickerDialog(
                title = "选择采购单位",
                options = state.options.units.map { PickerOption(it.id.value, it.name + " (" + it.symbol + ")") },
                selectedId = state.purchaseUnitId,
                allowNone = true,
                onDismiss = { picker = null },
                onSelect = { id ->
                    onPurchaseUnitIdChange(id.orEmpty())
                    picker = null
                },
            )

        is EditPicker.DefaultShelfLifeUnit ->
            OptionPickerDialog(
                title = "选择默认保质期单位",
                options = listOf(PickerOption("DAY", "天"), PickerOption("MONTH", "个月"), PickerOption("YEAR", "年")),
                selectedId = state.defaultShelfLifeUnit, allowNone = false,
                onDismiss = { picker = null },
                onSelect = { id -> id?.let(onDefaultShelfLifeUnitChange); picker = null },
            )

        is EditPicker.ShelfLifeCalculation ->
            OptionPickerDialog(
                title = "选择保质期计算方式",
                options = listOf(
                    PickerOption("PRODUCTION_DATE", "生产日期起算"),
                    PickerOption("RECEIPT_DATE", "入库日期起算"),
                    PickerOption("OPENED_DATE", "开封日期起算"),
                ),
                selectedId = state.shelfLifeCalculationMethod,
                allowNone = false,
                onDismiss = { picker = null },
                onSelect = { id ->
                    id?.let(onShelfLifeCalculationMethodChange)
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
    englishTitle: String?,
    description: String?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (categoryLabel.isNotBlank()) {
            Text(categoryLabel.uppercase(), style = CulinaryTextStyles.eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        }
        Text(chineseTitle, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        if (!englishTitle.isNullOrBlank()) {
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(104.dp),
            )
            ArchiveEditorInlineField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                modifier = Modifier.weight(1f),
            )
        }
        error?.let {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(104.dp))
                InlineError(message = it)
            }
        }
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(104.dp),
            )
            Text(
                text = value ?: placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(CulinarySpacing.xs))
            Icon(
                imageVector = CulinaryIcons.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        error?.let {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(104.dp))
                InlineError(message = it)
            }
        }
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
