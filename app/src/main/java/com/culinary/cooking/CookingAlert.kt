package com.culinary.cooking

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * 计时结束提示音。
 *
 * 只由 [CookingViewModel] 在"计时自然归零"这一条路径调用一次；
 * UI 重组、旋转屏幕或状态恢复都不会再次触发，避免重复播放。
 */
interface CookingAlert {
    fun playCompletion()
}

/**
 * 使用系统 [ToneGenerator] 播放一次短提示音。
 *
 * 应用当前没有内置音频资源，也没有既有播放器；这里沿用 Android 平台能力，
 * 不新增权限、不引入第三方音频库、不申请网络访问。
 */
class ToneCookingAlert : CookingAlert {
    override fun playCompletion() {
        val tone =
            runCatching {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME)
            }.getOrNull() ?: return
        runCatching { tone.startTone(ToneGenerator.TONE_PROP_BEEP2, DURATION_MILLIS) }
        Handler(Looper.getMainLooper()).postDelayed(
            { runCatching { tone.release() } },
            DURATION_MILLIS + RELEASE_DELAY_MILLIS,
        )
    }

    private companion object {
        const val VOLUME = 90
        const val DURATION_MILLIS = 600
        const val RELEASE_DELAY_MILLIS = 200L
    }
}
