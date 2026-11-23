package heizige.kk.khatkit.app.feature.settings.mcp

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.ai.mcp.McpServerConfig
import heizige.kk.khatkit.app.core.ui.hooks.EditState
import heizige.kk.khatkit.app.core.ui.hooks.useEditState
import heizige.kk.khatkit.uikit.KhatKitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class McpEditorDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun saveCancelAndReopenUseIndependentDrafts() {
        val save = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.setting_mcp_page_save)
        val saved = mutableListOf<String>()
        lateinit var editor: EditState<McpServerConfig>
        var closed = 0
        compose.setContent {
            KhatKitTheme {
                editor = useEditState { saved += it.commonOptions.name }
                McpServerConfigModal(editor, onClosed = { closed++ })
            }
        }
        fun open() = compose.runOnIdle { editor.open(McpServerConfig.StreamableHTTPServer()) }
        fun type(name: String) = compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement(name)
        open()
        type("first")
        compose.onNodeWithText(save).performClick()
        compose.waitUntil(5000) { closed == 1 }
        open()
        type("discarded")
        // Reopen before the native sheet has had time to finish closing.
        compose.runOnIdle { editor.dismiss() }
        compose.runOnUiThread { editor.open(McpServerConfig.SseTransportServer()) }
        compose.onNodeWithText("discarded").assertDoesNotExist()
        type("second")
        compose.onNodeWithText(save).performClick()
        compose.runOnIdle { assertEquals(listOf("first", "second"), saved) }
    }
}
