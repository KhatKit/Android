package heizige.kk.khatkit.bridge.impl

import heizige.kk.khatkit.bridge.I18nBridge
import heizige.kk.khatkit.card.CardStrings

class CardI18nBridge(private val strings: CardStrings) : I18nBridge {
    override fun locale(): String = strings.locale
    override fun text(key: String, values: Map<String, Any?>?): String = strings.text(key, values.orEmpty())
}
