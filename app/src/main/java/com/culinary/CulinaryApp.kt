package com.culinary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.data.startup.StartupState
import com.culinary.navigation.CulinaryNavHost
import com.culinary.startup.IncompatibleScreen
import com.culinary.startup.RecoveryScreen
import com.culinary.startup.StartupLoadingScreen
import com.culinary.startup.StartupViewModel

/**
 * 应用外壳：启动状态 → 主界面 / 兼容性提示 / 恢复页。
 *
 * 状态判断不在 Composable 内做业务计算；这里只做分支渲染。
 */
@Composable
fun CulinaryApp(viewModel: StartupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        null -> StartupLoadingScreen()
        StartupState.Ready -> CulinaryNavHost()
        is StartupState.Incompatible ->
            IncompatibleScreen(
                databaseVersion = current.databaseVersion,
                appSchemaVersion = current.appSchemaVersion,
            )
        is StartupState.Recovery ->
            RecoveryScreen(
                reason = current.reason,
                onRetry = viewModel::retry,
            )
    }
}
