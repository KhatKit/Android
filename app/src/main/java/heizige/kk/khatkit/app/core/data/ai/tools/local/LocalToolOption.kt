package heizige.kk.khatkit.app.core.data.ai.tools.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class LocalToolOption {
    @Serializable
    @SerialName("javascript_engine")
    data object JavascriptEngine : LocalToolOption()

    @Serializable
    @SerialName("time_info")
    data object TimeInfo : LocalToolOption()

    @Serializable
    @SerialName("clipboard")
    data object Clipboard : LocalToolOption()

    @Serializable
    @SerialName("tts")
    data object Tts : LocalToolOption()

    @Serializable
    @SerialName("ask_user")
    data object AskUser : LocalToolOption()

    @Serializable
    @SerialName("screen_time")
    data object ScreenTime : LocalToolOption()

    @Serializable
    @SerialName("calendar")
    data object Calendar : LocalToolOption()

    @Serializable
    @SerialName("chart_display")
    data object ChartDisplay : LocalToolOption()

    companion object {
        /**
         * 全部本地工具（默认助手与新建助手的出厂状态：全开）。
         * 顺序与 `AssistantLocalToolPage` 的开关列表一致，改这里不用再改第二处。
         */
        val ALL: List<LocalToolOption> = listOf(
            JavascriptEngine,
            TimeInfo,
            Clipboard,
            Tts,
            AskUser,
            ScreenTime,
            Calendar,
            ChartDisplay,
        )
    }
}
