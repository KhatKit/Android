package heizige.kk.khatkit.app.core.util

import heizige.kk.khatkit.ai.provider.BalanceOption
import java.math.BigDecimal

// Shared by core UI (ProviderBalanceText) and feature.balance: keep the balance presentation
// contract in core so core never imports a feature package (t63/t44).

// A bare cumulative usage field is not a balance. A formula such as credits - usage is valid.
internal val usageField = Regex("""\s*(?:[\w]+\s*\.\s*)*total_usage\s*""", RegexOption.IGNORE_CASE)
internal val BalanceOption.isUsageOnly: Boolean get() = usageField.matches(resultPath)

internal fun BalanceOption.thresholdAmount(): BigDecimal? = lowBalanceThreshold.trim()
    .replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

internal fun BalanceOption.isLow(raw: String?): Boolean? {
    if (!enabled || !lowBalanceAlert || isUsageOnly) return null
    val amount = raw?.trim()?.toBigDecimalOrNull() ?: return null
    return amount < (thresholdAmount() ?: return null)
}
