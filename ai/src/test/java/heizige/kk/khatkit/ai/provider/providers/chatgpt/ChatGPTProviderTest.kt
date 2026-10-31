package heizige.kk.khatkit.ai.provider.providers.chatgpt

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import heizige.kk.khatkit.ai.provider.Modality
import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ModelAbility
import heizige.kk.khatkit.ai.provider.OAuthCredential
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.ai.provider.TextGenerationParams
import heizige.kk.khatkit.ai.provider.providers.openai.ResponseAPI
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.ai.util.json
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGPTProviderTest {
    private val api = ResponseAPI(OkHttpClient())

    private fun codexBody(
        messages: List<UIMessage>,
        params: TextGenerationParams,
        sessionId: String? = "session-1",
    ) = api.buildRequestBody(
        capabilities = CODEX_CAPABILITIES,
        messages = messages,
        params = params.copy(sessionId = sessionId),
        stream = true,
    )

    @Test
    fun `system prompt becomes leading developer message instead of instructions`() {
        val body = codexBody(
            messages = listOf(UIMessage.system("Be brief."), UIMessage.user("hello")),
            params = TextGenerationParams(model = Model(modelId = "gpt-5.5")),
        )

        assertFalse("instructions" in body)
        val input = body["input"]!!.jsonArray
        val developer = input[0].jsonObject
        assertEquals("developer", developer["role"]!!.jsonPrimitive.content)
        assertEquals("Be brief.", developer["content"]!!.jsonPrimitive.content)
        assertEquals("user", input[1].jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `no developer message without system prompt`() {
        val body = codexBody(
            messages = listOf(UIMessage.user("hello")),
            params = TextGenerationParams(model = Model(modelId = "gpt-5.5")),
        )

        val input = body["input"]!!.jsonArray
        assertEquals(1, input.size)
        assertEquals("user", input[0].jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `streams without storage and drops unsupported parameters`() {
        val body = codexBody(
            messages = listOf(UIMessage.user("hello")),
            params = TextGenerationParams(
                model = Model(modelId = "custom-model"),
                temperature = 0.7f,
                topP = 0.9f,
                maxTokens = 1024,
            ),
        )

        assertEquals(true, body["stream"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(false, body["store"]!!.jsonPrimitive.content.toBoolean())
        assertFalse("max_output_tokens" in body)
        assertFalse("temperature" in body)
        assertFalse("top_p" in body)
        // 路由字段保持在最前面
        assertEquals(listOf("model", "stream", "store"), body.keys.take(3))
    }

    @Test
    fun `always includes encrypted reasoning and prompt cache key`() {
        val body = codexBody(
            messages = listOf(UIMessage.user("hello")),
            params = TextGenerationParams(model = Model(modelId = "gpt-5.5")),
            sessionId = "conversation-42",
        )

        assertEquals(
            listOf("reasoning.encrypted_content"),
            body["include"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("conversation-42", body["prompt_cache_key"]!!.jsonPrimitive.content)
    }

    @Test
    fun `keeps include from reasoning models and omits missing cache key`() {
        val body = codexBody(
            messages = listOf(UIMessage.user("hello")),
            params = TextGenerationParams(
                model = Model(modelId = "gpt-5.5", abilities = listOf(ModelAbility.REASONING)),
            ),
            sessionId = null,
        )

        assertEquals(1, body["include"]!!.jsonArray.size)
        assertTrue("reasoning" in body)
        assertNull(body["prompt_cache_key"])
    }

    @Test
    fun `parses visible codex models`() {
        val models = parseCodexModels(
            """
            {
              "models": [
                {
                  "slug": "gpt-5.5",
                  "display_name": "GPT-5.5",
                  "visibility": "list",
                  "supported_reasoning_levels": [{"effort": "low", "description": "fast"}],
                  "input_modalities": ["text", "image"],
                  "context_window": 272000
                },
                {
                  "slug": "gpt-5.5-mini",
                  "display_name": "GPT-5.5 mini",
                  "visibility": "list",
                  "supported_reasoning_levels": [],
                  "input_modalities": ["text"]
                },
                {
                  "slug": "internal-model",
                  "display_name": "Internal",
                  "visibility": "hide",
                  "supported_reasoning_levels": []
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(listOf("gpt-5.5", "gpt-5.5-mini"), models.map { it.modelId })
        val full = models[0]
        assertEquals("GPT-5.5", full.displayName)
        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), full.inputModalities)
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), full.abilities)
        assertEquals(272000, full.contextLength)
        val mini = models[1]
        assertEquals(listOf(Modality.TEXT), mini.inputModalities)
        assertEquals(listOf(ModelAbility.TOOL), mini.abilities)
    }

    @Test
    fun `falls back to all models when none are listed`() {
        val models = parseCodexModels(
            """{"models": [{"slug": "a", "display_name": "A", "visibility": "hide"}]}"""
        )

        assertEquals(listOf("a"), models.map { it.modelId })
        // 没给 input_modalities 时按 Codex 的默认值：文本 + 图片
        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), models.single().inputModalities)
    }

    @Test
    fun `chatgpt provider round trips through provider list serialization`() {
        val provider = ProviderSetting.ChatGPT(
            credential = OAuthCredential(accessToken = "access", refreshToken = "refresh", accountId = "acc"),
        )

        val encoded = json.encodeToString<List<ProviderSetting>>(listOf(provider))
        assertTrue(encoded.contains("\"type\":\"chatgpt\""))
        val decoded = json.decodeFromString<List<ProviderSetting>>(encoded).single()

        assertTrue(decoded is ProviderSetting.ChatGPT)
        assertEquals(provider.credential, (decoded as ProviderSetting.ChatGPT).credential)
    }

    @Test
    fun `credential is masked in toString`() {
        val credential = OAuthCredential(accessToken = "abcdefghijklmnop", refreshToken = "qrstuvwxyz")
        val text = credential.toString()

        assertFalse(text.contains("abcdefghijklmnop"))
        assertFalse(text.contains("qrstuvwxyz"))
        assertTrue(text.contains("abcd***mnop"))
    }
}
