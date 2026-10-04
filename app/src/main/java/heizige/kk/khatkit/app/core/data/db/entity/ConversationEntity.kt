package heizige.kk.khatkit.app.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("assistant_id", defaultValue = "0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
    val assistantId: String,
    @ColumnInfo("title")
    val title: String,
    @ColumnInfo("nodes")
    val nodes: String,
    @ColumnInfo("create_at")
    val createAt: Long,
    @ColumnInfo("update_at")
    val updateAt: Long,
    @ColumnInfo("suggestions", defaultValue = "[]")
    val chatSuggestions: String,
    @ColumnInfo("is_pinned", defaultValue = "0")
    val isPinned: Boolean,
    @ColumnInfo("custom_system_prompt", defaultValue = "")
    val customSystemPrompt: String = "",
    @ColumnInfo("mode_injection_ids", defaultValue = "[]")
    val modeInjectionIds: String = "[]",
    @ColumnInfo("lorebook_ids", defaultValue = "[]")
    val lorebookIds: String = "[]",
    @ColumnInfo("workspace_cwd", defaultValue = "")
    val workspaceCwd: String = "",
    @ColumnInfo("folder_id", defaultValue = "")
    val folderId: String = "",
    @ColumnInfo("type", defaultValue = "DIRECT")
    val type: String = "DIRECT",
    @ColumnInfo("group_config", defaultValue = "")
    val groupConfig: String = "",
    /**
     * 群聊导入时带进来的角色卡元数据（`List<RoleCardMeta>` 的 JSON 数组字符串）。
     *
     * 空串 = 这一列没有角色卡（Room 31 及更早版本的存量行迁移后就是这个值），
     * `"[]"` = 导入过但载荷里一张卡都没有，两者语义不同，别合并。
     * 独立成列而不是塞进 `group_config`：`cards` 是「导入时带进来的附属快照」，
     * `group_config` 是「用户可编辑的活配置」，且后者会被 `encodeQr` 带走，
     * persona 进去会把分享载荷撑爆。
     *
     * `TEXT NOT NULL DEFAULT ''` + 实体同款 `defaultValue`：Room 的 `TableInfo` 校验会逐项
     * 比对列默认值，两边不一致会在真机 31→32 升级那一刻直接抛
     * `Migration didn't properly handle`。DDL 见 `Migration_31_32.kt`。
     */
    @ColumnInfo("group_cards", defaultValue = "")
    val groupCards: String = "",
)
