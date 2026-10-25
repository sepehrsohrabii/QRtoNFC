package com.qrtonfc.nfc

import com.qrtonfc.scan.QrPayload
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WifiWscTest {

    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun `encodes wpa credential`() {
        val bytes = WifiWsc.encode(QrPayload.Wifi("", "ab", "pw", QrPayload.Wifi.Security.WPA, false))
        val expected = hex(
            "100E 0027" +
                "1026 0001 01" +
                "1045 0002 6162" +
                "1003 0002 0020" +
                "100F 0002 0008" +
                "1027 0002 7077" +
                "1020 0006 FFFFFFFFFFFF"
        )
        assertArrayEquals(expected, bytes)
    }

    @Test fun `open network has empty key and no encryption`() {
        val bytes = WifiWsc.encode(QrPayload.Wifi("", "x", "", QrPayload.Wifi.Security.OPEN, false))
        // credential header + index(5) + ssid(5) + auth(6) + enc(6) + key(4) + mac(10)
        assertEquals(4 + 5 + 5 + 6 + 6 + 4 + 10, bytes.size)
    }
}
