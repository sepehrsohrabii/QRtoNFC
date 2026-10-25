package com.qrtonfc.scan

/** What a scanned QR code contains, parsed into the shape we will encode on the tag. */
sealed interface QrPayload {
    val raw: String

    data class Uri(override val raw: String) : QrPayload

    data class Wifi(
        override val raw: String,
        val ssid: String,
        val password: String,
        val security: Security,
        val hidden: Boolean,
    ) : QrPayload {
        enum class Security { OPEN, WEP, WPA }
    }

    data class VCard(override val raw: String) : QrPayload

    data class Text(override val raw: String) : QrPayload
}

object QrParser {

    private val uriSchemes = setOf(
        "http", "https", "tel", "mailto", "sms", "smsto", "geo", "market",
        "ftp", "sftp", "file", "intent", "whatsapp", "tg", "spotify",
    )
    private val schemeRegex = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")

    fun parse(raw: String): QrPayload {
        val text = raw.trim()
        if (text.startsWith("WIFI:", ignoreCase = true)) {
            parseWifi(text)?.let { return it }
        }
        if (text.startsWith("BEGIN:VCARD", ignoreCase = true)) return QrPayload.VCard(text)

        val scheme = schemeRegex.find(text)?.groupValues?.get(1)?.lowercase()
        if (scheme != null && scheme in uriSchemes && text.none { it.isWhitespace() }) {
            return QrPayload.Uri(text)
        }
        return QrPayload.Text(raw)
    }

    /** Parses `WIFI:T:WPA;S:name;P:secret;H:true;;`, honoring backslash escapes. */
    fun parseWifi(text: String): QrPayload.Wifi? {
        val body = text.substring(5)
        val fields = mutableMapOf<String, String>()
        val current = StringBuilder()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length -> {
                    current.append(body[i + 1]); i += 2; continue
                }
                c == ';' -> {
                    val field = current.toString()
                    val colon = field.indexOf(':')
                    if (colon > 0) fields[field.substring(0, colon).uppercase()] = field.substring(colon + 1)
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        if (current.isNotEmpty()) {
            val field = current.toString()
            val colon = field.indexOf(':')
            if (colon > 0) fields[field.substring(0, colon).uppercase()] = field.substring(colon + 1)
        }

        val ssid = fields["S"] ?: return null
        val security = when (fields["T"]?.uppercase()) {
            null, "", "NOPASS", "NONE" -> QrPayload.Wifi.Security.OPEN
            "WEP" -> QrPayload.Wifi.Security.WEP
            else -> QrPayload.Wifi.Security.WPA
        }
        return QrPayload.Wifi(
            raw = text,
            ssid = ssid,
            password = fields["P"].orEmpty(),
            security = security,
            hidden = fields["H"].equals("true", ignoreCase = true),
        )
    }
}
