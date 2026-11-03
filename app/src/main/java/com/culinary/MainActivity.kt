package com.culinary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.culinary.core.designsystem.theme.CulinaryTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 唯一的 Activity（单 Activity + Navigation Compose，A01）。
 *
 * 阶段 1 只渲染框架占位文本，用于验证打包、安装与启动；导航在阶段 6 接入。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CulinaryTheme {
                FrameworkScreen()
            }
        }
    }
}

@Composable
private fun FrameworkScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.framework_placeholder),
            modifier = Modifier.wrapContentSize(Alignment.Center),
        )
    }
}
