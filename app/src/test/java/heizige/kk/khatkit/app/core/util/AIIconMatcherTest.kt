package heizige.kk.khatkit.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AIIconMatcherTest {
    @Test
    fun senseNovaAliasesMatchTheNewAsset() {
        listOf("SenseNova", "SenseTime", "SenseChat-5", "商汤", "日日新").forEach {
            assertEquals(it, "sensenova-color.svg", computeAIIconByName(it))
        }
    }

    @Test
    fun nvidiaModelNamesKeepTheExistingAsset() {
        listOf("NVIDIA", "Nemotron-70B", "英伟达").forEach {
            assertEquals(it, "nvidia.svg", computeAIIconByName(it))
        }
    }

    @Test
    fun existingProviderAndUnknownNamesAreUnchanged() {
        assertEquals("openai.svg", computeAIIconByName("OpenAI"))
        assertEquals("deepseek.svg", computeAIIconByName("DeepSeek"))
        assertNull(computeAIIconByName("unrecognized-provider"))
    }
}
