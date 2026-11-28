package heizige.kk.khatkit.localization

import android.content.Context
import heizige.kk.khatkit.R

fun RuntimeText.localizedMessage(context: Context): String {
    if (code == RuntimeText.Code.DEPENDENCY_TRIED_VERSIONS) {
        val count = (arguments[0] as Number).toInt()
        return context.resources.getQuantityString(R.plurals.khatkit_dependency_tried_versions, count, *arguments.toTypedArray())
    }
    return context.getString(when (code) {
        RuntimeText.Code.DEPENDENCY_HOST_UNSUPPORTED -> R.string.khatkit_dependency_host_unsupported
        RuntimeText.Code.DEPENDENCY_PREPARING -> R.string.khatkit_dependency_preparing
        RuntimeText.Code.DEPENDENCY_FALLBACK -> R.string.khatkit_dependency_fallback
        RuntimeText.Code.DEPENDENCY_UNAVAILABLE -> R.string.khatkit_dependency_unavailable
        RuntimeText.Code.DEPENDENCY_UPDATE_CARD -> R.string.khatkit_dependency_update_card
        RuntimeText.Code.DEPENDENCY_ALL_UNAVAILABLE -> R.string.khatkit_dependency_all_unavailable
        RuntimeText.Code.RUNTIME_MISSING_CAPABILITIES -> R.string.khatkit_runtime_missing_capabilities
        RuntimeText.Code.RUNTIME_BRIDGE_INJECTION -> R.string.khatkit_runtime_bridge_injection
        RuntimeText.Code.RUNTIME_COMMAND_MISSING -> R.string.khatkit_runtime_command_missing
        RuntimeText.Code.RUNTIME_COMMAND_UNAVAILABLE -> R.string.khatkit_runtime_command_unavailable
        RuntimeText.Code.RUNTIME_COMMAND_FAILED -> R.string.khatkit_runtime_command_failed
        RuntimeText.Code.HUB_CATALOG_UNAVAILABLE -> R.string.khatkit_hub_catalog_unavailable
        RuntimeText.Code.HUB_TOKEN_EXPIRED -> R.string.khatkit_hub_token_expired
        RuntimeText.Code.HUB_RATE_LIMITED -> R.string.khatkit_hub_rate_limited
        RuntimeText.Code.HUB_QUOTA_INSUFFICIENT -> R.string.khatkit_hub_quota_insufficient
        RuntimeText.Code.HUB_QUOTA_DENIED -> R.string.khatkit_hub_quota_denied
        RuntimeText.Code.HUB_CALL_DENIED -> R.string.khatkit_hub_call_denied
        RuntimeText.Code.HUB_RESPONSE_INVALID -> R.string.khatkit_hub_response_invalid
        RuntimeText.Code.HUB_ITEMS_MISSING -> R.string.khatkit_hub_items_missing
        RuntimeText.Code.HUB_NETWORK_ERROR -> R.string.khatkit_hub_network_error
        RuntimeText.Code.HUB_BAD_REQUEST -> R.string.khatkit_hub_bad_request
        RuntimeText.Code.HUB_ENTITLEMENT_UNAVAILABLE -> R.string.khatkit_hub_entitlement_unavailable
        RuntimeText.Code.HUB_ENDPOINT_MISSING -> R.string.khatkit_hub_endpoint_missing
        RuntimeText.Code.HUB_REQUEST_TIMEOUT -> R.string.khatkit_hub_request_timeout
        RuntimeText.Code.HUB_SERVER_ERROR -> R.string.khatkit_hub_server_error
        RuntimeText.Code.HUB_REQUEST_FAILED -> R.string.khatkit_hub_request_failed
        RuntimeText.Code.DEPENDENCY_TRIED_VERSIONS -> error("unreachable plural code")
    }, *arguments.toTypedArray())
}
