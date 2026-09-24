package heizige.kk.khatkit.card

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardValidatorTest {

    private fun manifest(
        name: String = "pdf_merge",
        engine: String = "lua",
        privilege: String = "none",
        bridges: List<String> = listOf("tool", "ui"),
        tags: CardManifest.Tags? = CardManifest.Tags("file", "convert", "work"),
        compliance: CardManifest.Compliance? = null,
        network: List<String> = emptyList(),
        command: String? = null,
        triggers: List<String> = CardManifest.DEFAULT_TRIGGERS,
        events: List<CardManifest.Event> = emptyList(),
    ) = CardManifest(
        name = name,
        version = "1.2.0",
        engine = engine,
        entry = CardManifest.Entry(lua = "main.lua"),
        privilege = privilege,
        requires = CardManifest.Requires(bridges = bridges),
        network = CardManifest.Network(allow = network),
        parameters = JsonObject(emptyMap()),
        triggers = triggers,
        tags = tags,
        compliance = compliance,
        command = command,
        events = events,
    )

    @Test
    fun validCardPasses() {
        assertTrue(CardValidator.isValid(manifest()))
    }

    @Test
    fun invalidNameRejected() {
        val issues = CardValidator.validate(manifest(name = "Bad-Name"))
        assertTrue(issues.any { it.code == "NAME_INVALID" && it.severity == Severity.ERROR })
    }

    @Test
    fun gameDomainRequiresCompliance() {
        val issues = CardValidator.validate(
            manifest(tags = CardManifest.Tags("game", "control"))
        )
        assertTrue(issues.any { it.code == "GAME_COMPLIANCE_MISSING" })
    }

    @Test
    fun gameDomainWithCompliancePasses() {
        assertTrue(
            CardValidator.isValid(
                manifest(
                    tags = CardManifest.Tags("game", "control"),
                    compliance = CardManifest.Compliance("high", "注意用户协议"),
                )
            )
        )
    }

    @Test
    fun unknownTagRejected() {
        val issues = CardValidator.validate(manifest(tags = CardManifest.Tags("pdf", "convert")))
        assertTrue(issues.any { it.code == "TAG_DOMAIN_INVALID" })
    }

    @Test
    fun undeclaredBridgeInScriptRejected() {
        val issues = CardValidator.validate(
            manifest(),
            scripts = mapOf("main.lua" to "root.shell('id')"),
        )
        assertTrue(issues.any { it.code == "BRIDGE_UNDECLARED" })
    }

    @Test
    fun undeclaredDomainInScriptRejected() {
        val issues = CardValidator.validate(
            manifest(),
            scripts = mapOf("main.lua" to "tool.httpGet('https://evil.example.com/x')"),
        )
        assertTrue(issues.any { it.code == "NETWORK_UNDECLARED" })
    }

    @Test
    fun declaredDomainAllowed() {
        val issues = CardValidator.validate(
            manifest(network = listOf("example.com")),
            scripts = mapOf("main.lua" to "tool.httpGet('https://api.example.com/x')"),
        )
        assertFalse(issues.any { it.code == "NETWORK_UNDECLARED" })
    }

    @Test
    fun commandEngineNeedsCommandField() {
        val issues = CardValidator.validate(manifest(engine = "command"))
        assertTrue(issues.any { it.code == "COMMAND_MISSING" })
    }

    @Test
    fun unknownTriggerRejected() {
        val issues = CardValidator.validate(manifest(triggers = listOf("timer")))
        assertTrue(issues.any { it.code == "TRIGGER_UNKNOWN" && it.severity == Severity.ERROR })
    }

    @Test
    fun duplicateTriggerRejected() {
        val issues = CardValidator.validate(manifest(triggers = listOf("ai", "ai")))
        assertTrue(issues.any { it.code == "TRIGGER_DUPLICATE" && it.severity == Severity.ERROR })
    }

    @Test
    fun emptyTriggersRejected() {
        val issues = CardValidator.validate(manifest(triggers = emptyList()))
        assertTrue(issues.any { it.code == "TRIGGER_EMPTY" && it.severity == Severity.ERROR })
    }

    @Test
    fun defaultTriggersSupportAiAndUser() {
        val parsed = manifest()
        assertTrue(parsed.supportsAi())
        assertTrue(parsed.supportsUser())
    }

    @Test
    fun explicitUserOnlyTriggerParses() {
        val parsed = CardParser.parse(
            """
            {
              "name": "user_only",
              "version": "1.0.0",
              "engine": "lua",
              "entry": { "lua": "main.lua" },
              "requires": { "bridges": ["ui"] },
              "network": { "allow": [] },
              "triggers": ["user"],
              "tags": { "domain": "file", "action": "convert" }
            }
            """.trimIndent()
        ).getOrThrow()
        assertFalse(parsed.supportsAi())
        assertTrue(parsed.supportsUser())
        assertTrue(CardValidator.isValid(parsed))
    }

    @Test
    fun unknownEventTypeRejected() {
        val issues = CardValidator.validate(
            manifest(events = listOf(CardManifest.Event(type = "timer")))
        )
        assertTrue(issues.any { it.code == "EVENT_TYPE_UNKNOWN" && it.severity == Severity.ERROR })
    }

    @Test
    fun scheduleWithoutTimesOrIntervalRejected() {
        val issues = CardValidator.validate(
            manifest(events = listOf(CardManifest.Event(type = "schedule")))
        )
        assertTrue(issues.any { it.code == "EVENT_SCHEDULE_EMPTY" })
    }

    @Test
    fun scheduleTimeFormatChecked() {
        val issues = CardValidator.validate(
            manifest(
                events = listOf(
                    CardManifest.Event(type = "schedule", times = listOf("8:00", "25:00"))
                )
            )
        )
        assertEquals(2, issues.count { it.code == "EVENT_TIME_INVALID" })
    }

    @Test
    fun scheduleValidTimePasses() {
        assertTrue(
            CardValidator.isValid(
                manifest(
                    events = listOf(
                        CardManifest.Event(
                            type = "schedule",
                            times = listOf("08:00", "21:30"),
                            days = listOf(1, 3, 5, 7),
                        )
                    )
                )
            )
        )
    }

    @Test
    fun scheduleInvalidDayRejected() {
        val issues = CardValidator.validate(
            manifest(
                events = listOf(
                    CardManifest.Event(type = "schedule", intervalMinutes = 30, days = listOf(0, 8))
                )
            )
        )
        assertEquals(2, issues.count { it.code == "EVENT_DAY_INVALID" })
    }

    @Test
    fun scheduleIntervalModePasses() {
        assertTrue(
            CardValidator.isValid(
                manifest(events = listOf(CardManifest.Event(type = "schedule", intervalMinutes = 15)))
            )
        )
    }

    @Test
    fun notificationWithoutAnyMatchRejected() {
        val issues = CardValidator.validate(
            manifest(events = listOf(CardManifest.Event(type = "notification")))
        )
        assertTrue(issues.any { it.code == "EVENT_NOTIFICATION_EMPTY" })
    }

    @Test
    fun notificationWithTitleButNoPackagePasses() {
        assertTrue(
            CardValidator.isValid(
                manifest(
                    events = listOf(
                        CardManifest.Event(type = "notification", titleContains = "验证码")
                    )
                )
            )
        )
    }

    @Test
    fun appLaunchWithEmptyPackagePasses() {
        assertTrue(
            CardValidator.isValid(
                manifest(events = listOf(CardManifest.Event(type = "app_launch")))
            )
        )
    }

    @Test
    fun appExitRequiresPackage() {
        val issues = CardValidator.validate(
            manifest(events = listOf(CardManifest.Event(type = "app_exit")))
        )
        assertTrue(issues.any { it.code == "EVENT_APP_EXIT_PACKAGE_REQUIRED" })
        assertTrue(
            CardValidator.isValid(
                manifest(events = listOf(CardManifest.Event(type = "app_exit", packageName = "com.tencent.mm")))
            )
        )
    }

    @Test
    fun shortcutRequiresName() {
        val issues = CardValidator.validate(
            manifest(events = listOf(CardManifest.Event(type = "shortcut")))
        )
        assertTrue(issues.any { it.code == "EVENT_SHORTCUT_NAME_REQUIRED" })
        assertTrue(
            CardValidator.isValid(
                manifest(events = listOf(CardManifest.Event(type = "shortcut", name = "一键签到")))
            )
        )
    }

    @Test
    fun tileRequiresName() {
        val issues = CardValidator.validate(
            manifest(events = listOf(CardManifest.Event(type = "tile")))
        )
        assertTrue(issues.any { it.code == "EVENT_TILE_NAME_REQUIRED" })
        assertTrue(
            CardValidator.isValid(
                manifest(events = listOf(CardManifest.Event(type = "tile", name = "快速记账")))
            )
        )
    }

    @Test
    fun appExitShortcutTileJsonRoundTrip() {
        val parsed = CardParser.parse(
            """
            {
              "name": "exit_hook",
              "version": "1.0.0",
              "engine": "lua",
              "entry": { "lua": "main.lua" },
              "requires": { "bridges": ["ui"] },
              "tags": { "domain": "system", "action": "monitor" },
              "events": [
                { "type": "app_exit", "package": "com.tencent.mm" },
                { "type": "shortcut", "name": "打开便签" },
                { "type": "tile", "name": "静音开关" }
              ]
            }
            """.trimIndent()
        ).getOrThrow()
        assertEquals(3, parsed.events.size)
        assertEquals("com.tencent.mm", parsed.events[0].packageName)
        assertEquals("打开便签", parsed.events[1].name)
        assertEquals("静音开关", parsed.events[2].name)
        assertTrue(CardValidator.isValid(parsed))
    }

    @Test
    fun chargingRequiresKnownState() {
        val invalid = CardValidator.validate(
            manifest(events = listOf(CardManifest.Event(type = "charging", state = "plugged")))
        )
        assertTrue(invalid.any { it.code == "EVENT_CHARGING_STATE_INVALID" })

        assertTrue(
            CardValidator.isValid(
                manifest(events = listOf(CardManifest.Event(type = "charging", state = "connected")))
            )
        )
    }

    @Test
    fun wifiStateValidatedButOptional() {
        assertTrue(
            CardValidator.isValid(
                manifest(events = listOf(CardManifest.Event(type = "wifi")))
            )
        )
        assertTrue(
            CardValidator.isValid(
                manifest(
                    events = listOf(
                        CardManifest.Event(type = "wifi", ssid = "Home", state = "disconnected")
                    )
                )
            )
        )
        val issues = CardValidator.validate(
            manifest(events = listOf(CardManifest.Event(type = "wifi", state = "on")))
        )
        assertTrue(issues.any { it.code == "EVENT_WIFI_STATE_INVALID" })
    }

    @Test
    fun networkStateRequired() {
        val issues = CardValidator.validate(
            manifest(events = listOf(CardManifest.Event(type = "network", state = "wifi")))
        )
        assertTrue(issues.any { it.code == "EVENT_NETWORK_STATE_INVALID" })
        assertTrue(
            CardValidator.isValid(
                manifest(events = listOf(CardManifest.Event(type = "network", state = "offline")))
            )
        )
    }

    @Test
    fun batteryNeedsConditionAndValidRange() {
        val empty = CardValidator.validate(
            manifest(events = listOf(CardManifest.Event(type = "battery")))
        )
        assertTrue(empty.any { it.code == "EVENT_BATTERY_EMPTY" })

        val badLevel = CardValidator.validate(
            manifest(
                events = listOf(CardManifest.Event(type = "battery", levelBelow = 120))
            )
        )
        assertTrue(badLevel.any { it.code == "EVENT_BATTERY_LEVEL_INVALID" })

        assertTrue(
            CardValidator.isValid(
                manifest(
                    events = listOf(
                        CardManifest.Event(
                            type = "battery",
                            levelBelow = 20,
                            levelAbove = 80,
                            state = "discharging",
                        )
                    )
                )
            )
        )
    }

    @Test
    fun screenStateValidated() {
        val issues = CardValidator.validate(
            manifest(events = listOf(CardManifest.Event(type = "screen", state = "bright")))
        )
        assertTrue(issues.any { it.code == "EVENT_SCREEN_STATE_INVALID" })
        assertTrue(
            CardValidator.isValid(
                manifest(events = listOf(CardManifest.Event(type = "screen", state = "locked")))
            )
        )
    }

    @Test
    fun clipboardNeedsTextContains() {
        val issues = CardValidator.validate(
            manifest(events = listOf(CardManifest.Event(type = "clipboard")))
        )
        assertTrue(issues.any { it.code == "EVENT_CLIPBOARD_EMPTY" })
        assertTrue(
            CardValidator.isValid(
                manifest(events = listOf(CardManifest.Event(type = "clipboard", textContains = "https://")))
            )
        )
    }

    @Test
    fun bluetoothNeedsConnectionState() {
        val issues = CardValidator.validate(
            manifest(events = listOf(CardManifest.Event(type = "bluetooth", state = "paired")))
        )
        assertTrue(issues.any { it.code == "EVENT_BLUETOOTH_STATE_INVALID" })
        assertTrue(
            CardValidator.isValid(
                manifest(
                    events = listOf(
                        CardManifest.Event(type = "bluetooth", state = "connected", device = "耳机")
                    )
                )
            )
        )
    }

    @Test
    fun locationGeometryValidated() {
        val issues = CardValidator.validate(
            manifest(events = listOf(CardManifest.Event(type = "location", state = "enter")))
        )
        assertTrue(issues.any { it.code == "EVENT_LOCATION_LAT_INVALID" })
        assertTrue(issues.any { it.code == "EVENT_LOCATION_LON_INVALID" })
        assertTrue(issues.any { it.code == "EVENT_LOCATION_RADIUS_INVALID" })

        val badState = CardValidator.validate(
            manifest(
                events = listOf(
                    CardManifest.Event(
                        type = "location",
                        state = "inside",
                        lat = 31.23,
                        lon = 121.47,
                        radiusM = 200,
                    )
                )
            )
        )
        assertTrue(badState.any { it.code == "EVENT_LOCATION_STATE_INVALID" })

        assertTrue(
            CardValidator.isValid(
                manifest(
                    events = listOf(
                        CardManifest.Event(
                            type = "location",
                            state = "exit",
                            lat = 31.23,
                            lon = 121.47,
                            radiusM = 200,
                        )
                    )
                )
            )
        )
    }

    @Test
    fun eventsJsonRoundTrip() {
        val parsed = CardParser.parse(
            """
            {
              "name": "notify_hook",
              "version": "1.0.0",
              "engine": "lua",
              "entry": { "lua": "main.lua" },
              "requires": { "bridges": ["ui"] },
              "tags": { "domain": "system", "action": "monitor" },
              "events": [
                { "type": "schedule", "times": ["08:00"], "days": [1, 2] },
                { "type": "notification", "package": "com.tencent.mm", "titleContains": "红包" },
                { "type": "app_launch", "package": "com.tencent.mm" },
                { "type": "charging", "state": "connected" },
                { "type": "wifi", "ssid": "Home", "state": "connected" },
                { "type": "network", "state": "offline" },
                { "type": "battery", "level_below": 20, "state": "discharging" },
                { "type": "screen", "state": "unlocked" },
                { "type": "clipboard", "text_contains": "https://" },
                { "type": "bluetooth", "state": "connected", "device": "耳机" },
                {
                  "type": "location",
                  "state": "enter",
                  "lat": 31.23,
                  "lon": 121.47,
                  "radius_m": 200
                }
              ]
            }
            """.trimIndent()
        ).getOrThrow()
        assertEquals(11, parsed.events.size)
        assertEquals("com.tencent.mm", parsed.events[1].packageName)
        assertEquals(20, parsed.events[6].levelBelow)
        assertEquals(200, parsed.events[10].radiusM)
        assertEquals(31.23, parsed.events[10].lat!!, 0.0001)
        assertTrue(parsed.supportsEvents())
        assertTrue(CardValidator.isValid(parsed))
    }

    @Test
    fun scheduleCalendarValidated() {
        assertTrue(
            CardValidator.isValid(
                manifest(
                    events = listOf(
                        CardManifest.Event(
                            type = "schedule",
                            times = listOf("08:00"),
                            calendar = CardManifest.CALENDAR_WORKDAY,
                        )
                    )
                )
            )
        )
        val issues = CardValidator.validate(
            manifest(
                events = listOf(
                    CardManifest.Event(type = "schedule", intervalMinutes = 60, calendar = "monday")
                )
            )
        )
        assertTrue(issues.any { it.code == "EVENT_CALENDAR_INVALID" && it.severity == Severity.ERROR })
    }

    @Test
    fun calendarOnlyAllowedOnSchedule() {
        val issues = CardValidator.validate(
            manifest(
                events = listOf(
                    CardManifest.Event(
                        type = "notification",
                        packageName = "com.example.app",
                        calendar = CardManifest.CALENDAR_WORKDAY,
                    )
                )
            )
        )
        assertTrue(issues.any { it.code == "EVENT_CALENDAR_UNSUPPORTED" })
    }

    @Test
    fun notificationClickRequiresMatchCondition() {
        val issues = CardValidator.validate(
            manifest(events = listOf(CardManifest.Event(type = "notification_click")))
        )
        assertTrue(issues.any { it.code == "EVENT_NOTIFICATION_CLICK_EMPTY" })
        assertTrue(
            CardValidator.isValid(
                manifest(
                    events = listOf(
                        CardManifest.Event(type = "notification_click", packageName = "com.tencent.mm")
                    )
                )
            )
        )
    }

    @Test
    fun notificationReplyRequiresPackage() {
        val issues = CardValidator.validate(
            manifest(events = listOf(CardManifest.Event(type = "notification_reply", titleContains = "群聊")))
        )
        assertTrue(issues.any { it.code == "EVENT_NOTIFICATION_REPLY_PACKAGE_REQUIRED" })
        assertTrue(
            CardValidator.isValid(
                manifest(
                    events = listOf(
                        CardManifest.Event(type = "notification_reply", packageName = "com.tencent.mm")
                    )
                )
            )
        )
    }

    @Test
    fun appInstallAndUninstallAllowEmptyPackage() {
        assertTrue(
            CardValidator.isValid(
                manifest(
                    events = listOf(
                        CardManifest.Event(type = "app_install"),
                        CardManifest.Event(type = "app_uninstall", packageName = "com.example.app"),
                    )
                )
            )
        )
    }

    @Test
    fun newEventTypesJsonRoundTrip() {
        val parsed = CardParser.parse(
            """
            {
              "name": "hook2",
              "version": "1.0.0",
              "engine": "lua",
              "entry": { "lua": "main.lua" },
              "requires": { "bridges": ["ui"] },
              "tags": { "domain": "system", "action": "monitor" },
              "events": [
                {
                  "type": "schedule",
                  "times": ["08:00"],
                  "calendar": "workday"
                },
                {
                  "type": "notification_click",
                  "package": "com.tencent.mm",
                  "title_contains": "红包",
                  "text_contains": "领取"
                },
                { "type": "notification_reply", "package": "com.tencent.mm" },
                { "type": "app_install" },
                { "type": "app_uninstall", "package": "com.example.app" }
              ]
            }
            """.trimIndent()
        ).getOrThrow()
        assertEquals(5, parsed.events.size)
        assertEquals(CardManifest.CALENDAR_WORKDAY, parsed.events[0].calendar)
        assertEquals("红包", parsed.events[1].titleContains)
        assertEquals("领取", parsed.events[1].textContains)
        assertEquals("com.tencent.mm", parsed.events[2].packageName)
        assertEquals("com.example.app", parsed.events[4].packageName)
        assertTrue(CardValidator.isValid(parsed))
    }

    @Test
    fun jsonParsingRoundTrip() {
        val json = """
            {
              "name": "pdf_merge",
              "version": "1.2.0",
              "description": "合并多个 PDF 为一个",
              "engine": "auto",
              "entry": { "lua": "main.lua", "js": "main.js" },
              "privilege": "none",
              "requires": { "bridges": ["tool", "ui"], "libs": [], "bins": [], "env": [] },
              "network": { "allow": [] },
              "parameters": { "type": "object", "properties": {} },
              "tags": { "domain": "file", "action": "convert", "scene": "work" },
              "store": { "quota_mb": 50, "secret": false }
            }
        """.trimIndent()
        val parsed = CardParser.parse(json).getOrThrow()
        assertEquals("pdf_merge", parsed.name)
        assertEquals("work", parsed.tags?.scene)
        assertEquals(50, parsed.store.quotaMb)
        assertEquals(listOf("ai", "user"), parsed.triggers)
        assertTrue(CardValidator.isValid(parsed))
    }
}
