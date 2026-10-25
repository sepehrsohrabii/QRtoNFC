package com.qrtonfc.nfc

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.qrtonfc.flow.FailReason
import com.qrtonfc.flow.Problem
import com.qrtonfc.flow.VerifyReport
import com.qrtonfc.flow.WrittenTag

sealed interface WriteResult {
    class Ok(val written: WrittenTag) : WriteResult
    class Error(val problem: Problem) : WriteResult
}

sealed interface VerifyResult {
    class Done(val report: VerifyReport) : VerifyResult
    class Error(val problem: Problem) : VerifyResult
}

/**
 * Blocking NFC I/O, kept as short as possible. Every operation closes its connection before
 * returning: holding a tag open, or polling it, can stop some NFC controllers from discovering
 * the next tap.
 */
object NfcOps {

    fun write(tag: Tag, message: NdefMessage, lock: Boolean, onLocking: () -> Unit): WriteResult {
        val ndef = Ndef.get(tag)
        return try {
            if (ndef != null) writeNdef(tag, ndef, message, lock, onLocking) else formatAndWrite(tag, message, lock, onLocking)
        } catch (e: TagLostException) {
            WriteResult.Error(Problem(FailReason.TAG_LOST))
        } catch (e: Exception) {
            WriteResult.Error(Problem(FailReason.WRITE_ERROR, e.message))
        } finally {
            runCatching { ndef?.close() }
        }
    }

    private fun writeNdef(tag: Tag, ndef: Ndef, message: NdefMessage, lock: Boolean, onLocking: () -> Unit): WriteResult {
        ndef.connect()
        if (!ndef.isWritable) {
            return WriteResult.Error(Problem(FailReason.TAG_READ_ONLY))
        }
        val size = message.byteArrayLength
        if (size > ndef.maxSize) {
            return WriteResult.Error(Problem(FailReason.TOO_LARGE, "Needs $size B, this tag holds ${ndef.maxSize} B"))
        }
        ndef.writeNdefMessage(message)

        var lockApplied = false
        if (lock) {
            if (!ndef.canMakeReadOnly()) {
                return WriteResult.Error(Problem(FailReason.LOCK_UNSUPPORTED, "Data was written but the tag is still writable"))
            }
            onLocking()
            lockApplied = ndef.makeReadOnly()
        }
        return WriteResult.Ok(WrittenTag(tag.id ?: ByteArray(0), techName(ndef.type), ndef.maxSize, lock, lockApplied))
    }

    private fun formatAndWrite(tag: Tag, message: NdefMessage, lock: Boolean, onLocking: () -> Unit): WriteResult {
        val formatable = NdefFormatable.get(tag)
            ?: return WriteResult.Error(Problem(FailReason.UNSUPPORTED_TAG, tag.techList.joinToString { it.substringAfterLast('.') }))
        formatable.connect()
        try {
            if (lock) {
                onLocking()
                formatable.formatReadOnly(message)
            } else {
                formatable.format(message)
            }
        } finally {
            runCatching { formatable.close() }
        }
        return WriteResult.Ok(WrittenTag(tag.id ?: ByteArray(0), "NDEF (formatted)", 0, lock, lock))
    }

    fun verify(tag: Tag, expected: NdefMessage, written: WrittenTag): VerifyResult {
        val id = tag.id ?: ByteArray(0)
        if (id.isNotEmpty() && written.id.isNotEmpty() && !id.contentEquals(written.id)) {
            return VerifyResult.Error(Problem(FailReason.DIFFERENT_TAG, "UID ${id.toHex()}"))
        }
        val ndef = Ndef.get(tag) ?: return VerifyResult.Error(Problem(FailReason.UNSUPPORTED_TAG))
        return try {
            ndef.connect()
            val actual = ndef.ndefMessage
            val report = VerifyReport(
                dataMatches = NdefBuilder.sameContent(actual, expected),
                lockRequested = written.lockRequested,
                isLocked = !ndef.isWritable,
                uid = id.toHex(),
                tech = techName(ndef.type),
                used = actual?.byteArrayLength ?: 0,
                capacity = ndef.maxSize,
            )
            VerifyResult.Done(report)
        } catch (e: TagLostException) {
            VerifyResult.Error(Problem(FailReason.TAG_LOST))
        } catch (e: Exception) {
            VerifyResult.Error(Problem(FailReason.READ_ERROR, e.message))
        } finally {
            runCatching { ndef.close() }
        }
    }

    private fun techName(type: String?): String = when (type) {
        Ndef.NFC_FORUM_TYPE_1 -> "NFC Type 1"
        Ndef.NFC_FORUM_TYPE_2 -> "NFC Type 2"
        Ndef.NFC_FORUM_TYPE_3 -> "NFC Type 3"
        Ndef.NFC_FORUM_TYPE_4 -> "NFC Type 4"
        Ndef.MIFARE_CLASSIC -> "MIFARE Classic"
        null -> "NDEF"
        else -> type.substringAfterLast('.')
    }
}

fun ByteArray.toHex(): String = joinToString(":") { "%02X".format(it) }
