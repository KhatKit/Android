// SPDX-License-Identifier: AGPL-3.0-or-later
package heizige.kk.khatkit.app.feature.yunx

import android.content.Context
import androidx.annotation.Keep
import heizige.kk.khatkit.bridge.CardBindable
import heizige.kk.khatkit.yunx.YunxParserManager

/** Each card invocation receives an isolated parser, with no cross-card session access. */
@Keep
class YunxCardBridge(private val context: Context) : CardBindable {
    private val parser by lazy { YunxParserManager(context.applicationContext) }
    override fun bindCard(cardName: String): Any = YunxCardBridge(context)
    fun call(operation: String, input: String): String = parser.call(operation, input)
}
