package com.culinary.feature.ingredients

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.culinary.core.domain.model.PendingIngredientImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 系统照片选择器：不申请存储权限，图片读入内存后由食材档案存储。 */
@Composable
internal fun rememberIngredientImagePicker(onPicked: (PendingIngredientImage) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    val loaded =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                if (bytes == null || bytes.isEmpty()) null else {
                                    PendingIngredientImage(
                                        bytes = bytes,
                                        mimeType = context.contentResolver.getType(uri) ?: "image/jpeg",
                                    )
                                }
                            }.getOrNull()
                        }
                    loaded?.let(onPicked)
                }
            }
        }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}