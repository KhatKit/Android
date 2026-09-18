package heizige.kk.khatkit.trigger

import heizige.kk.khatkit.card.CardManifest
import org.junit.Assert.assertEquals
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
}
