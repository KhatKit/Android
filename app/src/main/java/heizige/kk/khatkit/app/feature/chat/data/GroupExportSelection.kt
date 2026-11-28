package heizige.kk.khatkit.app.feature.chat.data

import heizige.kk.khatkit.app.core.data.model.MessageNode

/** 群聊导出文件的扩展名。酒馆群聊文件本身就是 JSONL（每行一个 JSON 对象）。 */
internal const val GROUP_EXPORT_EXTENSION = "jsonl"

/**
 * 分享用的 mimeType。
 *
 * 依据：仓库里关于 JSON 分享 mime 的**唯一**既有约定是 `application/json`
 * （`core/data/export/ExportHooks.kt:57` 的 `ACTION_SEND` 与同文件 `:85` 的
 * `ActivityResultContracts.CreateDocument`）；全仓库检索不到任何 jsonl / ndjson 专用
 * mime 的既有用法（`grep -rn "x-ndjson\|json-seq" --include=*.kt app/src/main` 无命中），
 * Android 的 `MimeTypeMap` 也没有 `.jsonl` 条目。
 *
 * 所以这里沿用 `application/json` 而不是 `application/x-ndjson`：share chooser 是按 mime
 * 过滤候选目标的，一个多数目标不认的 mime 会让导出的文件在大部分 App 里根本不出现。
 * 扩展名仍是 `.jsonl`，扩展名与 mime 不一致在这里是**刻意**的——文件名要让人认出格式，
 * mime 要让文件能被分享出去。
 */
internal const val GROUP_EXPORT_MIME_TYPE = "application/json"

/** 群名兜底：会话没标题时用（与「存为卡片」的 `对话存档` 同一做法）。 */
internal const val GROUP_EXPORT_FALLBACK_NAME = "群聊存档"

/** 用户名兜底：昵称为空时用。与 `TavernMacroTransformer` / `PromptInjectionTransformer` 的 `User` 同一口径。 */
internal const val GROUP_EXPORT_FALLBACK_USER = "User"

/**
 * 导出面板给的 `List<UIMessage>` 反查成 `List<MessageNode>` 的结果。
 *
 * [skipped] 是**反查不到**的消息条数，必须带着走：这些消息不在当前会话的 `messageNodes` 里
 * （预览态、跨会话残留、被并发改写），静默丢掉的话用户会以为导出成功了而文件里少了东西。
 */
internal data class GroupExportSelection(
    val nodes: List<MessageNode>,
    val skipped: Int,
) {
    /** 用户一共选了多少条（成功 + 跳过）。 */
    val requested: Int get() = nodes.size + skipped

    /** 一条都没反查到。UI 见到 true 就该直接报错，不要导出空文件。 */
    val isEmpty: Boolean get() = nodes.isEmpty()
}
