package heizige.kk.khatkit.trigger

import heizige.kk.khatkit.card.CardManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
class TriggerEngineTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun argsOf(event: Map<String, Any?>): Map<*, Any?> =
        event.getValue(TriggerEngine.ARG_EVENT) as Map<*, Any?>

    private class Recorder {
        val runs = mutableListOf<Pair<String, Map<String, Any?>>>()
        val runner = TriggerRunner { name, args -> runs.add(name to args) }
    }

    private fun engine(recorder: Recorder, now: Long): TriggerEngine =
        TriggerEngine(runner = recorder.runner, clock = { now })

    @Test
    fun scheduleTimeFiresOncePerMinute() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 18, 8, 0))
        engine.updateCards(
            listOf(
                TriggerCard(
                    "morning_card",
                    listOf(CardManifest.Event(type = "schedule", times = listOf("08:00", "21:30"))),
                )
            )
        )

        assertEquals(1, engine.tickSchedule(zone = zone))
        assertEquals(0, engine.tickSchedule(zone = zone))
        assertEquals(0, engine.tickSchedule(now = at(2026, 9, 18, 8, 1), zone = zone))
        assertEquals(1, engine.tickSchedule(now = at(2026, 9, 18, 21, 30), zone = zone))
        assertEquals(2, recorder.runs.size)
        assertEquals("morning_card", recorder.runs[0].first)
        assertEquals("schedule", argsOf(recorder.runs[0].second)["type"])
        assertEquals("08:00", argsOf(recorder.runs[0].second)["time"])
    }

    @Test
    fun scheduleRespectsDays() {
        val recorder = Recorder()
        // 2026-09-18 是周五（ISO 5）
        val engine = engine(recorder, at(2026, 9, 18, 8, 0))
        engine.updateCards(
            listOf(
                TriggerCard(
                    "weekday_card",
                    listOf(CardManifest.Event(type = "schedule", times = listOf("08:00"), days = listOf(1, 2, 3, 4, 5))),
                )
            )
        )
        assertEquals(1, engine.tickSchedule(zone = zone))

        // 周六同一时间不触发
        assertEquals(0, engine.tickSchedule(now = at(2026, 9, 19, 8, 0), zone = zone))
    }

    @Test
    fun scheduleIntervalWaitsBetweenRuns() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 18, 8, 0))
        engine.updateCards(
            listOf(
                TriggerCard(
                    "interval_card",
                    listOf(CardManifest.Event(type = "schedule", intervalMinutes = 10)),
                )
            )
        )

        assertEquals(0, engine.tickSchedule(zone = zone))
        assertEquals(0, engine.tickSchedule(now = at(2026, 9, 18, 8, 5), zone = zone))
        assertEquals(1, engine.tickSchedule(now = at(2026, 9, 18, 8, 10), zone = zone))
        assertEquals(0, engine.tickSchedule(now = at(2026, 9, 18, 8, 15), zone = zone))
        assertEquals(1, engine.tickSchedule(now = at(2026, 9, 18, 8, 20), zone = zone))
    }

    @Test
    fun notificationMatchesAndCoolsDown() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 18, 8, 0))
        engine.updateCards(
            listOf(
                TriggerCard(
                    "sms_card",
                    listOf(
                        CardManifest.Event(
                            type = "notification",
                            packageName = "com.example.sms",
                            titleContains = "验证码",
                        )
                    ),
                )
            )
        )

        val now = at(2026, 9, 18, 8, 0)
        assertEquals(1, engine.onNotification("com.example.sms", "您的验证码", "123456", now))
        // 冷却期内重复通知被抑制
        assertEquals(0, engine.onNotification("com.example.sms", "您的验证码", "654321", now + 1_000))
        // 超过 3 秒后可再次触发
        assertEquals(1, engine.onNotification("com.example.sms", "您的验证码", "777777", now + 3_000))
        // 包名不符不触发
        assertEquals(0, engine.onNotification("com.other.app", "您的验证码", "1", now + 10_000))

        assertEquals(2, recorder.runs.size)
        val event = argsOf(recorder.runs[0].second)
        assertEquals("notification", event["type"])
        assertEquals("com.example.sms", event["package"])
        assertEquals("您的验证码", event["title"])
        assertEquals("123456", event["text"])
    }

    @Test
    fun appLaunchCoolsDownForOneMinute() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 18, 8, 0))
        engine.updateCards(
            listOf(
                TriggerCard(
                    "app_card",
                    listOf(CardManifest.Event(type = "app_launch", packageName = "com.example.app")),
                )
            )
        )

        val now = at(2026, 9, 18, 8, 0)
        assertEquals(1, engine.onAppLaunch("com.example.app", now))
        assertEquals(0, engine.onAppLaunch("com.example.app", now + 30_000))
        assertEquals(1, engine.onAppLaunch("com.example.app", now + 60_000))
        assertEquals("app_launch", argsOf(recorder.runs[0].second)["type"])
    }

    @Test
    fun appLaunchEmptyPackageMatchesAnyApp() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 18, 8, 0))
        engine.updateCards(
            listOf(TriggerCard("any_card", listOf(CardManifest.Event(type = "app_launch"))))
        )
        assertEquals(1, engine.onAppLaunch("com.example.any", at(2026, 9, 18, 8, 0)))
        assertEquals("com.example.any", argsOf(recorder.runs[0].second)["package"])
    }

    @Test
    fun appExitMatchesExactPackageOnly() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 18, 8, 0))
        engine.updateCards(
            listOf(
                TriggerCard(
                    "exit_card",
                    listOf(CardManifest.Event(type = "app_exit", packageName = "com.example.app")),
                )
            )
        )

        val now = at(2026, 9, 18, 8, 0)
        // 未声明该包名 / 空包名不触发
        assertEquals(0, engine.onAppExit("com.other.app", now))
        assertEquals(0, engine.onAppExit("", now))
        assertEquals(1, engine.onAppExit("com.example.app", now))
        // 冷却期内不重复
        assertEquals(0, engine.onAppExit("com.example.app", now + 1_000))
        // 超过 15s 冷却可再次触发
        assertEquals(1, engine.onAppExit("com.example.app", now + 15_000))
        assertEquals("app_exit", argsOf(recorder.runs[0].second)["type"])
        assertEquals("com.example.app", argsOf(recorder.runs[0].second)["package"])
    }

    @Test
    fun disabledEventIndexIsSkipped() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 18, 8, 0))
        engine.updateCards(
            listOf(
                TriggerCard(
                    "mixed_card",
                    listOf(
                        CardManifest.Event(type = "charging", state = "connected"),
                        CardManifest.Event(type = "network", state = "offline"),
                        CardManifest.Event(type = "schedule", times = listOf("08:00")),
                    ),
                    disabledIndexes = setOf(0, 2),
                )
            )
        )

        val now = at(2026, 9, 18, 8, 0)
        // 被停用的充电与定时事件不派发
        assertEquals(0, engine.onCharging("connected", now))
        assertEquals(0, engine.tickSchedule(now = now, zone = zone))
        // 未停用的网络事件正常派发
        assertEquals(1, engine.onNetwork(false, now))
        assertEquals("network", argsOf(recorder.runs[0].second)["type"])
        assertEquals(1, recorder.runs.size)
    }

    @Test
    fun chargingMatchesState() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 18, 8, 0))
        engine.updateCards(
            listOf(
                TriggerCard(
                    "charge_card",
                    listOf(CardManifest.Event(type = "charging", state = "connected")),
                )
            )
        )

        val now = at(2026, 9, 18, 8, 0)
        assertEquals(0, engine.onCharging("disconnected", now))
        assertEquals(1, engine.onCharging("connected", now))
        assertEquals(0, engine.onCharging("connected", now + 1_000))
        assertEquals(1, engine.onCharging("connected", now + 5_000))
        assertEquals("charging", argsOf(recorder.runs[0].second)["type"])
        assertEquals("connected", argsOf(recorder.runs[0].second)["state"])
    }

    @Test
    fun wifiMatchesSsidAndOptionalState() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 18, 8, 0))
        engine.updateCards(
            listOf(
                TriggerCard("wifi_home", listOf(CardManifest.Event(type = "wifi", ssid = "Home", state = "connected"))),
                TriggerCard("wifi_any", listOf(CardManifest.Event(type = "wifi"))),
            )
        )

        val now = at(2026, 9, 18, 8, 0)
        // "Office" 只命中任意热点卡片
        assertEquals(1, engine.onWifi("Office", true, now))
        assertEquals("wifi_any", recorder.runs[0].first)
        // 同刻 "home" 命中指定 SSID，任意卡片仍在冷却
        assertEquals(1, engine.onWifi("home", true, now))
        assertEquals("wifi_home", recorder.runs[1].first)
        assertEquals("home", argsOf(recorder.runs[1].second)["ssid"])
        // 冷却 15s 后连接事件两卡都命中
        assertEquals(2, engine.onWifi("home", true, now + 15_000))
    }

    @Test
    fun networkMatchesOnlineOffline() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 18, 8, 0))
        engine.updateCards(
            listOf(TriggerCard("net_card", listOf(CardManifest.Event(type = "network", state = "offline"))))
        )
        val now = at(2026, 9, 18, 8, 0)
        assertEquals(0, engine.onNetwork(true, now))
        assertEquals(1, engine.onNetwork(false, now))
        assertEquals("offline", argsOf(recorder.runs[0].second)["state"])
    }

    @Test
    fun batteryMatchesThresholdAndState() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 18, 8, 0))
        engine.updateCards(
            listOf(
                TriggerCard(
                    "battery_card",
                    listOf(
                        CardManifest.Event(type = "battery", levelBelow = 20),
                        CardManifest.Event(type = "battery", levelAbove = 80, state = "charging"),
                    ),
                )
            )
        )

        val now = at(2026, 9, 18, 8, 0)
        assertEquals(0, engine.onBattery(50, false, now))
        assertEquals(1, engine.onBattery(15, false, now))
        // 冷却期内低电量重复上报被抑制
        assertEquals(0, engine.onBattery(10, false, now + 1_000))
        // 高电量 + 充电条件：60s 冷却后
        assertEquals(1, engine.onBattery(85, true, now + 60_000))
        // 高电量但不充电：不匹配 state
        assertEquals(0, engine.onBattery(90, false, now + 120_000))
    }

    @Test
    fun screenClipboardBluetoothMatch() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 18, 8, 0))
        engine.updateCards(
            listOf(
                TriggerCard("screen_card", listOf(CardManifest.Event(type = "screen", state = "unlocked"))),
                TriggerCard("clip_card", listOf(CardManifest.Event(type = "clipboard", textContains = "https://"))),
                TriggerCard(
                    "bt_card",
                    listOf(CardManifest.Event(type = "bluetooth", state = "connected", device = "耳机")),
                ),
            )
        )

        val now = at(2026, 9, 18, 8, 0)
        assertEquals(0, engine.onScreen("on", now))
        assertEquals(1, engine.onScreen("unlocked", now))
        assertEquals("unlocked", argsOf(recorder.runs[0].second)["state"])

        assertEquals(0, engine.onClipboard("普通文本", now))
        assertEquals(1, engine.onClipboard("打开 HTTPS://example.com", now))

        assertEquals(0, engine.onBluetooth("connected", "键盘", now))
        assertEquals(1, engine.onBluetooth("connected", "蓝牙耳机", now))
        assertEquals("connected", argsOf(recorder.runs[2].second)["state"])
    }

    @Test
    fun locationFiresOnlyOnBoundaryCrossing() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 18, 8, 0))
        engine.updateCards(
            listOf(
                TriggerCard(
                    "geo_card",
                    listOf(
                        CardManifest.Event(
                            type = "location",
                            state = "enter",
                            lat = 31.2304,
                            lon = 121.4737,
                            radiusM = 500,
                        ),
                        CardManifest.Event(
                            type = "location",
                            state = "exit",
                            lat = 31.2304,
                            lon = 121.4737,
                            radiusM = 500,
                        ),
                    ),
                )
            )
        )

        val now = at(2026, 9, 18, 8, 0)
        // 首次采样只记基线
        assertEquals(0, engine.onLocation(31.2304, 121.4737, now))
        // 离开围栏 → exit
        assertEquals(1, engine.onLocation(31.3000, 121.4737, now + 60_000))
        // 再次离开不重复
        assertEquals(0, engine.onLocation(31.3100, 121.4737, now + 120_000))
        // 回到围栏 → enter
        assertEquals(1, engine.onLocation(31.2304, 121.4737, now + 180_000))
        assertEquals("enter", argsOf(recorder.runs[1].second)["state"])
        assertEquals(31.2304, argsOf(recorder.runs[1].second)["lat"] as Double, 0.0001)
    }

    @Test
    fun retryReDispatchesFailedRunUntilSuccess() {
        val recorder = Recorder()
        val now = at(2026, 9, 18, 8, 0)
        val engine = engine(recorder, now)
        engine.updateCards(
            listOf(
                TriggerCard(
                    "flaky_card",
                    listOf(CardManifest.Event(type = "charging", state = "connected")),
                    maxRetries = 2,
                    retryDelaySeconds = 10,
                )
            )
        )

        assertEquals(1, engine.onCharging("connected", now))
        val args = recorder.runs[0].second
        assertTrue(engine.reportRunResult("flaky_card", args, success = false, now = now))
        // 延迟未到
        assertEquals(0, engine.tickRetries(now + 9_000))
        assertTrue(engine.hasPendingRetry("flaky_card"))
        // 到期重试
        assertEquals(1, engine.tickRetries(now + 10_000))
        assertTrue(engine.reportRunResult("flaky_card", args, success = false, now = now + 10_000))
        assertEquals(1, engine.tickRetries(now + 20_000))
        // 重试次数用尽，不再排队
        assertFalse(engine.reportRunResult("flaky_card", args, success = false, now = now + 20_000))
        assertFalse(engine.hasPendingRetry("flaky_card"))
        assertEquals(0, engine.tickRetries(now + 30_000))
        assertEquals(3, recorder.runs.size)
    }

    @Test
    fun retryClearedAfterSuccess() {
        val recorder = Recorder()
        val now = at(2026, 9, 18, 8, 0)
        val engine = engine(recorder, now)
        engine.updateCards(
            listOf(
                TriggerCard(
                    "recover_card",
                    listOf(CardManifest.Event(type = "charging", state = "connected")),
                    maxRetries = 3,
                    retryDelaySeconds = 5,
                )
            )
        )
        engine.onCharging("connected", now)
        val args = recorder.runs[0].second
        assertTrue(engine.reportRunResult("recover_card", args, success = false, now = now))
        engine.tickRetries(now + 5_000)
        assertFalse(engine.reportRunResult("recover_card", args, success = true, now = now + 5_000))
        assertFalse(engine.hasPendingRetry("recover_card"))
        assertEquals(0, engine.tickRetries(now + 60_000))
        assertEquals(2, recorder.runs.size)
    }

    @Test
    fun removedCardDropsCooldownState() {
        val recorder = Recorder()
        val now = at(2026, 9, 18, 8, 0)
        val engine = engine(recorder, now)
        engine.updateCards(
            listOf(
                TriggerCard("temporary", listOf(CardManifest.Event(type = "charging", state = "connected")))
            )
        )
        assertEquals(1, engine.onCharging("connected", now))
        engine.updateCards(emptyList())
        assertEquals(0, engine.onCharging("connected", now + 1))
        assertEquals(1, recorder.runs.size)
    }

    @Test
    fun notificationClickMatchesAndCoolsDown() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 18, 8, 0))
        engine.updateCards(
            listOf(
                TriggerCard(
                    "click_card",
                    listOf(
                        CardManifest.Event(
                            type = "notification_click",
                            packageName = "com.example.sms",
                            textContains = "领取",
                        ),
                        // 空条件事件在引擎层被显式拒绝
                        CardManifest.Event(type = "notification_click"),
                    ),
                )
            )
        )

        val now = at(2026, 9, 18, 8, 0)
        assertEquals(0, engine.onNotificationClick("com.other.app", "红包", "点此领取", now))
        assertEquals(1, engine.onNotificationClick("com.example.sms", "红包", "点此领取", now))
        // 冷却期内重复点击被抑制
        assertEquals(0, engine.onNotificationClick("com.example.sms", "红包", "点此领取", now + 1_000))
        assertEquals(1, engine.onNotificationClick("com.example.sms", "红包", "点此领取", now + 3_000))
        assertEquals(2, recorder.runs.size)
        val event = argsOf(recorder.runs[0].second)
        assertEquals("notification_click", event["type"])
        assertEquals("com.example.sms", event["package"])
        assertEquals("点此领取", event["text"])
    }

    @Test
    fun notificationReplyMatchesPackageAndTextWithCooldown() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 18, 8, 0))
        engine.updateCards(
            listOf(
                TriggerCard(
                    "reply_ok",
                    listOf(
                        CardManifest.Event(
                            type = "notification_reply",
                            packageName = "com.tencent.mm",
                            textContains = "收到",
                        )
                    ),
                ),
                // 运行期兜底：缺包名的回复事件不应匹配任何输入
                TriggerCard(
                    "reply_missing_package",
                    listOf(CardManifest.Event(type = "notification_reply", textContains = "收到")),
                ),
            )
        )

        val now = at(2026, 9, 18, 8, 0)
        assertEquals(0, engine.onNotificationReply("com.other.app", "群聊", "收到", now))
        assertEquals(1, engine.onNotificationReply("com.tencent.mm", "群聊", "收到，谢谢", now))
        assertEquals(0, engine.onNotificationReply("com.tencent.mm", "群聊", "收到", now + 1_000))
        assertEquals(1, engine.onNotificationReply("com.tencent.mm", "群聊", "收到", now + 3_000))
        assertEquals(2, recorder.runs.size)
        val event = argsOf(recorder.runs[0].second)
        assertEquals("notification_reply", event["type"])
        assertEquals("com.tencent.mm", event["package"])
        assertEquals("收到，谢谢", event["text"])
    }

    @Test
    fun appInstallAndUninstallMatchOptionalPackage() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 18, 8, 0))
        engine.updateCards(
            listOf(
                TriggerCard("install_any", listOf(CardManifest.Event(type = "app_install"))),
                TriggerCard(
                    "install_target",
                    listOf(CardManifest.Event(type = "app_install", packageName = "com.example.new")),
                ),
                TriggerCard(
                    "uninstall_target",
                    listOf(CardManifest.Event(type = "app_uninstall", packageName = "com.example.old")),
                ),
            )
        )

        val now = at(2026, 9, 18, 8, 0)
        // 空包名输入被拒绝
        assertEquals(0, engine.onAppInstall("", now))
        // 任意卡片 + 指定卡片同时命中
        assertEquals(2, engine.onAppInstall("com.example.new", now))
        assertEquals(0, engine.onAppInstall("com.example.new", now + 1_000))
        assertEquals(2, engine.onAppInstall("com.example.new", now + 3_000))
        // 卸载只命中指定包名
        assertEquals(0, engine.onAppUninstall("com.other.app", now))
        assertEquals(1, engine.onAppUninstall("com.example.old", now))
        assertEquals("app_uninstall", argsOf(recorder.runs.last().second)["type"])
        assertEquals("com.example.old", argsOf(recorder.runs.last().second)["package"])
    }

    @Test
    fun scheduleCalendarFiltersWorkdayAndHoliday() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 24, 9, 0))
        engine.calendar = WorkdayCalendar.parse(
            """{"holidays":["2026-09-25","2026-10-01"],"workdays":["2026-09-20"]}"""
        )
        engine.updateCards(
            listOf(
                TriggerCard(
                    "workday_card",
                    listOf(CardManifest.Event(type = "schedule", times = listOf("08:00"), calendar = "workday")),
                ),
                TriggerCard(
                    "holiday_card",
                    listOf(CardManifest.Event(type = "schedule", times = listOf("08:00"), calendar = "holiday")),
                ),
            )
        )

        // 2026-09-24 周四：仅工作日命中
        assertEquals(1, engine.tickSchedule(now = at(2026, 9, 24, 8, 0), zone = zone))
        assertEquals("workday_card", recorder.runs.last().first)
        // 2026-09-25 周五（中秋，法定假日）：仅节假日命中
        assertEquals(1, engine.tickSchedule(now = at(2026, 9, 25, 8, 0), zone = zone))
        assertEquals("holiday_card", recorder.runs.last().first)
        // 2026-09-26 周六（自然周末，非法定假日）：两卡都不命中
        assertEquals(0, engine.tickSchedule(now = at(2026, 9, 26, 8, 0), zone = zone))
        assertEquals(2, recorder.runs.size)
    }

    @Test
    fun scheduleCalendarWeekendMatchesRestDaysAndMakeupWorkday() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 25, 9, 0))
        engine.calendar = WorkdayCalendar.parse(
            """{"holidays":["2026-09-25"],"workdays":["2026-09-20"]}"""
        )
        engine.updateCards(
            listOf(
                TriggerCard(
                    "weekend_card",
                    listOf(CardManifest.Event(type = "schedule", times = listOf("08:00"), calendar = "weekend")),
                ),
                TriggerCard(
                    "workday_card",
                    listOf(CardManifest.Event(type = "schedule", times = listOf("08:00"), calendar = "workday")),
                ),
            )
        )

        // 法定假日在「休息日」语义内：weekend 命中
        assertEquals(1, engine.tickSchedule(now = at(2026, 9, 25, 8, 0), zone = zone))
        assertEquals("weekend_card", recorder.runs.last().first)
        // 周六同样是休息日
        assertEquals(1, engine.tickSchedule(now = at(2026, 9, 26, 8, 0), zone = zone))
        // 周一恢复上班：weekend 不命中，workday 命中
        assertEquals(1, engine.tickSchedule(now = at(2026, 9, 28, 8, 0), zone = zone))
        assertEquals("workday_card", recorder.runs.last().first)
    }

    @Test
    fun scheduleCalendarTreatsMakeupSundayAsWorkday() {
        val recorder = Recorder()
        val engine = engine(recorder, at(2026, 9, 20, 8, 0))
        engine.calendar = WorkdayCalendar.parse("""{"workdays":["2026-09-20"],"holidays":[]}""")
        engine.updateCards(
            listOf(
                TriggerCard(
                    "workday_card",
                    listOf(CardManifest.Event(type = "schedule", times = listOf("08:00"), calendar = "workday")),
                ),
                TriggerCard(
                    "weekend_card",
                    listOf(CardManifest.Event(type = "schedule", times = listOf("08:00"), calendar = "weekend")),
                ),
            )
        )
        // 2026-09-20 是周日，但为调休上班日：仅 workday 命中
        assertEquals(1, engine.tickSchedule(zone = zone))
        assertEquals("workday_card", recorder.runs.last().first)
        assertEquals(1, recorder.runs.size)
    }
}
