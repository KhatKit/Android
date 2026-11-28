package heizige.kk.khatkit.app.feature.chat.helper

import heizige.kk.khatkit.app.core.data.model.GroupChat

/**
 * 抽屉 chip 的筛选值 → DAO 的 type 参数。
 *
 * `FILTER_ALL`（以及任何空白值）折成空串，即 DAO 里 `AND (:type = '' OR type = :type)`
 * 的「不筛」分支——和未归档路同一套语义，不在这里发明第二种表达。
 */
internal fun String.asConversationTypeArgument(): String =
    takeUnless { it == GroupChat.FILTER_ALL || it.isBlank() }.orEmpty()
