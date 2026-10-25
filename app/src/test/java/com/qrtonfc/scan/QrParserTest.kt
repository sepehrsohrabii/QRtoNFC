package com.qrtonfc.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrParserTest {

    @Test fun `https link is a uri`() {
        assertTrue(QrParser.parse("https://example.com/a?b=1") is QrPayload.Uri)
    }

    @Test fun `tel and mailto are uris`() {
        assertTrue(QrParser.parse("tel:+15551234") is QrPayload.Uri)
        assertTrue(QrParser.parse("MAILTO:me@example.com") is QrPayload.Uri)
    }

    @Test fun `text with spaces after scheme-like prefix stays text`() {
        assertTrue(QrParser.parse("http: is a protocol") is QrPayload.Text)
    }

    @Test fun `unknown scheme is text`() {
        assertTrue(QrParser.parse("note:buy milk") is QrPayload.Text)
    }

    @Test fun `plain text keeps original whitespace`() {
        val p = QrParser.parse("  hello world \n")
        assertEquals("  hello world \n", p.raw)
    }

    @Test fun `vcard detected`() {
        assertTrue(QrParser.parse("BEGIN:VCARD\nVERSION:3.0\nFN:Ada\nEND:VCARD") is QrPayload.VCard)
    }

    @Test fun `wifi wpa with escapes`() {
        val w = QrParser.parse("WIFI:T:WPA;S:My\\;Net;P:pa\\:ss\\\\;H:true;;") as QrPayload.Wifi
        assertEquals("My;Net", w.ssid)
        assertEquals("pa:ss\\", w.password)
        assertEquals(QrPayload.Wifi.Security.WPA, w.security)
        assertTrue(w.hidden)
    }

    @Test fun `wifi field order does not matter and nopass is open`() {
        val w = QrParser.parse("WIFI:S:Cafe;T:nopass;;") as QrPayload.Wifi
        assertEquals("Cafe", w.ssid)
        assertEquals("", w.password)
        assertEquals(QrPayload.Wifi.Security.OPEN, w.security)
    }

    @Test fun `wifi without ssid falls back to text`() {
        assertNull(QrParser.parseWifi("WIFI:T:WPA;P:x;;"))
        assertTrue(QrParser.parse("WIFI:T:WPA;P:x;;") is QrPayload.Text)
    }
}
