package com.culinary.feature.scan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.culinary.core.designsystem.theme.CulinaryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 扫码页 Compose UI：权限说明、相机动作、手电筒提示、手工输入兜底与返回。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScanUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `permission pane explains why the camera is needed`() {
        compose.setContent {
            CulinaryTheme {
                ScanScreen(
                    state = ScanUiState(cameraAccess = CameraAccessState.NOT_REQUESTED),
                    onRequestPermission = {},
                    onOpenManualEntry = {},
                    onCloseManualEntry = {},
                    onManualCodeChange = {},
                    onSubmitManualCode = {},
                    onToggleTorch = {},
                    onResumeScanning = {},
                    onBack = {},
                    onDismissTorchNotice = {},
                    cameraContent = {},
                )
            }
        }

        compose.onNodeWithText("需要相机权限").assertIsDisplayed()
        compose.onNodeWithText("允许使用相机").assertIsDisplayed()
        compose.onNodeWithText("手工输入条码").assertIsDisplayed()
        compose.onNodeWithText("返回").assertIsDisplayed()
    }

    @Test
    fun `denied pane offers retry and manual fallback`() {
        compose.setContent {
            CulinaryTheme {
                ScanScreen(
                    state = ScanUiState(cameraAccess = CameraAccessState.DENIED),
                    onRequestPermission = {},
                    onOpenManualEntry = {},
                    onCloseManualEntry = {},
                    onManualCodeChange = {},
                    onSubmitManualCode = {},
                    onToggleTorch = {},
                    onResumeScanning = {},
                    onBack = {},
                    onDismissTorchNotice = {},
                    cameraContent = {},
                )
            }
        }

        compose.onNodeWithText("相机权限被拒绝").assertIsDisplayed()
        compose.onNodeWithText("重新请求权限").assertIsDisplayed()
        compose.onNodeWithText("手工输入条码").assertIsDisplayed()
    }

    @Test
    fun `camera pane shows scan frame hint torch and manual entry`() {
        compose.setContent {
            CulinaryTheme {
                ScanScreen(
                    state =
                        ScanUiState(
                            cameraAccess = CameraAccessState.GRANTED,
                            scanning = true,
                        ),
                    onRequestPermission = {},
                    onOpenManualEntry = {},
                    onCloseManualEntry = {},
                    onManualCodeChange = {},
                    onSubmitManualCode = {},
                    onToggleTorch = {},
                    onResumeScanning = {},
                    onBack = {},
                    onDismissTorchNotice = {},
                    cameraContent = {},
                )
            }
        }

        compose.onNodeWithText("将条码放入框内，保持稳定").assertIsDisplayed()
        compose.onNodeWithText("打开手电筒").assertIsDisplayed()
        compose.onNodeWithText("手工输入条码").assertIsDisplayed()
        compose.onNodeWithText("返回").assertIsDisplayed()
    }

    @Test
    fun `unavailable camera sends the user to manual entry`() {
        compose.setContent {
            CulinaryTheme {
                ScanScreen(
                    state = ScanUiState(cameraAccess = CameraAccessState.UNAVAILABLE, hasCamera = false),
                    onRequestPermission = {},
                    onOpenManualEntry = {},
                    onCloseManualEntry = {},
                    onManualCodeChange = {},
                    onSubmitManualCode = {},
                    onToggleTorch = {},
                    onResumeScanning = {},
                    onBack = {},
                    onDismissTorchNotice = {},
                    cameraContent = {},
                )
            }
        }

        compose.onNodeWithText("没有可用的相机").assertIsDisplayed()
        compose.onNodeWithText("手工输入条码").assertIsDisplayed()
    }

    @Test
    fun `manual entry pane accepts typed barcode and shows validation message`() {
        compose.setContent {
            CulinaryTheme {
                ScanScreen(
                    state =
                        ScanUiState(
                            cameraAccess = CameraAccessState.DENIED,
                            manualEntry = true,
                            manualCode = "6901234567892",
                            manualError = "条码内容不能为空。",
                        ),
                    onRequestPermission = {},
                    onOpenManualEntry = {},
                    onCloseManualEntry = {},
                    onManualCodeChange = {},
                    onSubmitManualCode = {},
                    onToggleTorch = {},
                    onResumeScanning = {},
                    onBack = {},
                    onDismissTorchNotice = {},
                    cameraContent = {},
                )
            }
        }

        compose.onNodeWithText("手工输入条码").assertIsDisplayed()
        compose.onNodeWithText("使用该条码").assertIsDisplayed()
        compose.onNodeWithText("条码内容不能为空。").assertIsDisplayed()
    }

    @Test
    fun `manual entry field is editable`() {
        compose.setContent {
            CulinaryTheme {
                var code by remember { mutableStateOf("") }
                ScanScreen(
                    state =
                        ScanUiState(
                            cameraAccess = CameraAccessState.DENIED,
                            manualEntry = true,
                            manualCode = code,
                        ),
                    onRequestPermission = {},
                    onOpenManualEntry = {},
                    onCloseManualEntry = {},
                    onManualCodeChange = { code = it },
                    onSubmitManualCode = {},
                    onToggleTorch = {},
                    onResumeScanning = {},
                    onBack = {},
                    onDismissTorchNotice = {},
                    cameraContent = {},
                )
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("6901234567892")

        compose.onNodeWithText("6901234567892", substring = true).assertExists()
    }

    @Test
    fun `torch notice is shown when the device has no flash`() {
        compose.setContent {
            CulinaryTheme {
                ScanScreen(
                    state =
                        ScanUiState(
                            cameraAccess = CameraAccessState.GRANTED,
                            scanning = true,
                            torchNotice = "此设备不支持手电筒。",
                        ),
                    onRequestPermission = {},
                    onOpenManualEntry = {},
                    onCloseManualEntry = {},
                    onManualCodeChange = {},
                    onSubmitManualCode = {},
                    onToggleTorch = {},
                    onResumeScanning = {},
                    onBack = {},
                    onDismissTorchNotice = {},
                    cameraContent = {},
                )
            }
        }

        compose.onNodeWithText("此设备不支持手电筒。").assertIsDisplayed()
    }

    @Test
    fun `resume action appears after a capture pauses scanning`() {
        compose.setContent {
            CulinaryTheme {
                ScanScreen(
                    state =
                        ScanUiState(
                            cameraAccess = CameraAccessState.GRANTED,
                            scanning = false,
                            status = "已识别：6901234567892",
                        ),
                    onRequestPermission = {},
                    onOpenManualEntry = {},
                    onCloseManualEntry = {},
                    onManualCodeChange = {},
                    onSubmitManualCode = {},
                    onToggleTorch = {},
                    onResumeScanning = {},
                    onBack = {},
                    onDismissTorchNotice = {},
                    cameraContent = {},
                )
            }
        }

        compose.onNodeWithText("重新扫描").assertIsDisplayed()
        compose.onNodeWithText("已识别：6901234567892").assertIsDisplayed()
    }
}
