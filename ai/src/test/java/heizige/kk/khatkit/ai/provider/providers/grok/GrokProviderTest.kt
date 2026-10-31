package heizige.kk.khatkit.ai.provider.providers.grok

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import heizige.kk.khatkit.ai.core.ReasoningLevel
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

class GrokProviderTest {
    private val api = ResponseAPI(OkHttpClient())

    private fun grokBody(
        model: Model,
        reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
        messages: List<UIMessage> = listOf(UIMessage.user("hello")),
    ) = api.buildRequestBody(
        capabilities = GROK_CAPABILITIES,
        messages = messages,
        params = TextGenerationParams(model = model, reasoningLevel = reasoningLevel),
        stream = true,
    )

    @Test
    fun `system prompt becomes system message instead of instructions`() {
        val body = grokBody(
            model = Model(modelId = "grok-4.6"),
            messages = listOf(UIMessage.system("Be brief."), UIMessage.user("hello")),
        )

        assertFalse("instructions" in body)
        val input = body["input"]!!.jsonArray
        assertEquals("system", input[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Be brief.", input[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("user", input[1].jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `xhigh only for grok-4_6 and others fall back to high`() {
        val newest = grokBody(Model(modelId = "grok-4.6"), ReasoningLevel.XHIGH)
        assertEquals(
            "xhigh",
            newest["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content,
        )

        val older = grokBody(Model(modelId = "grok-4.5"), ReasoningLevel.XHIGH)
        assertEquals("high", older["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)

        // AUTO 不发送档位
        val auto = grokBody(Model(modelId = "grok-4.5"), ReasoningLevel.AUTO)
        assertNull(auto["reasoning"]?.jsonObject?.get("effort"))

        // OFF 明确发 none
        val off = grokBody(Model(modelId = "grok-4.5"), ReasoningLevel.OFF)
        assertEquals("none", off["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
    }

    @Test
    fun `grok-4_20 reasoning models never send effort`() {
        val body = grokBody(
            Model(modelId = "grok-4.20-reasoning", abilities = listOf(ModelAbility.REASONING)),
            ReasoningLevel.HIGH,
        )

        assertNull(body["reasoning"]?.jsonObject?.get("effort"))
        // 但加密推理内容仍要带
        assertEquals(
            listOf("reasoning.encrypted_content"),
            body["include"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `merges catalogs with cli priority and filters non chat models`() {
        val cli = json.parseToJsonElement(
            """
            {"data": [
              {"model": "grok-4.6", "name": "Grok 4.6", "reasoningEfforts": ["low", "high"]},
              {"model": "grok-imagine", "name": "imagine"},
              {"model": "hidden-model", "hidden": true}
            ]}
            """.trimIndent()
        ).jsonObject
        val api = json.parseToJsonElement(
            """
            {"data": [
              {"id": "grok-4.6", "name": "Grok 4.6 (api)"},
              {"id": "grok-3", "name": "Grok 3"}
            ]}
            """.trimIndent()
        ).jsonObject

        val models = parseGrokModels(cliCatalog = cli, apiCatalog = api)

        assertEquals(listOf("grok-4.6", "grok-3"), models.map { it.modelId })
        val newest = models[0]
        // CLI 目录优先，且声明了档位 ⇒ 推理模型
        assertEquals("Grok 4.6", newest.displayName)
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), newest.abilities)
        // 没有档位声明的按普通模型
        assertEquals(listOf(ModelAbility.TOOL), models[1].abilities)
    }

    @Test
    fun `grok provider round trips through provider list serialization`() {
        val provider = ProviderSetting.Grok(
            credential = OAuthCredential(accessToken = "access", accountId = "acc"),
        )

        val encoded = json.encodeToString<List<ProviderSetting>>(listOf(provider))
        assertTrue(encoded.contains("\"type\":\"grok\""))
        val decoded = json.decodeFromString<List<ProviderSetting>>(encoded).single()

        assertTrue(decoded is ProviderSetting.Grok)
        assertEquals(provider.credential, (decoded as ProviderSetting.Grok).credential)
    }
}
