package com.qrtonfc.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrStabilizerTest {

    @Test fun `accepts after two identical frames, once`() {
        val s = QrStabilizer()
        assertNull(s.offer("a"))
        assertEquals("a", s.offer("a"))
        assertNull(s.offer("a"))
    }

    @Test fun `a different value restarts the count`() {
        val s = QrStabilizer()
        s.offer("a")
        assertNull(s.offer("b"))
        assertEquals("b", s.offer("b"))
    }

    @Test fun `an empty frame restarts the count`() {
        val s = QrStabilizer()
        s.offer("a")
        assertNull(s.offer(null))
        assertNull(s.offer("a"))
        assertEquals("a", s.offer("a"))
    }
}
