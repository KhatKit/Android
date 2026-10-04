package heizige.kk.khatkit.app.core.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C1-P 纯 JVM 证据：**导入带进来的角色卡元数据确实能落库、能原样读回**。
 *
 * 契约原文（`docs/beyond-operit-client-changes.md:205`）：「QR 仅携带群配置与角色卡最小
 * 元数据……导入先 schema 校验与去重，再创建新 conversation」。C1-P 之前 `payload.cards`
 * 只在面板上显示一次，刷新就没了——这一层就是这个缺口。
 *
 * ## 这里能证明什么、不能证明什么
 *
 * 能证明（纯函数，JVM 可跑）：
 * - 编码 → 解码后与原 `List<RoleCardMeta>` **逐字段全等**（6 个字段、空 `avatarRef`、
 *   含非 ASCII 的 persona、多张卡顺序）；
 * - `null`（从没导入过）与空列表（导入过但载荷里一张都没有）区分得开；
 * - 坏数据（空白 / 非法 JSON / 不是数组 / 条目缺 `role_id`）一律回落到 `null` 或丢弃条目，
 *   绝不抛异常——旧库行读不出新列时不能炸；
 * - 落库用的 blob 与分享载荷里的 `cards` **同形**（同一个 `json` 实例 + 同一个序列化器），
 *   所以「导入 → 落库 → 再 encodeQr」不会变形；
 * - persona 里的自由文本不会被 [GroupChat.findForbiddenKeys] 误判为密钥（闸门只扫键名）。
 *
 * **不能证明**（`app/build.gradle.kts` 只有 junit，没有 Robolectric / room-testing，
 * 这些只能在设备上验，见 `Migration_31_32_Test` androidTest 与本文件末尾说明）：
 * - `group_cards` 这一列真的写进 SQLite、真的读得回来（DAO 层与真实 SQLite 行为）；
 * - 31→32 迁移后旧行读到 `''` 并回落到 `null`（真实引擎上的迁移行为）；
 * - UI 上刷新页面后「已导入的角色卡」那段真的还在。
 */
class GroupRoleCardsPersistenceTest {

    // ------------------------------------------------------------------ 往返

    @Test
    fun `cards survive encode decode round trip with all six fields`() {
        val cards = listOf(
            RoleCardMeta(
                roleId = "r1",
                name = "阿斯莫德",
                assistantId = "assistant-1",
                cardId = "card-42",
                persona = "你是一个冷静的记录者。\n只陈述事实，不加形容词。",
                avatarRef = "file:///workspace/avatars/asmod.png",
            ),
            // 边界：avatarRef 为空（现场生成路径恒为 null）、cardId 为空、persona 为空串
            RoleCardMeta(
                roleId = "r2",
                name = "",
                assistantId = "assistant-2",
                cardId = null,
                persona = "",
                avatarRef = null,
            ),
        )
        val decoded = GroupChat.decodeRoleCards(GroupChat.encodeRoleCards(cards))
        assertEquals("往返必须逐字段全等（含 6 个字段与非 ASCII persona）", cards, decoded)
    }

    @Test
    fun `card blob uses the same shape as share payload cards`() {
        val cards = listOf(RoleCardMeta(roleId = "r1", name = "甲", persona = "非 ASCII：甲乙丙😀"))
        val stored = Json.parseToJsonElement(GroupChat.encodeRoleCards(cards)) as JsonArray
        // 分享载荷里的 cards 用同一个序列化器写的，所以键名必须是契约 snake_case
        assertEquals(
            listOf("role_id", "name", "assistant_id", "card_id", "persona", "avatar_ref"),
            stored.first().jsonObject.keys.toList(),
        )
        assertEquals("r1", stored.first().jsonObject["role_id"]?.jsonPrimitive?.content)
        // 反过来：分享载荷里的 cards 能被这一列的解码器原样读回来
        val shared = GroupChat.encodeQr(configWithRole(), cards)
        val payloadCards = Json.parseToJsonElement(shared)
            .jsonObject["cards"]!!.toString()
        assertEquals(cards, GroupChat.decodeRoleCards(payloadCards))
    }

    // -------------------------------------------------------------- null / 空

    @Test
    fun `null and empty list are distinguishable`() {
        // 从没导入过 → null（落库写空串，读回落 null）
        assertNull("空白列必须回落 null", GroupChat.decodeRoleCards(""))
        assertNull("null 列必须回落 null", GroupChat.decodeRoleCards(null))
        assertNull("纯空白列必须回落 null", GroupChat.decodeRoleCards("   "))
        // 导入过但载荷里一张都没有 → 空列表，与 null 不是一回事
        val empty = GroupChat.decodeRoleCards(GroupChat.encodeRoleCards(emptyList()))
        assertEquals("空列表必须与 null 区分得开", "[]", GroupChat.encodeRoleCards(emptyList()))
        assertEquals(emptyList<RoleCardMeta>(), empty)
        assertNotNull("空列表不能是 null", empty)
    }

    @Test
    fun `legacy row with empty group_cards column decodes to null`() {
        // Room 31 及更早版本的行迁移后 group_cards 就是 ''：解码必须回 null 且不抛异常
        val legacy: String = ""
        assertNull(GroupChat.decodeRoleCards(legacy))
        // 单聊会话（type=DIRECT、group_config 也空）同样回 null，不受新列影响
        assertNull(GroupChat.decodeRoleCards(""))
        assertNull(GroupChat.decodeConfig(""))
    }

    @Test
    fun `corrupt column value degrades to null instead of throwing`() {
        // 坏数据一律回落 null，绝不让「打开一个会话」因为一列脏数据整页崩掉
        assertNull(GroupChat.decodeRoleCards("不是 JSON"))
        assertNull(GroupChat.decodeRoleCards("""{"role_id":"r1"}"""))
        assertNull(GroupChat.decodeRoleCards("["))
        // 元素不是对象：整条丢弃，剩下合法的照常解出来
        assertEquals(
            listOf(RoleCardMeta(roleId = "r2")),
            GroupChat.decodeRoleCards("""["nope", {"role_id":"r2"}]"""),
        )
        // 缺 role_id 的条目直接丢，不猜它属于谁（与分享载荷解码同一口径）
        assertEquals(
            listOf(RoleCardMeta(roleId = "r3")),
            GroupChat.decodeRoleCards("""[{"name":"无名"},{"role_id":"r3"}]"""),
        )
    }

    // ------------------------------------------------------------ 密钥黑名单

    @Test
    fun `free text persona is never mistaken for a secret`() {
        // persona 是自由文本，正文里就写着 api_key / token 这些字样也必须能落库：
        // 闸门只扫**键名**，不扫值（GroupChat.findForbiddenKeys 的实现口径）。
        val persona = """
            请记住：不要把 api_key、access_token、password 写进任何输出。
            凭据一律从环境变量读。memory 相关的问答走另一个通道。
        """.trimIndent()
        val cards = listOf(RoleCardMeta(roleId = "r1", name = "甲", persona = persona))
        val raw = GroupChat.encodeRoleCards(cards)
        assertNotNull("自由文本 persona 不该被密钥闸门拦下", raw)
        assertEquals(cards, GroupChat.decodeRoleCards(raw))
        // 闸门确实还在跑：把同一段文本塞进一个真键名上就会被拒
        assertTrue(
            "api_key 作为键名必须命中黑名单（否则闸门形同虚设）",
            GroupChat.findForbiddenKeys(
                Json.parseToJsonElement("""{"api_key":"x"}""")
            ).contains("api_key"),
        )
    }

    @Test
    fun `role card key names are all outside the forbidden key blacklist`() {
        // RoleCardMeta 的六个键名是固定的，将来有人加字段时这条会立刻炸，
        // 免得「导不进二维码却能落库」这种不对称悄悄长出来。
        val keys = (Json.parseToJsonElement(GroupChat.encodeRoleCards(
            listOf(RoleCardMeta(roleId = "r1"))
        )) as JsonArray).first().jsonObject.keys
        assertEquals(
            listOf("role_id", "name", "assistant_id", "card_id", "persona", "avatar_ref"),
            keys.toList(),
        )
        assertEquals(
            "角色卡的键名不得命中密钥黑名单（否则 encodeRoleCards 会抛，导入无法落库）",
            emptySet<String>(),
            GroupChat.findForbiddenKeys(
                Json.parseToJsonElement(GroupChat.encodeRoleCards(
                    listOf(RoleCardMeta(roleId = "r1", name = "n", assistantId = "a"))
                ))
            ),
        )
    }

    private fun configWithRole() = GroupConfig(
        roles = listOf(
            GroupRole(
                id = "r1",
                name = "甲",
                assistantId = "assistant-1",
            )
        ),
        tokenBudgetPerRound = 2000,
    )
}