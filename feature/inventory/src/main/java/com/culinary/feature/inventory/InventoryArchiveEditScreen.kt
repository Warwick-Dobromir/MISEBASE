package com.culinary.feature.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.designsystem.component.ArchiveEditorSectionHeader
import com.culinary.core.designsystem.component.ArchiveEditorTopBar
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.InlineError
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.ArchiveKind
import com.culinary.core.domain.model.InventoryArchive
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.Unit as StockUnit
import com.culinary.core.domain.usecase.inventory.ArchiveFormOptions
import com.culinary.core.domain.usecase.inventory.GetInventoryArchiveDetailUseCase
import com.culinary.core.domain.usecase.inventory.ListArchiveFormOptionsUseCase
import com.culinary.core.domain.usecase.inventory.SaveInventoryArchiveUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 新建/编辑共用的表单状态；字段错误按字段名保存在 map 里，避免一处错误刷新整页。 */
data class ArchiveEditUiState(
    val archiveId: String? = null,
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
}

@HiltViewModel
class ArchiveEditViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val formOptions: ListArchiveFormOptionsUseCase,
        private val saveArchive: SaveInventoryArchiveUseCase,
        private val archiveDetail: GetInventoryArchiveDetailUseCase,
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
    )
}

private sealed interface EditPicker {
    data object Category : EditPicker

    data object Unit : EditPicker

    data object Location : EditPicker

    data object Sauce : EditPicker
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
) {
    var picker by remember { mutableStateOf<EditPicker?>(null) }
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
                ArchiveEditorSectionHeader(index = "", english = "IDENTITY", title = "基本信息")
                Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                CulinaryTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = "中文名称 *",
                    singleLine = true,
                    isError = state.fieldErrors["name"] != null,
                    supportingText = state.fieldErrors["name"] ?: "必填，最多 40 个字",
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.field))
                CulinaryTextField(
                    value = state.englishName,
                    onValueChange = onEnglishNameChange,
                    label = "英文名称",
                    singleLine = true,
                    supportingText = "可留空，用于列表副标题",
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.field))
                EditPickerField(
                    label = "分类",
                    value = state.categoryLabel,
                    placeholder = "未指定分类",
                    error = state.fieldErrors["category"],
                    onClick = { picker = EditPicker.Category },
                )
                if (state.kind == ArchiveKind.SAUCE) {
                    EditPickerField(
                        label = "酱汁 *",
                        value = state.sauceLabel,
                        placeholder = "请选择酱汁",
                        error = state.fieldErrors["sauce"],
                        onClick = { picker = EditPicker.Sauce },
                    )
                }
                ArchiveEditorSectionHeader(index = "", english = "MEASURE", title = "计量与阈值")
                Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                EditPickerField(
                    label = "库存单位 *",
                    value = state.unitLabel,
                    placeholder = "请选择库存单位",
                    error = state.fieldErrors["unit"],
                    onClick = { picker = EditPicker.Unit },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.md)) {
                    CulinaryTextField(
                        value = state.minimumStock,
                        onValueChange = onMinimumStockChange,
                        label = "最低库存",
                        singleLine = true,
                        isError = state.fieldErrors["minimum"] != null,
                        supportingText = state.fieldErrors["minimum"] ?: "低于该值标记为低库存",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    CulinaryTextField(
                        value = state.targetStock,
                        onValueChange = onTargetStockChange,
                        label = "目标库存",
                        singleLine = true,
                        isError = state.fieldErrors["target"] != null,
                        supportingText = state.fieldErrors["target"] ?: "补货参考值",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                ArchiveEditorSectionHeader(index = "", english = "STORAGE", title = "存储信息")
                Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                EditPickerField(
                    label = "默认库位",
                    value = state.locationLabel,
                    placeholder = "未指定库位",
                    error = state.fieldErrors["location"],
                    onClick = { picker = EditPicker.Location },
                )
                CulinaryTextField(
                    value = state.storageMethod,
                    onValueChange = onStorageMethodChange,
                    label = "存储方式",
                    singleLine = true,
                    supportingText = "例如 密封、避光、冷藏",
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.field))
                CulinaryTextField(
                    value = state.storageTemperature,
                    onValueChange = onStorageTemperatureChange,
                    label = "存储温度",
                    singleLine = true,
                    supportingText = "例如 2-8 ℃、常温",
                )
                ArchiveEditorSectionHeader(index = "", english = "NOTES", title = "补充信息")
                Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                CulinaryTextField(
                    value = state.imageUri,
                    onValueChange = onImageUriChange,
                    label = "图片 URI",
                    singleLine = true,
                    supportingText = "可留空；填写后详情页会显示该地址",
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.field))
                CulinaryTextField(
                    value = state.description,
                    onValueChange = onDescriptionChange,
                    label = "备注",
                    minLines = 4,
                    supportingText = "可留空",
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.xl))
                CulinaryButton(
                    text = if (state.isNew) "保存并创建" else "保存修改",
                    onClick = onSave,
                    loading = state.isSaving,
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
