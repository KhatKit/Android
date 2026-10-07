package heizige.kk.khatkit.app.core.ui.components.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.media.Image
import android.media.ImageReader
import android.media.ImageWriter
import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.TagBundle
import androidx.camera.core.impl.utils.ExifData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.common.Barcode
import heizige.kk.khatkit.app.core.data.model.GroupChat
import heizige.kk.khatkit.app.core.data.model.GroupConfig
import heizige.kk.khatkit.app.core.data.model.GroupImportResult
import heizige.kk.khatkit.app.core.data.model.GroupRole
import heizige.kk.khatkit.app.core.data.model.RoleCardMeta
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * C1-09「**真实相机扫码链路**」的真机端到端闭环（不需要摄像头硬件）。
 *
 * ## 未被覆盖的那一层
 *
 * `QrScannerSheet.analyzeFrame` 的解码入口是
 * `InputImage.fromMediaImage(mediaImage, imageInfo.rotationDegrees)`，其中 `mediaImage` 来自
 * `ImageProxy.getImage()` —— 也就是底层 `android.media.Image`。此前 `C1GroupQrBitmapRoundTripDeviceTest`
 * 只验过 `InputImage.fromBitmap`，**绕过了 mediaImage 这一层**；本类补上这一段：
 *
 * 1. 用 `ImageReader`（`ImageFormat.JPEG`）+ `ImageWriter` 造一个**真实的** `android.media.Image`，
 *    里面装的是生产 [encodeQrBitmap] 产出的二维码位图压缩成的 JPEG；
 * 2. 把它包成 [ImageProxy]（[SyntheticImageProxy]，逐字段转发底层 `Image`），rotation 由测试指定；
 * 3. 调用生产 seam [processQrFrame]（内部就是 `InputImage.fromMediaImage` + `scanner.process`），
 *    scanner 由生产 [buildQrScanner]（`FORMAT_QR_CODE`）构造。
 *
 * ## 与生产的关系
 *
 * [processQrFrame] / [buildQrScanner] / [firstQrValue] 都是从生产 [QrScannerSheet] 里**原样提取**的
 * seam（见各自 KDoc）。生产 `analyzeFrame` 现在直接调用它们，所以本类跑通的正是生产同一条代码路径。
 *
 * ## 不证明什么
 *
 * - 不接摄像头：帧是合成的（ImageReader 注入），不走 CameraX 取景，也不覆盖 `Preview`/`bindToLifecycle`。
 * - `SyntheticImageProxy` 不是 CameraX 的 `AndroidImageProxy`（后者是包内私有），而是逐字段转发真实
 *   `android.media.Image` 的最小适配器；`getImage()` 返回的是**真实**的 media Image，所以
 *   mediaImage → MLKit 这一层是真的。
 */
@RunWith(AndroidJUnit4::class)
class C1GroupQrCameraPathDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val prettyJson = Json { prettyPrint = true }

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

    // ==================================================================
    // ① 主链：encodeQr → encodeQrBitmap → 合成 mediaImage → MLKit → 解码
    // ==================================================================

    @Test
    fun payloadDecodedFromRealMediaImageEqualsEncodeQrVerbatim() {
        val raw = GroupChat.encodeQr(config(), cards)
        val bitmap = encodeQrBitmap(raw, 1024, Color.BLACK, Color.WHITE)

        syntheticImageProxy(bitmap, rotationDegrees = 0).use { proxy ->
            assertEquals("合成帧必须是真实 JPEG mediaImage", ImageFormat.JPEG, proxy.format)
            val decoded = decodeStrings(proxy)
            assertEquals("一帧应恰好解出一个二维码", 1, decoded.size)
            assertEquals(
                "MLKit 从真实 mediaImage 解出的字符串必须逐字等于 encodeQr 输出",
                raw,
                decoded.single(),
            )
            assertPayloadFields(decoded.single())
            writeEvidence(
                "c1-qr-camera-roundtrip.json",
                buildJsonObject {
                    put("case", "C1-09 qr camera path roundtrip")
                    put("format", "JPEG")
                    put("rotation_degrees", 0)
                    put("decoded_count", decoded.size)
                    put("decoded_equals_raw", raw == decoded.single())
                    put("raw_text", raw)
                    put("decoded_text", decoded.single())
                },
            )
        }
        println("C1-QR-CAMERA=roundtrip equal=${true} bytes=${raw.toByteArray(Charsets.UTF_8).size}")
    }

    // ==================================================================
    // ② 非 Latin-1（CJK + emoji）也必须逐字往返 —— 钉住刚修好的 UTF-8 缺陷
    // ==================================================================

    @Test
    fun nonLatin1PayloadDecodedFromRealMediaImageRoundTripsVerbatim() {
        val chinese = "热血解说"
        val roleName = "$chinese·卧龙"
        val persona = "冷面军师，运筹帷幄🔥🀄"
        val avatarRef = "头像://青龙/🐉"
        val chineseCards = listOf(
            RoleCardMeta(
                roleId = "r2", name = roleName, assistantId = "asst-r2",
                cardId = null, persona = persona, avatarRef = avatarRef,
            ),
        )
        val raw = GroupChat.encodeQr(config(), chineseCards)
        assertTrue("夹具里必须出现 CJK", raw.contains(chinese))
        assertTrue("夹具里必须出现非 BMP emoji", raw.contains("🔥"))

        val bitmap = encodeQrBitmap(raw, 1024, Color.BLACK, Color.WHITE)
        syntheticImageProxy(bitmap, rotationDegrees = 0).use { proxy ->
            val decoded = decodeStrings(proxy).singleOrNull()
            assertEquals("UTF-8 修复后中文/emoji 经真实 mediaImage 往返必须逐字相等", raw, decoded)
            assertFalse("修复后不得再出现被替换的 '????'", decoded!!.contains("????"))

            val payload = GroupChat.decodeSharePayload(decoded)
            assertNotNull("生产解码器必须解出自产载荷", payload)
            requireNotNull(payload)
            assertEquals("群配置必须无损", config(), payload.config)
            val card = payload.cards.single()
            assertEquals(roleName, card.name)
            assertEquals(persona, card.persona)
            assertEquals(avatarRef, card.avatarRef)
            assertEquals(chineseCards, payload.cards)

            val imported = GroupChat.importShare(decoded)
            assertTrue(
                "自产中文载荷必须被 importShare 放行，实际：$imported",
                imported is GroupImportResult.Accepted,
            )
            assertEquals(chineseCards, (imported as GroupImportResult.Accepted).payload.cards)
        }
        println("C1-QR-CAMERA-CHARSET=utf8-verbatim cjk=$chinese emoji_ok=${raw.contains("🔥")}")
    }

    // ==================================================================
    // ③ rotationDegrees：真实 mediaImage 的旋转参数确实被 MLKit 消费
    // ==================================================================

    @Test
    fun rotationDegreesIsAppliedToRealMediaImageFrames() {
        val raw = GroupChat.encodeQr(config(), cards)
        val upright = encodeQrBitmap(raw, 1024, Color.BLACK, Color.WHITE)

        // 正立帧 + rotation 0 → 精确解出（基线）
        syntheticImageProxy(upright, rotationDegrees = 0).use { proxy ->
            assertEquals("正立帧 rotation=0 必须解出原串", raw, decodeStrings(proxy).singleOrNull())
        }

        // 位图预旋转 90°，rotation=90 → MLKit 应把它旋回正立后解出原串。
        val rotated = rotateBitmap(upright, 90)
        syntheticImageProxy(rotated, rotationDegrees = 90).use { proxy ->
            assertEquals(
                "预旋转 90° 的帧配 rotation=90 必须旋回正立并解出原串",
                raw,
                decodeStrings(proxy).singleOrNull(),
            )
        }

        // 同一个预旋转帧若 rotation 传 0（不纠正方向），不得再解出**同一串**。
        syntheticImageProxy(rotated, rotationDegrees = 0).use { proxy ->
            assertFalse(
                "rotation=0 未纠正 90° 旋转，不应再解出原串",
                decodeStrings(proxy).contains(raw),
            )
        }

        println("C1-QR-CAMERA-ROTATION=0-and-90-ok")
    }

    // ==================================================================
    // ④ 无二维码的帧 → 空列表，不抛异常
    // ==================================================================

    @Test
    fun frameWithoutQrReturnsEmptyListWithoutThrowing() {
        val blank = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        syntheticImageProxy(blank, rotationDegrees = 0).use { proxy ->
            val decoded = decodeStrings(proxy)
            assertTrue("无二维码帧必须解出空列表，实际=$decoded", decoded.isEmpty())
        }
        println("C1-QR-CAMERA-EMPTY=ok")
    }

    // ==================================================================
    // ⑤ 一帧多个二维码 → 每次 decode 返回全部，analyzeFrame 只取第一个 rawValue
    // ==================================================================

    @Test
    fun multipleQrCodesInOneFrameYieldFirstValueLikeAnalyzeFrame() {
        val rawA = GroupChat.encodeQr(config(), cards)
        val rawB = GroupChat.encodeQr(config().copy(tokenBudgetPerRound = 4321), cards)
        assertFalse("两个载荷必须不同", rawA == rawB)

        val a = encodeQrBitmap(rawA, 480, Color.BLACK, Color.WHITE)
        val b = encodeQrBitmap(rawB, 480, Color.BLACK, Color.WHITE)
        val canvasBitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            val canvas = Canvas(this)
            canvas.drawBitmap(a, 16f, 16f, null)
            canvas.drawBitmap(b, 528f, 16f, null)
        }

        syntheticImageProxy(canvasBitmap, rotationDegrees = 0).use { proxy ->
            val barcodes = decodeBarcodes(proxy)
            val values = barcodes.mapNotNull { it.rawValue }
            assertTrue("一帧至少应解出一个二维码，实际=$values", values.isNotEmpty())
            assertTrue(
                "解出的每一个 rawValue 都必须是两个夹具之一，实际=$values",
                values.all { it == rawA || it == rawB },
            )
            // analyzeFrame 原语义：只回调第一个非空 rawValue。
            val first = firstQrValue(barcodes)
            assertNotNull("firstQrValue 不应为 null", first)
            assertEquals(
                "firstQrValue 必须等于列表中第一个非空 rawValue",
                barcodes.firstNotNullOfOrNull { it.rawValue },
                first,
            )
            assertTrue("firstQrValue 必须是两个夹具之一", first == rawA || first == rawB)
            writeEvidence(
                "c1-qr-camera-multi.json",
                buildJsonObject {
                    put("case", "C1-09 qr camera path multiple codes")
                    putJsonArray("decoded_values") { values.forEach { add(JsonPrimitive(it)) } }
                    put("first_value_is_a", first == rawA)
                    put("first_value_is_b", first == rawB)
                },
            )
            println("C1-QR-CAMERA-MULTI count=${values.size} first_is_a=${first == rawA}")
        }
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** 解出全部 barcode（生产 scanner + 生产 seam）。 */
    @androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
    private fun decodeBarcodes(proxy: ImageProxy): List<Barcode> {
        val scanner = buildQrScanner()
        try {
            val task = processQrFrame(proxy, scanner)
            assertNotNull("processQrFrame 返回 null（proxy.image 为空？）", task)
            return Tasks.await(task!!, 30, TimeUnit.SECONDS)
        } finally {
            scanner.close()
        }
    }

    private fun decodeStrings(proxy: ImageProxy): List<String> =
        decodeBarcodes(proxy).mapNotNull { it.rawValue }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * 造一个**真实的** `ImageProxy`：用 `ImageReader`(`JPEG`) + `ImageWriter` 把 [bitmap] 的 JPEG
     * 字节写进底层 `android.media.Image`，再用 [SyntheticImageProxy] 转发出来。
     */
    private fun syntheticImageProxy(
        bitmap: Bitmap,
        rotationDegrees: Int,
        timestamp: Long = 1_000L,
    ): SyntheticImageProxy {
        val jpeg = ByteArrayOutputStream().use { out ->
            assertTrue("位图 JPEG 编码失败", bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out))
            out.toByteArray()
        }
        val reader = ImageReader.newInstance(bitmap.width, bitmap.height, ImageFormat.JPEG, 2)
        val writer = ImageWriter.newInstance(reader.surface, 2)
        try {
            val input = writer.dequeueInputImage()
            val buffer = input.planes[0].buffer
            assertTrue(
                "JPEG 字节(${jpeg.size}) 超过合成帧 plane 容量(${buffer.capacity()})",
                jpeg.size <= buffer.capacity(),
            )
            buffer.rewind()
            buffer.put(jpeg)
            buffer.rewind()
            writer.queueInputImage(input)
        } finally {
            writer.close()
        }

        val deadline = SystemClock.elapsedRealtime() + 5_000
        var image: Image? = null
        while (image == null && SystemClock.elapsedRealtime() < deadline) {
            image = reader.acquireLatestImage()
            if (image == null) Thread.sleep(10)
        }
        assertNotNull("ImageReader 未产出合成帧", image)
        return SyntheticImageProxy(image!!, rotationDegrees, timestamp, reader)
    }

    private fun assertPayloadFields(decoded: String) {
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
    }

    private fun writeEvidence(name: String, report: JsonElement) {
        val externalDir = context.getExternalFilesDir(null)
        assertNotNull("external files dir 必须可用", externalDir)
        val dir = File(externalDir!!, "c1-qr-camera")
        assertTrue("证据目录建不出来：$dir", dir.mkdirs() || dir.isDirectory)
        File(dir, name).writeText(prettyJson.encodeToString(JsonElement.serializer(), report))
    }
}

/**
 * 最小的真实 [ImageProxy] 适配器：逐字段转发底层 `android.media.Image`。
 *
 * CameraX 自己的 `AndroidImageProxy` 是**包内私有**，androidTest 拿不到；这里不伪造任何像素，
 * 只把 ImageReader 产出的真实 `Image` 按 `ImageProxy` 接口暴露出来。`getImage()` 返回的就是那个
 * 真实 media Image，因此 `InputImage.fromMediaImage` 走的是与生产完全相同的转换。
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
private class SyntheticImageProxy(
    private val image: Image,
    private val rotationDegrees: Int,
    private val timestamp: Long,
    private val reader: ImageReader,
) : ImageProxy {

    private val planeProxies: Array<ImageProxy.PlaneProxy> =
        image.planes.map { SyntheticPlaneProxy(it) }.toTypedArray()

    private val imageInfo: ImageInfo = object : ImageInfo {
        override fun getTagBundle(): TagBundle = TagBundle.emptyBundle()
        override fun getTimestamp(): Long = timestamp
        override fun getRotationDegrees(): Int = rotationDegrees
        override fun populateExifData(exifBuilder: ExifData.Builder) = Unit
    }

    override fun close() {
        image.close()
        reader.close()
    }

    override fun getCropRect(): Rect = image.cropRect

    override fun setCropRect(rect: Rect?) {
        if (rect != null) image.cropRect = rect
    }

    override fun getFormat(): Int = image.format

    override fun getHeight(): Int = image.height

    override fun getWidth(): Int = image.width

    override fun getPlanes(): Array<ImageProxy.PlaneProxy> = planeProxies

    override fun getImageInfo(): ImageInfo = imageInfo

    override fun getImage(): Image = image
}

private class SyntheticPlaneProxy(private val plane: Image.Plane) : ImageProxy.PlaneProxy {
    override fun getRowStride(): Int = plane.rowStride
    override fun getPixelStride(): Int = plane.pixelStride
    override fun getBuffer() = plane.buffer
}
