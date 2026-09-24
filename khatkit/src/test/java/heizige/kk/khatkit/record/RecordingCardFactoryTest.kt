package heizige.kk.khatkit.record

import heizige.kk.khatkit.card.CardParser
import heizige.kk.khatkit.card.CardValidator
import heizige.kk.khatkit.card.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingCardFactoryTest {

    private val click = RecordedStep(
        type = RecordedStep.Type.CLICK,
        atMillis = 1_000L,
        packageName = "com.example.app",
        viewId = "com.example.app:id/login",
        text = "登录",
        x = 540,
        y = 960,
    )

    private val input = RecordedStep(
        type = RecordedStep.Type.SET_TEXT,
        atMillis = 2_500L,
        packageName = "com.example.app",
        viewId = "com.example.app:id/input",
        inputText = "hello \"world\"",
        x = 540,
        y = 400,
    )

    private val swipe = RecordedStep(
        type = RecordedStep.Type.SWIPE,
        atMillis = 4_200L,
        packageName = "com.example.app",
        direction = "down",
        x = 540,
        y = 1_800,
        x2 = 540,
        y2 = 600,
    )

    private val openApp = RecordedStep(
        type = RecordedStep.Type.OPEN_APP,
        atMillis = 9_000L,
        packageName = "com.tencent.mm",
    )

    @Test
    fun luaReplaysStepsWithSelectorsAndCoordinateFallbacks() {
        val card = RecordingCardFactory.create(
            title = "测试卡片",
            steps = listOf(click, input, swipe, openApp),
            nowMillis = 1_700_000_000_000L,
        )

        assertTrue(card.scriptText.startsWith("-- 录制回放卡片：测试卡片"))
        assertTrue(card.scriptText.contains("-- 第 1 步：点击「登录」"))
        assertTrue(card.scriptText.contains("accessibility.click({ viewId = \"com.example.app:id/login\" })"))
        assertTrue(card.scriptText.contains("accessibility.tap(540, 960)"))
        assertTrue(card.scriptText.contains("-- 第 2 步：输入「hello \"world\"」"))
        assertTrue(
            card.scriptText.contains(
                "accessibility.setText({ viewId = \"com.example.app:id/input\" }, \"hello \\\"world\\\"\")"
            )
        )
        assertTrue(card.scriptText.contains("-- 第 3 步：向下滑动"))
        assertTrue(card.scriptText.contains("accessibility.scroll(\"down\")"))
        assertTrue(card.scriptText.contains("accessibility.swipe(540, 1800, 540, 600, 300)"))
        assertTrue(card.scriptText.contains("-- 第 4 步：打开应用「com.tencent.mm」"))
        assertTrue(card.scriptText.contains("accessibility.openApp(\"com.tencent.mm\")"))
        assertTrue(card.scriptText.contains("accessibility.waitForPackage(\"com.tencent.mm\", 5000)"))
        assertTrue(card.scriptText.contains("accessibility.waitForNode({ viewId = \"com.example.app:id/login\" }, 3000)"))
        assertTrue(card.scriptText.endsWith("return { ok = okAll, steps = 4 }\n"))
    }

    @Test
    fun sleepUsesClampedIntegerSeconds() {
        // 1000 → 1500ms 间隔 → 1.5 秒四舍五入为 2
        assertTrue(RecordingCardFactory.buildLua("t", listOf(click, input)).contains("tool.sleep(2)"))
        // 2500 → 4200ms 间隔 → 1.7 秒四舍五入为 2
        assertTrue(RecordingCardFactory.buildLua("t", listOf(click, input, swipe)).contains("tool.sleep(2)"))
    }

    @Test
    fun sleepLineClampsGapIntoRange() {
        assertNull(RecordingCardFactory.sleepLine(120L))
        assertNull(RecordingCardFactory.sleepLine(999L))
        assertEquals("tool.sleep(1)", RecordingCardFactory.sleepLine(1_000L))
        assertEquals("tool.sleep(5)", RecordingCardFactory.sleepLine(60_000L))
    }

    @Test
    fun manifestDeclaresUserTriggerAndRequiredBridges() {
        val card = RecordingCardFactory.create("测试卡片", listOf(click), nowMillis = 1_700_000_000_000L)
        val manifest = CardParser.parse(card.manifestJson).getOrThrow()

        assertEquals("record_ops_000000", card.name)
        assertEquals("1.0.0", card.version)
        assertEquals("lua", manifest.engine)
        assertEquals("main.lua", manifest.entry.lua)
        assertEquals("elevated", manifest.privilege)
        assertEquals(listOf("user"), manifest.triggers)
        assertTrue(manifest.requires.bridges.containsAll(listOf("tool", "ui", "accessibility")))
        assertEquals("device", manifest.tags?.domain)
        assertEquals("control", manifest.tags?.action)
        assertTrue(manifest.description.contains("测试卡片"))
        // 生成的卡片必须能通过自己的卡片校验
        val issues = CardValidator.validate(manifest, mapOf("main.lua" to card.scriptText))
        assertTrue(issues.joinToString { "${it.code}:${it.message}" }, issues.none { it.severity == Severity.ERROR })
    }

    @Test
    fun nameIsValidSlugAndUniquePerTimestamp() {
        val first = RecordingCardFactory.buildName("每日 签到！", 1_700_000_000_000L)
        val second = RecordingCardFactory.buildName("每日 签到！", 1_700_000_001_000L)
        assertTrue(first.matches(Regex("^[a-z][a-z0-9_]{1,63}$")))
        assertFalse(first == second)
    }

    @Test
    fun waitStepOnlyWaitsForIdle() {
        val wait = RecordedStep(type = RecordedStep.Type.WAIT, atMillis = 10L, packageName = "com.example.app")
        val lua = RecordingCardFactory.buildLua("等待", listOf(wait))
        assertTrue(lua.contains("accessibility.waitForIdle(3000)"))
        assertFalse(lua.contains("tool.sleep"))
    }
}
