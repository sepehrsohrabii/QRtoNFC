package com.qrtonfc.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.qrtonfc.scan.QrPayload

object NdefBuilder {

    /** Builds the message for [payload]; [rawText] forces a plain Text record for everything. */
    fun build(payload: QrPayload, rawText: Boolean): NdefMessage {
        val record = if (rawText) {
            NdefRecord.createTextRecord("en", payload.raw)
        } else when (payload) {
            is QrPayload.Uri -> NdefRecord.createUri(payload.raw)
            is QrPayload.Wifi -> NdefRecord.createMime(WifiWsc.MIME_TYPE, WifiWsc.encode(payload))
            is QrPayload.VCard -> NdefRecord.createMime("text/vcard", payload.raw.toByteArray(Charsets.UTF_8))
            is QrPayload.Text -> NdefRecord.createTextRecord("en", payload.raw)
        }
        return NdefMessage(record)
    }

    /** Record-by-record comparison of TNF, type and payload. */
    fun sameContent(a: NdefMessage?, b: NdefMessage?): Boolean {
        if (a == null || b == null) return false
        if (a.records.size != b.records.size) return false
        return a.records.indices.all { i ->
            val x = a.records[i]
            val y = b.records[i]
            x.tnf == y.tnf && x.type.contentEquals(y.type) && x.payload.contentEquals(y.payload)
        }
    }
}

/** Typical usable NDEF capacity of common NTAG chips, to tell the user which tag fits. */
enum class TagSize(val label: String, val bytes: Int) {
    NTAG213("NTAG213", 137),
    NTAG215("NTAG215", 496),
    NTAG216("NTAG216", 868);

    companion object {
        fun smallestFitting(size: Int): TagSize? = entries.firstOrNull { size <= it.bytes }
    }
}
