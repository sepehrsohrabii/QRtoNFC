package com.qrtonfc.scan

/**
 * Accepts a QR value only once it has been decoded on [requiredHits] consecutive frames,
 * which filters out the occasional misread without a noticeable delay.
 */
class QrStabilizer(private val requiredHits: Int = 2) {
    private var last: String? = null
    private var hits = 0

    /** Feed one frame's result (null when nothing was decoded). Returns the value once stable. */
    fun offer(value: String?): String? {
        if (value == null) {
            hits = 0; last = null
            return null
        }
        if (value == last) hits++ else { last = value; hits = 1 }
        return if (hits == requiredHits) value else null
    }

    fun reset() {
        last = null; hits = 0
    }
}
