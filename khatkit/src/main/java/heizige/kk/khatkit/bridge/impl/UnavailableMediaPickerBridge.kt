package heizige.kk.khatkit.bridge.impl

import heizige.kk.khatkit.bridge.MediaPickerBridge

class UnavailableMediaPickerBridge : MediaPickerBridge {
    override fun pickMedia(options: Map<String, Any?>): List<String> =
        throw IllegalStateException("媒体选择器暂不可用：请在宿主界面中打开卡片")
}
