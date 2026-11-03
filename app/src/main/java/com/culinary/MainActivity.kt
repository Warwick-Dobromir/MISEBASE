package com.culinary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.culinary.core.designsystem.theme.CulinaryTheme
import com.culinary.navigation.CulinaryNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * 唯一的 Activity（单 Activity + Navigation Compose，A01）。
 *
 * 只负责主题与导航容器；启动协调、恢复流程与业务规则不写在这里（阶段 6.2）。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CulinaryTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CulinaryNavHost()
                }
            }
        }
    }
}
