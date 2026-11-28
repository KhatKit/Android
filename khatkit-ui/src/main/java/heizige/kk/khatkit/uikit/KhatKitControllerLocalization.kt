package heizige.kk.khatkit.uikit

import android.content.Context
import heizige.kk.khatkit.hub.HubAccountStatus
import heizige.kk.khatkit.hub.HubActionResult

/** Structured presentation failure for headless hosts: no text or Context is retained. */
class KhatKitUiMessageException(@androidx.annotation.StringRes val messageRes: Int) : IllegalStateException()

private suspend fun <T> localizedHostCall(
    context: Context,
    failure: (String) -> T,
    call: suspend () -> T,
): T = try {
    call()
} catch (error: KhatKitUiMessageException) {
    failure(context.getString(error.messageRes))
}

/** Context-taking UI adapters also handle default, headless controller implementations. */
suspend fun KhatKitController.hubAccountStatus(context: Context, refresh: Boolean = false): HubAccountStatus =
    localizedHostCall(context, { HubAccountStatus(message = it) }) { hubAccountStatus(refresh) }

suspend fun KhatKitController.activateHub(context: Context, token: String, hubBaseUrl: String): HubAccountStatus =
    localizedHostCall(context, { HubAccountStatus(message = it) }) { activateHub(token, hubBaseUrl) }

suspend fun KhatKitController.publishCard(
    context: Context, name: String, license: String, price: Double, changelog: String = "",
): HubActionResult = localizedHostCall(context, { HubActionResult(false, it) }) {
    publishCard(name, license, price, changelog)
}

suspend fun KhatKitController.forkCard(
    context: Context, name: String, parentVersion: String, changelog: String, newVersion: String = "",
): HubActionResult = localizedHostCall(context, { HubActionResult(false, it) }) {
    forkCard(name, parentVersion, changelog, newVersion)
}

suspend fun KhatKitController.voteCard(context: Context, name: String): HubActionResult =
    localizedHostCall(context, { HubActionResult(false, it) }) { voteCard(name) }
