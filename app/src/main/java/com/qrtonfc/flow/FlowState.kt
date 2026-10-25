package com.qrtonfc.flow

import android.nfc.NdefMessage
import com.qrtonfc.scan.QrPayload

/** A QR scan ready to be written: the parsed payload plus the exact message we will write. */
data class Job(val payload: QrPayload, val message: NdefMessage) {
    val size: Int get() = message.byteArrayLength
}

/** What the first tap left on the tag, carried into the verify step. */
class WrittenTag(
    val id: ByteArray,
    val tech: String,
    val capacity: Int,
    val lockRequested: Boolean,
    val lockApplied: Boolean,
)

data class VerifyReport(
    val dataMatches: Boolean,
    val lockRequested: Boolean,
    val isLocked: Boolean,
    val uid: String,
    val tech: String,
    val used: Int,
    val capacity: Int,
) {
    val passed: Boolean get() = dataMatches && (!lockRequested || isLocked)
}

enum class FailReason(val title: String, val hint: String) {
    TAG_LOST("Tag moved away", "Hold it still a little longer"),
    UNSUPPORTED_TAG("Tag not supported", "Use an NTAG21x or other NDEF tag"),
    TAG_READ_ONLY("Tag is locked", "This tag is read-only. Use a fresh one"),
    TOO_LARGE("Tag is too small", "Use a tag with more memory"),
    LOCK_UNSUPPORTED("Couldn't lock", "This tag type can't be made read-only"),
    WRITE_ERROR("Write failed", "Tap again to retry"),
    DIFFERENT_TAG("Different tag", "Tap the tag you just wrote"),
    DATA_MISMATCH("Data doesn't match", "Tap the tag to write it again"),
    NOT_LOCKED("Tag isn't locked", "Tap the tag to write and lock again"),
    READ_ERROR("Couldn't read tag", "Tap again to retry"),
}

sealed interface FlowState {
    data object Scanning : FlowState

    data class AwaitWriteTap(val job: Job, val problem: Problem? = null) : FlowState
    data class Writing(val job: Job) : FlowState
    data class Locking(val job: Job) : FlowState

    /** [ready] flips shortly after the write, once the user has had time to lift the tag. */
    data class AwaitVerifyTap(
        val job: Job,
        val written: WrittenTag,
        val ready: Boolean = false,
        val problem: Problem? = null,
    ) : FlowState

    data class Verifying(val job: Job, val written: WrittenTag) : FlowState
    data class Success(val job: Job, val report: VerifyReport) : FlowState

    /** Verification failed: the next tap rewrites the same job. */
    data class Failed(val job: Job, val reason: FailReason, val report: VerifyReport?) : FlowState
}

/** A recoverable problem shown inline while we keep waiting for the next tap. */
data class Problem(val reason: FailReason, val detail: String? = null)

/** Which step of the journey a state belongs to, for the progress rail and colors. */
enum class Stage { SCAN, WRITE, LOCK, VERIFY, DONE, FAILED }

val FlowState.stage: Stage
    get() = when (this) {
        FlowState.Scanning -> Stage.SCAN
        is FlowState.AwaitWriteTap, is FlowState.Writing -> Stage.WRITE
        is FlowState.Locking -> Stage.LOCK
        is FlowState.AwaitVerifyTap, is FlowState.Verifying -> Stage.VERIFY
        is FlowState.Success -> Stage.DONE
        is FlowState.Failed -> Stage.FAILED
    }

val FlowState.job: Job?
    get() = when (this) {
        FlowState.Scanning -> null
        is FlowState.AwaitWriteTap -> job
        is FlowState.Writing -> job
        is FlowState.Locking -> job
        is FlowState.AwaitVerifyTap -> job
        is FlowState.Verifying -> job
        is FlowState.Success -> job
        is FlowState.Failed -> job
    }
