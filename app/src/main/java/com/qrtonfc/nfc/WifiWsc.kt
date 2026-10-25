package com.qrtonfc.nfc

import com.qrtonfc.scan.QrPayload
import java.io.ByteArrayOutputStream

/**
 * Encodes Wi-Fi credentials as a Wi-Fi Simple Config (WSC) credential, the payload of an
 * `application/vnd.wfa.wsc` NDEF record. Android joins the network when such a tag is tapped.
 */
object WifiWsc {
    const val MIME_TYPE = "application/vnd.wfa.wsc"

    private const val CREDENTIAL = 0x100E
    private const val NETWORK_INDEX = 0x1026
    private const val SSID = 0x1045
    private const val AUTH_TYPE = 0x1003
    private const val ENCRYPTION_TYPE = 0x100F
    private const val NETWORK_KEY = 0x1027
    private const val MAC_ADDRESS = 0x1020

    private const val AUTH_OPEN = 0x0001
    private const val AUTH_WPA2_PSK = 0x0020
    private const val ENC_NONE = 0x0001
    private const val ENC_WEP = 0x0002
    private const val ENC_AES = 0x0008

    fun encode(wifi: QrPayload.Wifi): ByteArray {
        val (auth, enc) = when (wifi.security) {
            QrPayload.Wifi.Security.OPEN -> AUTH_OPEN to ENC_NONE
            QrPayload.Wifi.Security.WEP -> AUTH_OPEN to ENC_WEP
            QrPayload.Wifi.Security.WPA -> AUTH_WPA2_PSK to ENC_AES
        }
        val credential = ByteArrayOutputStream().apply {
            tlv(NETWORK_INDEX, byteArrayOf(1))
            tlv(SSID, wifi.ssid.toByteArray(Charsets.UTF_8))
            tlv(AUTH_TYPE, u16(auth))
            tlv(ENCRYPTION_TYPE, u16(enc))
            tlv(NETWORK_KEY, wifi.password.toByteArray(Charsets.UTF_8))
            tlv(MAC_ADDRESS, ByteArray(6) { 0xFF.toByte() })
        }.toByteArray()
        return ByteArrayOutputStream().apply { tlv(CREDENTIAL, credential) }.toByteArray()
    }

    private fun u16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())

    private fun ByteArrayOutputStream.tlv(type: Int, value: ByteArray) {
        write(u16(type))
        write(u16(value.size))
        write(value)
    }
}
