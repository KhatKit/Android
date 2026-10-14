package heizige.kk.khatkit.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * AI 提供商图标的三道护栏。
 *
 * t45 起的设计：图标一律是**中性单色** —— 底色取主题 `secondaryContainer`、图形取
 * `onSecondaryContainer`，且单色由**渲染层结构性保证**（`AIIcon.kt` 对每个资产无条件
 * `ColorFilter.tint`），不再依赖「我们的资产碰巧是单色」，也不再按提供商分品牌色。
 *
 * 所以这里有三条线：
 * 1. [渲染层对每个资产统一 tint]：源码护栏 —— `AIIcon.kt` 必须对所有资产无条件 tint；
 * 2. [目录下所有 svg 都是单色]：资产层再兜一道，目录**全量**扫描（含未登记的孤儿、
 *    有意保留的 `grok.svg`，并钉住允许的光栅 .png 集合），逐元素 `fill` / `stroke`、
 *    `style="…"` 与 `<style>` 块里的字面色值合计不得超过 1 种；
 * 3. [每个代表性提供商都匹配到真实存在的图标文件]：匹配器每条正则都落到存在的文件。
 *
 * 图标来源与许可见 `app/src/main/assets/icons/LICENSES.md`。
 */
class AIIconAssetsTest {

    /**
     * 每个 `PATTERN_*` 至少一个代表性名称 → 期望文件名。
     * 名称要按 `AIIconMatcher.kt` 里 `when` 的**顺序**判，所以这里刻意避开了会先命中
     * 别的正则的子串（例如任何带 `llama` 的名字都会先命中 `PATTERN_META`）。
     */
    private val representativeIcons: Map<String, String> = mapOf(
        "gpt-4o" to "openai.svg",
        "gemini-2.0-flash" to "gemini.svg",
        "google" to "google.svg",
        "claude-3-7-sonnet" to "claude.svg",
        "anthropic" to "anthropic.svg",
        "deepseek-chat" to "deepseek.svg",
        "grok-4" to "xai.svg",
        "qwen-max" to "qwen.svg",
        "doubao-pro" to "doubao.svg",
        "openrouter" to "openrouter.svg",
        "zhipu-glm" to "zhipu.svg",
        "mistral-large" to "mistral.svg",
        "meta-llama-3" to "meta.svg",
        "hunyuan-turbo" to "hunyuan.svg",
        "gemma-3" to "gemma.svg",
        "perplexity-sonar" to "perplexity.svg",
        "aliyun-bailian" to "alibabacloud.svg",
        "bytedance-seed" to "bytedance.svg",
        "siliconflow" to "siliconcloud.svg",
        "aihubmix" to "aihubmix.svg",
        "ollama" to "ollama.svg",
        "github-copilot" to "github.svg",
        "cloudflare-workers-ai" to "cloudflare.svg",
        "minimax-abab" to "minimax.svg",
        "xai" to "xai.svg",
        "juhenext" to "juhenext.png",
        "kimi-k2" to "kimi.svg",
        "moonshot-v1" to "moonshot.svg",
        "302ai" to "ai302.svg",
        "step-2" to "stepfun.svg",
        "internlm2" to "internlm.svg",
        "cohere-command-r" to "cohere.svg",
        "tavern" to "sillytavern.svg",
        "cerebras" to "cerebras.svg",
        "nvidia-nim" to "nvidia.svg",
        "ppio" to "ppio.svg",
        "vercel-ai" to "vercel.svg",
        "groq" to "groq.svg",
        "tokenpony" to "tokenpony.svg",
        "ling-flash" to "ling.png",
        "xiaomimimo" to "xiaomimimo.svg",
        "longcat-flash" to "longcat.svg",
        "elevenlabs-v3" to "elevenlabs.svg",
        "fish-audio" to "fishaudio.svg",
        "rikka" to "khatkit.svg",
        "linkup" to "linkup.png",
        "bing-web" to "bing.svg",
        "tavily-search" to "tavily.svg",
        "exa-search" to "exa.svg",
        "brave-search" to "brave.svg",
        "metaso" to "metaso.svg",
        "firecrawl" to "firecrawl.svg",
        "jina-reader" to "jina.svg",
        "searxng" to "searxng.svg",
    )

    @Test
    fun `每个代表性提供商都匹配到真实存在的图标文件`() {
        assertTrue(
            "样本表被清空了，护栏在空跑。工作目录：${File("").absolutePath}",
            representativeIcons.size >= 50,
        )
        representativeIcons.forEach { (name, expected) ->
            assertEquals("$name 的图标映射变了（改了就要同步改这里）", expected, computeAIIconByName(name))
            val file = File(iconsDir(), expected)
            assertTrue("$name → $expected 在 assets/icons 下不存在（会静默退化成字母头像）", file.isFile)
            assertTrue("$expected 是空文件", file.length() > 0)
        }
    }

    /**
     * 资产层护栏：**遍历 `iconsDir()` 全量**（不是只遍历映射表里那几个文件），用 XML 解析
     * 取「有效颜色集合」：
     *
     * 1. 不能有渐变 / 图案 / 滤镜定义，也不能有 `url(#…)` 引用；
     * 2. 元素上的 `fill` / `stroke` 属性里的**字面色值**合计 ≤ 1 种；
     * 3. `style="…"` 属性里的 `fill:` / `stroke:` 声明合计 ≤ 1 种；
     * 4. `<style>` 块里的 `fill:` / `stroke:` 声明合计 ≤ 1 种。
     *
     * `none` / `currentColor` / `inherit` / `transparent` 是**合法**写法，一律放行
     * （`khatkit.svg` 合法地用着 `stroke="currentColor"`，正是靠这条不被误伤）。
     * `.png` 的颜色在像素里、资产层查不出多色，所以这里只钉住「允许存在的光栅集合」，
     * 由渲染层的 tint 兜底（见 [渲染层对每个资产统一 tint]）。
     */
    @Test
    fun `目录下所有 svg 都是单色`() {
        val files = iconsDir().listFiles().orEmpty().filter { it.isFile }
        val svgFiles = files.filter { it.extension.equals("svg", ignoreCase = true) }.sortedBy { it.name }
        assertTrue("iconsDir 下没扫到 SVG，护栏在空跑：${iconsDir()}", svgFiles.size >= 50)

        val problems = mutableListOf<String>()
        svgFiles.forEach { file ->
            val doc = parseXml(file)
            val literals = mutableSetOf<String>()

            val elements = doc.getElementsByTagName("*")
            for (i in 0 until elements.length) {
                val el = elements.item(i) as? Element ?: continue
                val tag = el.tagName.substringAfter(':').lowercase()
                if (tag in GRADIENT_OR_PATTERN_TAGS) {
                    problems += "${file.name}: 有 <$tag> 定义（多色 logo 特征）"
                }
                listOf("fill", "stroke").forEach { attr ->
                    val value = el.getAttribute(attr)?.trim().orEmpty()
                    if (value.isEmpty()) return@forEach
                    when {
                        value.startsWith("url(", ignoreCase = true) ->
                            problems += "${file.name}: $attr=\"$value\" 是引用（渐变/图案）"

                        isAllowedKeyword(value) -> Unit
                        else -> literals += value.lowercase()
                    }
                }
                collectStyleDeclarations(el.getAttribute("style").orEmpty(), literals, problems, file.name)
            }

            val styleNodes = doc.getElementsByTagName("style")
            for (i in 0 until styleNodes.length) {
                collectStyleDeclarations(styleNodes.item(i).textContent.orEmpty(), literals, problems, file.name)
            }

            if (literals.size > 1) {
                problems += "${file.name}: 出现 ${literals.size} 种字面色值 $literals（单色最多 1 种）"
            }
        }

        assertTrue(
            "以下 SVG 不满足「单色」：\n" + problems.joinToString("\n"),
            problems.isEmpty(),
        )

        // 允许存在的光栅集合（多色像素靠渲染层 tint 归一到单色）；新增光栅必须在这里登记
        val rasters = files.filter { it.extension.equals("png", ignoreCase = true) }.map { it.name }.toSortedSet()
        assertEquals(
            "光栅资产的集合变了：多色 .png 只能靠 AIIcon 的 tint 兜住，新增前请确认并同步这里",
            setOf("juhenext.png", "ling.png", "linkup.png"),
            rasters,
        )
        assertTrue(
            "光栅文件不能是空文件",
            files.filter { it.extension.equals("png", ignoreCase = true) }.all { it.length() > 0 },
        )

        // 目录里除 svg / png / 说明文件外不应混进别的类型
        val others = files
            .filterNot { it.name.endsWith(".svg") || it.name.endsWith(".png") || it.name.endsWith(".md") }
            .map { it.name }
        assertTrue("icons 目录里出现了未知类型的文件：$others", others.isEmpty())
    }

    @Test
    fun `资源目录里不再有 -color 后缀的文件`() {
        val leftovers = iconsDir().listFiles().orEmpty()
            .filter { it.name.endsWith("-color.svg") }
            .map { it.name }
        assertTrue(
            "这些彩色版应该已被 lobe 单色版替换：$leftovers",
            leftovers.isEmpty(),
        )
        // 未知提供商必须返回 null，交给 AutoAIIcon 退化成字母头像（不是乱指一个文件）
        assertEquals(null, computeAIIconByName("某个不存在的提供商 xyzzy"))
    }

    /**
     * 渲染层护栏（源码级）：单色必须由 `AIIcon.kt` **无条件** tint 保证 ——
     * 一旦有人把 `colorFilter` 改回「只对 .png 生效」，或重新引入按提供商取色的机制，
     * 这条会立刻红。资产里塞进逐元素 fill / 内联 `<style>` / stroke 也漏不出第二色，
     * 因为 SrcIn 着色只保留 alpha、把全部 RGB 换成 contentColor
     * （真实渲染实验见任务 t45 的证据：多色 SVG 与多色 PNG 经 SrcIn 后像素只剩 1 种颜色）。
     */
    @Test
    fun `渲染层对每个资产统一 tint`() {
        val source = File(
            repoRoot(),
            "app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/AIIcon.kt",
        ).readText()
        assertTrue("AIIcon.kt 读不到或为空，护栏在空跑", source.isNotEmpty())

        assertTrue(
            "AIIcon.kt 必须对每个资产无条件做 ColorFilter.tint(contentColor)",
            source.contains("ColorFilter.tint(contentColor)"),
        )
        assertTrue(
            "不许再按扩展名分支决定是否 tint（SVG 与 PNG 必须一视同仁）",
            !source.contains("endsWith(\".png\""),
        )
        listOf("aiIconBrandColor", "aiIconForegroundOn", "AI_ICON_BRAND_COLORS").forEach { symbol ->
            assertTrue("AIIcon.kt 不许再出现按提供商取色的 $symbol", !source.contains(symbol))
        }
        assertTrue(
            "缺省底色必须是主题 secondaryContainer",
            source.contains("MaterialTheme.colorScheme.secondaryContainer"),
        )
        assertTrue(
            "缺省图形色必须是主题 onSecondaryContainer",
            source.contains("MaterialTheme.colorScheme.onSecondaryContainer"),
        )
    }

    private fun iconsDir(): File = File(repoRoot(), "app/src/main/assets/icons")

    private fun repoRoot(): File {
        var dir = File("").absoluteFile
        while (true) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: error("找不到仓库根（向上没有 settings.gradle.kts）")
        }
    }

    /** 用 XML 解析（而不是正则），保证不会漏掉藏在属性和 `<style>` 里的颜色。 */
    private fun parseXml(file: File): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        return factory.newDocumentBuilder().parse(file)
    }

    /**
     * 从一段 CSS（`style="…"` 属性或 `<style>` 块）里取出 `fill:` / `stroke:` 的字面色值。
     * 关键字（`none` / `currentColor` / `inherit` / `transparent`）放行。
     */
    private fun collectStyleDeclarations(
        css: String,
        literals: MutableSet<String>,
        problems: MutableList<String>,
        fileName: String,
    ) {
        if (css.isBlank()) return
        COLOR_DECLARATION.findAll(css).forEach { match ->
            val value = match.groupValues[2].trim()
            when {
                value.isEmpty() -> Unit
                value.startsWith("url(", ignoreCase = true) ->
                    problems += "$fileName: CSS 里的 ${match.groupValues[1]}: $value 是引用"

                isAllowedKeyword(value) -> Unit
                else -> literals += value.lowercase()
            }
        }
    }

    private fun isAllowedKeyword(value: String): Boolean =
        value.lowercase() in ALLOWED_COLOR_KEYWORDS

    private companion object {
        /** CSS/属性里允许的「非字面颜色」关键字：合法写法，不算多色。 */
        private val ALLOWED_COLOR_KEYWORDS =
            setOf("none", "currentcolor", "inherit", "transparent")

        /** 多色 logo 的典型特征：渐变、图案、滤镜定义。 */
        private val GRADIENT_OR_PATTERN_TAGS =
            setOf("lineargradient", "radialgradient", "pattern", "filter")

        /** `fill: #fff` / `stroke : rgb(0,0,0)` 这类声明。 */
        private val COLOR_DECLARATION = Regex("""(fill|stroke)\s*:\s*([^;}"']+)""")
    }
}
