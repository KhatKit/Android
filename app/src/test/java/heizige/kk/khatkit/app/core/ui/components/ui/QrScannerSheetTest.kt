package heizige.kk.khatkit.app.core.ui.components.ui

import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [QrScannerSheet] 的纯逻辑单测。
 *
 * CameraX 与 MLKit 都要求真机，JVM 侧跑不了，因此把「入口决策」与「结果一次性闸门」
 * 两块纯逻辑抽出来在这里覆盖。
 */
class QrScannerSheetTest {

    // ---------- ScanGate：结果只放行一次 ----------

    @Test
    fun `first accept should win and later ones should be rejected`() {
        val gate = ScanGate()

        assertTrue("首次调用应放行", gate.tryAccept())
        repeat(20) {
            assertFalse("放行名额只能被消费一次", gate.tryAccept())
        }
    }

    @Test
    fun `gate should report accepted state`() {
        val gate = ScanGate()
        assertFalse(gate.isAccepted)

        gate.tryAccept()

        assertTrue(gate.isAccepted)
    }

    @Test
    fun `gate should become reusable after reset`() {
        val gate = ScanGate()
        assertTrue(gate.tryAccept())
        assertFalse(gate.tryAccept())

        gate.reset()

        assertFalse(gate.isAccepted)
        assertTrue("复位后应重新放行一次", gate.tryAccept())
    }

    @Test
    fun `concurrent accepts should still yield exactly one winner`() {
        val gate = ScanGate()
        val winners = java.util.concurrent.atomic.AtomicInteger(0)
        val threads = (1..16).map {
            Thread {
                if (gate.tryAccept()) winners.incrementAndGet()
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals("无论多少线程并发抢，只应有一个赢家", 1, winners.get())
    }

    // ---------- decideScanEntry：设备能力 + 权限状态 ----------

    @Test
    fun `granted permission with camera should scan`() {
        assertEquals(
            ScanEntryDecision.Scan,
            decideScanEntry(PermissionStatus.Granted, hasCameraHardware = true)
        )
    }

    @Test
    fun `not requested permission should ask first`() {
        assertEquals(
            ScanEntryDecision.RequestPermission,
            decideScanEntry(PermissionStatus.NotRequested, hasCameraHardware = true)
        )
    }

    @Test
    fun `unknown permission status should ask first`() {
        assertEquals(
            ScanEntryDecision.RequestPermission,
            decideScanEntry(permissionStatus = null, hasCameraHardware = true)
        )
    }

    @Test
    fun `denied permission should fall back`() {
        assertEquals(
            ScanEntryDecision.Fallback,
            decideScanEntry(PermissionStatus.Denied, hasCameraHardware = true)
        )
    }

    @Test
    fun `permanently denied permission should fall back`() {
        assertEquals(
            ScanEntryDecision.Fallback,
            decideScanEntry(PermissionStatus.DeniedPermanently, hasCameraHardware = true)
        )
    }

    @Test
    fun `missing camera hardware should fall back regardless of permission`() {
        // 无相机时即便已授权也只能回落 —— 授权不改变硬件缺失的事实。
        // 同时保证此时不会再弹权限框（apply 分支已在 hasCameraHardware 处短路）。
        PermissionStatus.entries.forEach { status ->
            assertEquals(
                "权限状态 $status 下无相机都应回落",
                ScanEntryDecision.Fallback,
                decideScanEntry(status, hasCameraHardware = false)
            )
        }
        assertEquals(
            ScanEntryDecision.Fallback,
            decideScanEntry(permissionStatus = null, hasCameraHardware = false)
        )
    }

    @Test
    fun `only granted permission with camera may open viewfinder`() {
        // 穷举全部组合，锁定「能取景」的唯一条件，防止后续改动放宽了权限门槛。
        PermissionStatus.entries.forEach { status ->
            listOf(true, false).forEach { hasCamera ->
                val decision = decideScanEntry(status, hasCamera)
                val canScan = decision == ScanEntryDecision.Scan
                assertEquals(
                    "权限 $status / 有相机=$hasCamera 的决策应为 $canScan",
                    status == PermissionStatus.Granted && hasCamera,
                    canScan
                )
            }
        }
    }

    // ---------- shouldAnalyzeFrame：过期帧丢弃，且时间戳缺失时必须放行 ----------

    @Test
    fun `newer timestamp should be analyzed`() {
        assertTrue(shouldAnalyzeFrame(lastAnalyzedTimestamp = 100L, frameTimestamp = 200L))
    }

    @Test
    fun `same or older timestamp should be dropped`() {
        assertFalse("时间戳相同说明是重复帧", shouldAnalyzeFrame(100L, 100L))
        assertFalse("时间戳更早说明是乱序到达的旧 buffer", shouldAnalyzeFrame(200L, 100L))
    }

    @Test
    fun `zero timestamp must always be analyzed`() {
        // 这是本包修掉的一个真实缺陷：CameraX 的 ImageAnalysis 时间戳来自
        // android.media.Image.getTimestamp()，平台不保证它被赋值。一旦恒为 0，
        // 原来的 `timestamp <= last` 判定会把每一帧都当成过期帧丢掉 —— 取景能开但
        // 永远扫不出东西。首帧 last=0 的场景尤其致命。
        assertTrue(shouldAnalyzeFrame(lastAnalyzedTimestamp = 0L, frameTimestamp = 0L))
        assertTrue(shouldAnalyzeFrame(lastAnalyzedTimestamp = 999L, frameTimestamp = 0L))
        // 负数时间戳同样按「不可用」处理，不得丢帧。
        assertTrue(shouldAnalyzeFrame(lastAnalyzedTimestamp = 0L, frameTimestamp = -1L))
    }

    @Test
    fun `invalid timestamps must not poison the monotonic baseline`() {
        // 模拟 analyzeFrame 的推进方式：不可用的时间戳不写入 lastAnalyzedTimestamp，
        // 因此后续真实时间戳仍能被正常比较。
        var last = 0L
        listOf(0L, 0L, 500L, 0L, 400L, 600L).forEach { ts ->
            if (!shouldAnalyzeFrame(last, ts)) return@forEach
            if (ts > 0L) last = ts
        }
        assertEquals("最后一次有效时间戳应为 600", 600L, last)
    }

    @Test
    fun `strictly increasing real timestamps should never be dropped`() {
        // 真机正常路径：单调递增的时间戳序列一帧都不能丢，否则会出现「扫不出码」。
        var last = 0L
        (1L..500L).forEach { ts ->
            assertTrue("第 $ts 帧不应被丢弃", shouldAnalyzeFrame(last, ts))
            last = ts
        }
    }
}