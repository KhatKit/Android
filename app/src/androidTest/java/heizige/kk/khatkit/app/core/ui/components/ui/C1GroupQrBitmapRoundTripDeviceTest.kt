package heizige.kk.khatkit.app.core.ui.components.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.WriterException
import heizige.kk.khatkit.app.core.data.ai.tavern.TavernChatCodec
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupImportResult
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.MessageNode
import heizige.kk.khatkit.app.core.data.model.RoleCardMeta
import heizige.kk.khatkit.app.feature.chat.GROUP_EXPORT_MIME_TYPE
import heizige.kk.khatkit.app.feature.chat.writeExportTempFile
import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.common.android.appTempFolder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * C1-09「二维码位图往返」的**真机端到端**闭环（不需要摄像头）。
 *
 * ## 为什么这条只有 androidTest 能跑
 *
 * 生产里编码是 zxing、解码是 MLKit：
 * - 编码 [encodeQrBitmap] 从 [QRCode] 的 `@Composable` 体内**原样提取**（本次唯一的生产
 *   改动，见该函数 KDoc）。提取后它能吃普通 `String` / `Int`，可以在测试里独立调用。
 * - 解码走生产同款 `BarcodeScanning.getClient(BarcodeScannerOptions...FORMAT_QR_CODE)`，
 *   输入用 `InputImage.fromBitmap(bitmap, 0)` 而不是相机帧——**这正是绕开摄像头的路径**。
 *   MLKit 需要 Android 运行时，JVM 单测（`testImplementation` 只有 junit，无 Robolectric）跑不了。
 *
 * 位图里装的字节就是 [GroupChat.encodeQr] 的返回值；解出的字符串再交给生产
 * [GroupChat.decodeSharePayload] / [GroupChat.importShare] 解回配置，逐字段断言。
 *
 * ## 与已有测试的分工
 *
 * - `C1GroupQrPayloadCodecRoundTripTest`（JVM）：只盖**字符串 ↔ 配置**，不碰位图。
 * - `QrScannerSheetTest`（JVM）：只盖扫码的纯逻辑，不碰 MLKit / Bitmap。
 * - 本类：位图 ↔ 字符串 ↔ 配置的**全链**，外加 FileProvider URI 真可读 + Intent 形状。
 *
 * ## ✔ 非 Latin-1 内容：已修复为显式 UTF-8 编码
 *
 * `encodeQrBitmap` 现在走 5 参重载并显式传 `EncodeHintType.CHARACTER_SET = "UTF-8"`。
 * 此前不带 hints 时 zxing 3.5.4 默认按 **ISO-8859-1**（`Encoder.DEFAULT_BYTE_MODE_ENCODING`）
 * 取字节，中文 / emoji 等非 Latin-1 字符被替换成 `?`，位图往返不闭环
 * （实测 `{"persona":"热血解说"}` → `{"persona":"????"}`）。修复由
 * [nonLatin1ContentRoundTripsVerbatimThroughBitmapAfterUtf8Fix] 正面钉住。
 *
 * 副作用见生产 `QRCode.encodeQrBitmap` 的 KDoc：UTF-8 会触发 zxing 写 **ECI 头**（12 bit），
 * v40-L 字节模式有效容量因此从 2953 降到 **2952** 字节（[qrV40LCapacityBytes] 已按实测更新）。
 *
 * ## 不证明什么
 *
 * - 不证明相机扫码（`analyzeFrame` 的 mediaImage 路径需要真实取景）。
 * - 不真的弹系统分享面板（`shareFile` 直接 `startActivity`，需用户点选目标 app）；
 *   本类只对**同一形状**的 Intent 对象断言 action / type / EXTRA_STREAM / flags。
 */
@RunWith(AndroidJUnit4::class)
class C1GroupQrBitmapRoundTripDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val prettyJson = Json { prettyPrint = true }

    /**
     * QR v40 / EC 级别 L / 字节模式的有效容量上限（zxing `QRCodeWriter.encode` 无 hint 时默认 L）。
     *
     * **2952，不是 2953**：生产编码现在显式带 `CHARACTER_SET = UTF-8`，zxing 会在字节模式载荷前
     * 写 12 bit 的 **ECI 头**（4 bit ECI 模式指示 + 8 bit ECI 设计值 26），比无 ECI 时多吃
     * 12 bit，正好吃掉 1 个字节的字节模式容量。实测（纯 JVM `QRCodeWriter.encode` 二分）：
     * 无 hint 时 `"a".repeat(2953)` 成功；带 UTF-8 hint 时 2953 抛 `WriterException`、2952 成功。
     */
    private val qrV40LCapacityBytes = 2952

    // ------------------------------------------------------------------
    // 夹具：刻意非字典序 id（r2 → r1 → r3），用来暴露「往返时把 roles 排序了」的回归
    // ------------------------------------------------------------------

    private val roles = listOf(
        GroupRole(id = "r2", name = "Bob", assistantId = "asst-r2"),
        GroupRole(id = "r1", name = "Alice", assistantId = "asst-r1"),
        GroupRole(id = "r3", name = "Cara", assistantId = "asst-r3", chair = true),
    )

    private fun config() = GroupConfig(
        roles = roles,
        mode = GroupChat.MODE_ROUNDTABLE,
        chairRoleId = "r3",
        tokenBudgetPerRound = 1234,
    )

    /**
     * 中间那张卡 `cardId` / `avatarRef` 为 `null`：`decodeCards` 用 `string` 而非裸 `content`，
     * 否则 `JsonNull`（也是 `JsonPrimitive`，`content == "null"`）会被读成字符串 `"null"`。
     *
     * 这里用 ASCII 内容，专门盖结构往返（字段顺序 / null 语义）；非 Latin-1（中文 + emoji）的
     * 字符集往返由 [nonLatin1ContentRoundTripsVerbatimThroughBitmapAfterUtf8Fix] 单独覆盖。
     */
    private val cards = listOf(
        RoleCardMeta(
            roleId = "r2",
            name = "Bob",
            assistantId = "asst-r2",
            cardId = null,
            persona = "hot-blooded commentator",
            avatarRef = null,
        ),
        RoleCardMeta(
            roleId = "r1",
            name = "Alice",
            assistantId = "asst-r1",
            cardId = "card-r1",
            persona = "cold-faced advisor",
            avatarRef = "avatar://r1",
        ),
        RoleCardMeta(
            roleId = "r3",
            name = "Cara",
            assistantId = "asst-r3",
            cardId = "card-r3",
            persona = "chair of the round table",
            avatarRef = "avatar://r3",
        ),
    )

    // ------------------------------------------------------------------
    // ① 位图往返端到端：encodeQr → encodeQrBitmap → MLKit → decodeQr
    // ------------------------------------------------------------------

    @Test
    fun qrPayloadSurvivesBitmapRoundTripThroughMlkitAndDecodesFieldByField() {
        val raw = GroupChat.encodeQr(config(), cards)
        assertTrue("载荷不应为空", raw.isNotBlank())

        val bitmap = encodeQrBitmap(raw, 1024, Color.BLACK, Color.WHITE)
        assertEquals("位图宽必须等于请求 size", 1024, bitmap.width)
        assertEquals("位图高必须等于请求 size", 1024, bitmap.height)

        val decoded = decodeQrFromBitmap(bitmap)
        // 位图无损承载了 encodeQr 的那串字节。
        assertEquals("MLKit 解出的字符串必须逐字等于 encodeQr 输出", raw, decoded)

        val payload = GroupChat.decodeSharePayload(decoded)
        assertNotNull("生产解码器必须解出自产载荷", payload)
        requireNotNull(payload)
        assertEquals(GroupChat.QR_KIND, payload.kind)
        assertEquals(GroupChat.SCHEMA_VERSION, payload.schemaVersion)
        assertEquals(GroupChat.MODE_ROUNDTABLE, payload.config.mode)
        assertEquals("r3", payload.config.chairRoleId)
        assertEquals(1234, payload.config.tokenBudgetPerRound)
        // roles 顺序必须逐位保持，不能被排序。
        assertEquals(listOf("r2", "r1", "r3"), payload.config.roles.map { it.id })
        assertEquals(listOf("Bob", "Alice", "Cara"), payload.config.roles.map { it.name })
        assertEquals(listOf(false, false, true), payload.config.roles.map { it.chair })

        // 角色卡逐字段（含 null 不能变字符串 "null"）。
        assertEquals(listOf("r2", "r1", "r3"), payload.cards.map { it.roleId })
        assertEquals(cards, payload.cards)
        val bob = payload.cards[0]
        assertEquals("Bob", bob.name)
        assertEquals("asst-r2", bob.assistantId)
        assertEquals("hot-blooded commentator", bob.persona)
        assertNull("card_id 缺省必须仍是 null，不能被读成字符串 \"null\"", bob.cardId)
        assertNull("avatar_ref 缺省必须仍是 null，不能被读成字符串 \"null\"", bob.avatarRef)

        // 受闸门的导入路径也要认同一串。
        val imported = GroupChat.importShare(decoded)
        assertTrue(
            "自产载荷必须被 importShare 放行，实际：$imported",
            imported is GroupImportResult.Accepted,
        )
        assertEquals(config(), (imported as GroupImportResult.Accepted).payload.config)
        assertEquals(cards, imported.payload.cards)

        writeEvidence("c1-qr-bitmap-roundtrip.json", raw, decoded, bitmap, listOf(1024))
        println("C1-QR-BITMAP-BEGIN")
        println("raw_bytes=${raw.toByteArray(Charsets.UTF_8).size}")
        println("decoded_equals_raw=${raw == decoded}")
        println("C1-QR-BITMAP-END")
    }

    // ------------------------------------------------------------------
    // ①b 正面钉住修复：非 Latin-1（CJK + emoji）位图往返逐字相等
    // ------------------------------------------------------------------

    /**
     * 修复前 zxing `QRCodeWriter.encode` 无 `CHARACTER_SET` 时默认 **ISO-8859-1**（3.5.4 实测），
     * 中文会被替换成 `?`（`{"persona":"热血解说"}` → `{"persona":"????"}`）。生产现已显式传
     * `EncodeHintType.CHARACTER_SET = "UTF-8"`。
     *
     * 本测试是**正面强断言**：夹具同时含 **CJK（基本区）+ 非 BMP emoji**（`🔥` U+1F525、
     * `🀄` U+1F004、`🐉` U+1F409），要求「位图 → MLKit」整串**逐字相等**，并逐字段断言
     * 解回的群配置 / 角色卡。
     *
     * 反空验证：把生产里 `CHARACTER_SET` 那行删掉后，本测试必须真红（`raw != decoded`
     * 且 persona 变 `????`），证明它确实能判别字符集是否生效，而不是恒绿。
     */
    @Test
    fun nonLatin1ContentRoundTripsVerbatimThroughBitmapAfterUtf8Fix() {
        val chinese = "热血解说"
        val roleName = "诸葛亮·卧龙"
        val persona = "冷面军师，运筹帷幄🔥🀄"
        val avatarRef = "头像://青龙/🐉"
        val chineseCards = listOf(
            RoleCardMeta(
                roleId = "r2", name = roleName, assistantId = "asst-r2",
                cardId = null, persona = persona, avatarRef = avatarRef,
            ),
        )
        val raw = GroupChat.encodeQr(config(), chineseCards)
        // 夹具必须确实携带非 Latin-1 字符（含 emoji），否则这条测不到字符集路径。
        assertTrue("夹具里必须出现 CJK", raw.contains(chinese))
        assertTrue("夹具里必须出现非 BMP emoji", raw.contains("🔥"))

        val decoded = decodeQrFromBitmap(encodeQrBitmap(raw, 1024, Color.BLACK, Color.WHITE))

        // 强断言：整串逐字相等（这是修复生效的直接判据，不是「不是全问号」这种弱断言）。
        assertEquals("UTF-8 修复后中文/emoji 位图往返必须逐字相等", raw, decoded)
        assertFalse("修复后不得再出现被替换的 '????'", decoded.contains("????"))

        val payload = GroupChat.decodeSharePayload(decoded)
        assertNotNull("生产解码器必须解出自产载荷", payload)
        requireNotNull(payload)
        assertEquals("群配置必须无损", config(), payload.config)
        // 逐字段：CJK 角色名 / persona / avatar_ref 与 emoji 必须原样存活。
        val card = payload.cards.single()
        assertEquals(roleName, card.name)
        assertEquals(persona, card.persona)
        assertEquals(avatarRef, card.avatarRef)
        assertEquals(chineseCards, payload.cards)

        val imported = GroupChat.importShare(decoded)
        assertTrue("自产中文载荷必须被 importShare 放行，实际：$imported", imported is GroupImportResult.Accepted)
        assertEquals(chineseCards, (imported as GroupImportResult.Accepted).payload.cards)

        println("C1-QR-CHARSET=utf8-verbatim cjk=$chinese emoji_ok=${decoded.contains("🔥")} equal=${raw == decoded}")
    }

    // ------------------------------------------------------------------
    // ② 同一载荷在不同 size 下都能解出同一串
    // ------------------------------------------------------------------

    @Test
    fun samePayloadDecodesIdenticallyAtDifferentSizes() {
        val raw = GroupChat.encodeQr(config(), cards)
        val sizes = listOf(512, 1024, 1536)
        sizes.forEach { size ->
            val bitmap = encodeQrBitmap(raw, size, Color.BLACK, Color.WHITE)
            assertEquals(size, bitmap.width)
            val decoded = decodeQrFromBitmap(bitmap)
            assertEquals("size=$size 下必须解出同一串", raw, decoded)
        }
        writeEvidence("c1-qr-bitmap-roundtrip-sizes.json", raw, raw, null, sizes)
        println("C1-QR-BITMAP-SIZES=${sizes.joinToString()}")
    }

    // ------------------------------------------------------------------
    // ③ 长度边界：更长但真实的载荷仍装得下；超限则由 zxing 明确拒绝
    // ------------------------------------------------------------------

    @Test
    fun largeRealisticPayloadStillFitsWithinQrCapacityAndRoundTrips() {
        // 3 张卡各 550 个 ASCII 字符：总载荷 ~2.7KB，明显长于常规分享但仍落在 v40-L 容量内。
        // 全 ASCII ⇒ UTF-8 字节数 == 字节模式实际编码字节数，故 `bytes <= 2952`（含 ECI）成立。
        val longPersona = "a".repeat(550)
        val bigCards = listOf(
            RoleCardMeta("r2", "Bob", "asst-r2", null, longPersona, null),
            RoleCardMeta("r1", "Alice", "asst-r1", "card-r1", longPersona, "avatar://r1"),
            RoleCardMeta("r3", "Cara", "asst-r3", "card-r3", longPersona, "avatar://r3"),
        )
        val raw = GroupChat.encodeQr(config(), bigCards)
        val bytes = raw.toByteArray(Charsets.UTF_8).size
        assertTrue(
            "长载荷必须仍落在 QR v40-L 容量内（$bytes <= $qrV40LCapacityBytes）",
            bytes <= qrV40LCapacityBytes,
        )

        val bitmap = encodeQrBitmap(raw, 1536, Color.BLACK, Color.WHITE)
        val decoded = decodeQrFromBitmap(bitmap)
        assertEquals("长载荷位图往返必须逐字相等", raw, decoded)
        assertEquals(bigCards, GroupChat.decodeSharePayload(decoded)?.cards)
        println("C1-QR-LARGE-BYTES=$bytes")
    }

    /**
     * 超出 QR 容量的载荷必须由 zxing **明确**拒绝，而不是悄悄截断——这是真实的长度限制，
     * 出现更长载荷时应当失败而不是产出不可扫的位图。
     */
    @Test
    fun oversizedPayloadIsRejectedByEncoderInsteadOfSilentlyTruncated() {
        val hugePersona = "a".repeat(3000)
        val hugeCards = listOf(RoleCardMeta("r2", "Bob", "asst-r2", null, hugePersona, null))
        val raw = GroupChat.encodeQr(config(), hugeCards)
        val bytes = raw.toByteArray(Charsets.UTF_8).size
        assertTrue("夹具必须确实超限（$bytes > $qrV40LCapacityBytes）", bytes > qrV40LCapacityBytes)

        val thrown = runCatching {
            encodeQrBitmap(raw, 1024, Color.BLACK, Color.WHITE)
        }.exceptionOrNull()
        assertNotNull("超限载荷必须抛异常，不能静默截断", thrown)
        assertTrue("超限必须由 zxing 的 WriterException 拒绝，实际=$thrown", thrown is WriterException)
    }

    // ------------------------------------------------------------------
    // ④ FileProvider URI 真实可读 + Intent 形状
    // ------------------------------------------------------------------

    @Test
    fun exportTempFileUriIsReadableAndItsBytesEqualProductionJsonl() {
        val nodes = listOf(
            MessageNode.of(UIMessage.user("@Bob 开始")),
            MessageNode.of(UIMessage.assistant("Bob：到位").copy(roleId = "r2")),
        )
        val expected = TavernChatCodec.exportGroupJsonl(
            nodes = nodes,
            config = config(),
            cards = cards,
            userName = "阿达",
            groupName = "C1 位图往返群",
            createDate = null,
        )
        val expectedBytes = expected.toByteArray(Charsets.UTF_8)
        assertTrue("JSONL 夹具不应为空", expectedBytes.isNotEmpty())

        val uri = writeExportTempFile(context, "c1-qr-bitmap-roundtrip.jsonl") { it.write(expectedBytes) }
        assertTrue("必须拿到 content:// FileProvider URI，实际=$uri", uri.scheme == "content")
        assertEquals(
            "authority 必须是包名派生的 FileProvider",
            "${context.packageName}.fileprovider",
            uri.authority,
        )

        // 真读：用 targetContext 的 contentResolver 打开生产 URI。
        val read = context.contentResolver.openInputStream(uri).use { stream ->
            assertNotNull("FileProvider URI 必须能打开输入流：$uri", stream)
            stream!!.readBytes()
        }
        assertTrue("通过 FileProvider 读回的字节必须逐字节等于生产 JSONL", read.contentEquals(expectedBytes))
        assertEquals(expectedBytes.size, read.size)

        // Intent 形状（不 startActivity，避免弹系统分享面板）。
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = GROUP_EXPORT_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/json", intent.type)
        assertEquals("MIME 必须与生产常量一致", GROUP_EXPORT_MIME_TYPE, intent.type)
        val streamExtra: Uri? = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        assertEquals("EXTRA_STREAM 必须带上同一个 URI", uri, streamExtra)
        assertTrue(
            "必须带 FLAG_GRANT_READ_URI_PERMISSION，否则接收方无读权限",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )

        // 真机文件内容与读回一致。
        val onDisk = File(context.appTempFolder, "c1-qr-bitmap-roundtrip.jsonl")
        assertTrue("生产临时文件必须落盘", onDisk.exists())
        assertTrue("磁盘字节必须与生产 JSONL 相等", onDisk.readBytes().contentEquals(expectedBytes))
        println("C1-QR-FILEPROVIDER uri=$uri bytes=${read.size} mime=${intent.type}")
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** MLKit 从生产位图解码。带超时上限，避免设备上挂死。 */
    private fun decodeQrFromBitmap(bitmap: Bitmap): String {
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val barcodes = Tasks.await(scanner.process(image), 30, TimeUnit.SECONDS)
            val decoded = barcodes.firstNotNullOfOrNull { it.rawValue }
            assertNotNull("MLKit 未能从生产位图解出任何二维码", decoded)
            return decoded!!
        } finally {
            scanner.close()
        }
    }

    private fun writeEvidence(
        name: String,
        raw: String,
        decoded: String,
        bitmap: Bitmap?,
        sizes: List<Int>,
    ) {
        val externalDir = context.getExternalFilesDir(null)
        assertNotNull("external files dir 必须可用", externalDir)
        val dir = File(externalDir!!, "c1-qr-bitmap")
        assertTrue("证据目录建不出来：$dir", dir.mkdirs() || dir.isDirectory)
        val report = buildJsonObject {
            put("case", "C1-09 qr bitmap roundtrip")
            put("raw_bytes", raw.toByteArray(Charsets.UTF_8).size)
            put("decoded_equals_raw", raw == decoded)
            putJsonArray("sizes") { sizes.forEach { add(JsonPrimitive(it)) } }
            bitmap?.let {
                putJsonObject("bitmap") {
                    put("width", it.width)
                    put("height", it.height)
                    put("argb_8888", it.config == Bitmap.Config.ARGB_8888)
                }
            }
            put("raw_text", raw)
            put("decoded_text", decoded)
        }
        val file = File(dir, name)
        file.writeText(prettyJson.encodeToString(JsonElement.serializer(), report))
    }
}
