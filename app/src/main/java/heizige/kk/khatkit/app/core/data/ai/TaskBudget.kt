package heizige.kk.khatkit.app.core.data.ai

data class BudgetLimits(
    val dailyTokens: Long = Long.MAX_VALUE,
    val monthlyTokens: Long = Long.MAX_VALUE,
    val dailyCost: Double = Double.MAX_VALUE,
    val monthlyCost: Double = Double.MAX_VALUE,
)

data class BudgetUsage(
    val dailyTokens: Long = 0,
    val monthlyTokens: Long = 0,
    val dailyCost: Double = 0.0,
    val monthlyCost: Double = 0.0,
)

enum class BudgetAction {
    ALLOW,
    DEGRADE,
    READ_ONLY,
}

fun budgetAction(usage: BudgetUsage, limits: BudgetLimits, cheaperAvailable: Boolean): BudgetAction {
    val over = usage.dailyTokens >= limits.dailyTokens ||
        usage.monthlyTokens >= limits.monthlyTokens ||
        usage.dailyCost >= limits.dailyCost ||
        usage.monthlyCost >= limits.monthlyCost
    if (!over) return BudgetAction.ALLOW
    return if (cheaperAvailable) BudgetAction.DEGRADE else BudgetAction.READ_ONLY
}
