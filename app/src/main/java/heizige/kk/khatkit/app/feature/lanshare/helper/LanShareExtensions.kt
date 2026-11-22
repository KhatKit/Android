package heizige.kk.khatkit.app.feature.lanshare.helper

import java.net.Inet4Address
import java.net.InetAddress

internal fun String.lanHtml(): String = buildString(length) {
    for (character in this@lanHtml) append(when (character) {
        '&' -> "&amp;"
        '<' -> "&lt;"
        '>' -> "&gt;"
        '"' -> "&quot;"
        '\'' -> "&#39;"
        else -> character.toString()
    })
}

internal fun String.lanAttachmentHeader(): String = "attachment; filename=\"download\"; filename*=UTF-8''" +
    encodeToByteArray().joinToString("") { byte ->
        val i = byte.toInt() and 255
        val c = i.toChar()
        if (c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z' || c in "-._~") c.toString()
        else "%" + i.toString(16).padStart(2, '0')
    }

internal fun InetAddress.isLanShareAddress(): Boolean =
    this is Inet4Address && !isLoopbackAddress && (isSiteLocalAddress || isLinkLocalAddress)
