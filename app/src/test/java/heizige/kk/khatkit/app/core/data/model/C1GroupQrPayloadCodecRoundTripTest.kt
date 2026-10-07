package heizige.kk.khatkit.app.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C1-09 ③「QR 编解码往返」里**能在 JVM 纯代码跑的那一半**。
 *
 * ## 为什么这里不是「二维码位图」往返（范围声明，勿误读）
 *
 * 真正的二维码**位图**编解码在仓库里是两条**无法在 JVM 单测里执行**的生产路径：
 *
 * - **编码**：`core/ui/components/ui/QRCode.kt:29-39` 在 `@Composable` 体内联调
 *   zxing 的 `QRCodeWriter.encode(value, BarcodeFormat.QR_CODE, size, size)`，随后直接
 *   把 bit matrix 写进 Compose 的 `Image`。仓库**没有**一个可独立调用的
 *   `String -> Bitmap` 生产函数；要造一个就得动 `app/src/main`（抽函数 / 提可见性），
 *   本次任务明确要求「能不动生产就不动」，故不做。
 * - **解码**：`core/ui/components/ui/QrScannerSheet.kt:318-322` 用 MLKit
 *   `BarcodeScanning`，其输入来自相机帧（`analyzeFrame` 的
 *   `InputImage.fromMediaImage(...)`，:413）。MLKit 需要 Android 运行时，JVM 单测跑不了。
 *
 * 且 `app/build.gradle.kts:343` 的 `testImplementation` **只有 junit**（无 Robolectric），
 * JVM 侧既加载不了 MLKit，也实例化不了 `android.content.Intent`。
 * ⇒ **「字符串 ↔ 二维码位图」的往返在本仓当前依赖与本任务约束下做不到**（详见交付报告）。
 *
 * ## 本文件覆盖什么
 *
 * 二维码里装的字节就是 `GroupChat.encodeQr(...)` 的返回值；扫码解出的字符串再交给
 * `GroupChat.decodeSharePayload` / `GroupChat.importShare`。这段**字符串 ↔ 群配置**的
 * 编码/解码是位图往返的**必要组成**，且 100% 走生产函数，可在此纯代码逐字段验证：
 * roles 顺序、mode、chair_role_id、token_budget_per_round、角色卡最小元数据。
 *
 * 断言刻意**逐字段**而非只比 `assertEquals(config, decoded)`：整体相等会把
 * 「某一字段恰好没被序列化、但默认值又恰好相等」的漏网掩盖掉；逐字段 + 显式期望值
 * 才能钉住契约键的映射。
 */
class C1GroupQrPayloadCodecRoundTripTest {

    private fun role(id: String, name: String, chair: Boolean = false) =
        GroupRole(id = id, name = name, assistantId = "asst-$id", chair = chair)

    /** 刻意用非字典序的 id（r2 → r1 → r3），用来暴露「往返时把 roles 排序了」的回归。 */
    private val roles = listOf(
        role("r2", "Bob"),
        role("r1", "Alice"),
        role("r3", "Cara", chair = true),
    )

    private fun config() = GroupConfig(
        roles = roles,
        mode = GroupChat.MODE_ROUNDTABLE,
        chairRoleId = "r3",
        tokenBudgetPerRound = 1234,
    )

    /**
     * 角色卡最小元数据。故意让中间那张卡 `cardId` / `avatarRef` 为 `null`：
     * 这正是 `decodeCards` 用 `string` 而非裸 `content` 的回归点 —— `JsonNull` 也是
     * `JsonPrimitive` 且 `content == "null"`，裸取会把 `null` 读成字符串 `"null"`。
     */
    private val cards = listOf(
        RoleCardMeta(
            roleId = "r2",
            name = "Bob",
            assistantId = "asst-r2",
            cardId = null,
            persona = "热血解说",
            avatarRef = null,
        ),
        RoleCardMeta(
            roleId = "r1",
            name = "Alice",
            assistantId = "asst-r1",
            cardId = "card-r1",
            persona = "冷面顾问",
            avatarRef = "avatar://r1",
        ),
        RoleCardMeta(
            roleId = "r3",
            name = "Cara",
            assistantId = "asst-r3",
            cardId = "card-r3",
            persona = "议长",
            avatarRef = "avatar://r3",
        ),
    )

    // ------------------------------------------------------------------
    // 1. encodeQr -> importShare：受闸门导入路径的完整往返
    // ------------------------------------------------------------------

    @Test
    fun `encodeQr payload survives importShare round trip field by field`() {
        val raw = GroupChat.encodeQr(config(), cards)

        val result = GroupChat.importShare(raw)
        assertTrue("自产载荷必须被 importShare 放行，实际：$result", result is GroupImportResult.Accepted)
        val payload = (result as GroupImportResult.Accepted).payload

        val decoded = payload.config
        assertEquals("schema_version 原样往返", GroupChat.SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals("mode 原样往返", GroupChat.MODE_ROUNDTABLE, decoded.mode)
        assertEquals("chair_role_id 原样往返", "r3", decoded.chairRoleId)
        assertEquals("token_budget_per_round 原样往返", 1234, decoded.tokenBudgetPerRound)

        // roles 顺序必须逐位保持，不能被排序（id 已刻意乱序）。
        assertEquals(
            "roles 顺序原样往返",
            listOf("r2", "r1", "r3"),
            decoded.roles.map { it.id },
        )
        assertEquals(listOf("Bob", "Alice", "Cara"), decoded.roles.map { it.name })
        assertEquals(listOf(false, false, true), decoded.roles.map { it.chair })
        assertEquals("整个 config 逐字段相等（含未知字段 extras）", config(), decoded)
    }

    @Test
    fun `role card minimal metadata survives importShare round trip`() {
        val raw = GroupChat.encodeQr(config(), cards)
        val result = GroupChat.importShare(raw) as GroupImportResult.Accepted
        val decodedCards = result.payload.cards

        assertEquals("角色卡顺序原样往返", listOf("r2", "r1", "r3"), decodedCards.map { it.roleId })
        assertEquals(cards, decodedCards)

        // 逐字段钉死契约键映射，别只靠整体 equals。
        val bob = decodedCards[0]
        assertEquals("Bob", bob.name)
        assertEquals("asst-r2", bob.assistantId)
        assertEquals("热血解说", bob.persona)
        // ⚠️ null 必须还是 null，不能被读成字符串 "null"（decodeCards 的 `string()` 回归点）。
        assertNull("card_id 缺省必须是 null，不能变成字符串 \"null\"", bob.cardId)
        assertNull("avatar_ref 缺省必须是 null，不能变成字符串 \"null\"", bob.avatarRef)

        val alice = decodedCards[1]
        assertEquals("card-r1", alice.cardId)
        assertEquals("avatar://r1", alice.avatarRef)
        assertNotNull(alice.cardId)
    }

    // ------------------------------------------------------------------
    // 2. encodeQr -> decodeSharePayload / decodeQr：便捷解码路径
    // ------------------------------------------------------------------

    @Test
    fun `decodeSharePayload round trip preserves every contract field`() {
        val raw = GroupChat.encodeQr(config())

        val payload = GroupChat.decodeSharePayload(raw)
        assertNotNull("decodeSharePayload 必须解出自产载荷", payload)
        requireNotNull(payload)

        assertEquals(GroupChat.QR_KIND, payload.kind)
        assertEquals(GroupChat.SCHEMA_VERSION, payload.schemaVersion)
        assertEquals(GroupChat.MODE_ROUNDTABLE, payload.config.mode)
        assertEquals("r3", payload.config.chairRoleId)
        assertEquals(1234, payload.config.tokenBudgetPerRound)
        assertEquals(listOf("r2", "r1", "r3"), payload.config.roles.map { it.id })
        assertEquals(config(), payload.config)
    }

    @Test
    fun `decodeQr convenience equals decodeSharePayload config`() {
        val raw = GroupChat.encodeQr(config(), cards)

        val viaConvenience = GroupChat.decodeQr(raw)
        assertNotNull(viaConvenience)
        // 便捷入口必须与完整入口解出**同一个** config（cards 会被它丢掉，见其 KDoc）。
        assertEquals(GroupChat.decodeSharePayload(raw)?.config, viaConvenience)
        assertEquals(config(), viaConvenience)
    }

    /**
     * 二次往返幂等：`encode -> decode -> encode` 与原载荷逐字节相等。
     *
     * 这一条守的是「扫码导入后再分享出去」不会因为一次往返而漂移（键顺序 / null 处理）。
     */
    @Test
    fun `second round trip is byte identical to the first`() {
        val first = GroupChat.encodeQr(config(), cards)
        val payload = GroupChat.decodeSharePayload(first)
        assertNotNull(payload)
        requireNotNull(payload)

        val second = GroupChat.encodeQr(payload.config, payload.cards)
        assertEquals("encode->decode->encode 必须字节相等", first, second)
    }
}
