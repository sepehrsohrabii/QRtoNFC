package com.qrtonfc.flow

import android.app.Application
import android.nfc.Tag
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qrtonfc.data.Settings
import com.qrtonfc.data.SettingsStore
import com.qrtonfc.feedback.Cue
import com.qrtonfc.feedback.Feedback
import com.qrtonfc.nfc.NdefBuilder
import com.qrtonfc.nfc.NfcOps
import com.qrtonfc.nfc.VerifyResult
import com.qrtonfc.nfc.WriteResult
import com.qrtonfc.scan.QrParser
import com.qrtonfc.scan.QrPayload
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job as CoroutineJob

data class HistoryEntry(val payload: QrPayload, val report: VerifyReport, val at: Long)

/**
 * The whole app is this state machine. Every transition is triggered by an event
 * (QR decoded, tag tapped, timer) so the user never has to press a button.
 */
class FlowViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val feedback = Feedback(app)

    val settings: StateFlow<Settings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _state = MutableStateFlow<FlowState>(FlowState.Scanning)
    val state: StateFlow<FlowState> = _state.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val busy = AtomicBoolean(false)
    private var timerJob: CoroutineJob? = null

    // The last tag we finished talking to. Restarting NFC discovery (e.g. on resume) makes
    // Android report a tag that is still on the phone again; that is not a new tap.
    @Volatile private var lastTagId: ByteArray? = null
    @Volatile private var lastTagDoneAt = 0L

    /** Called by the camera analyzer with a stable QR value. */
    fun onQrDetected(raw: String) {
        if (_state.value != FlowState.Scanning) return
        val payload = QrParser.parse(raw)
        val message = NdefBuilder.build(payload, settings.value.rawText)
        _state.value = FlowState.AwaitWriteTap(Job(payload, message))
        feedback.play(Cue.SCANNED)
    }

    /** Called for every tag that enters the field, from reader mode or foreground dispatch. */
    fun onTag(tag: Tag) {
        if (isRediscovery(tag)) return
        if (!busy.compareAndSet(false, true)) return
        try {
            when (val s = _state.value) {
                is FlowState.AwaitWriteTap -> write(tag, s.job)
                is FlowState.Failed -> write(tag, s.job)
                is FlowState.AwaitVerifyTap -> verify(tag, s)
                else -> Unit
            }
        } finally {
            lastTagId = tag.id
            lastTagDoneAt = SystemClock.elapsedRealtime()
            busy.set(false)
        }
    }

    private fun isRediscovery(tag: Tag): Boolean {
        val id = tag.id ?: return false
        val last = lastTagId ?: return false
        return id.isNotEmpty() && id.contentEquals(last) &&
            SystemClock.elapsedRealtime() - lastTagDoneAt < REDISCOVERY_WINDOW_MS
    }

    private fun write(tag: Tag, job: Job) {
        cancelJobs()
        _state.value = FlowState.Writing(job)
        val lock = settings.value.lockEnabled

        when (val r = NfcOps.write(tag, job.message, lock) { _state.value = FlowState.Locking(job) }) {
            is WriteResult.Ok -> {
                val locked = r.written.lockApplied
                feedback.play(if (locked) Cue.LOCKED else Cue.WRITTEN)
                if (locked) _state.value = FlowState.Locking(job)
                else _state.value = FlowState.AwaitVerifyTap(job, r.written)
                timerJob = viewModelScope.launch {
                    if (locked) {
                        // Let the padlock animation land before asking for the verify tap.
                        delay(LOCK_SHOW_MS)
                        _state.update { s -> if (s is FlowState.Locking) FlowState.AwaitVerifyTap(job, r.written) else s }
                    }
                    delay(LIFT_HINT_MS)
                    _state.update { s -> if (s is FlowState.AwaitVerifyTap && !s.ready) s.copy(ready = true) else s }
                }
            }
            is WriteResult.Error -> {
                feedback.play(Cue.ERROR)
                _state.value = FlowState.AwaitWriteTap(job, r.problem)
            }
        }
    }

    private fun verify(tag: Tag, s: FlowState.AwaitVerifyTap) {
        _state.value = FlowState.Verifying(s.job, s.written)
        when (val r = NfcOps.verify(tag, s.job.message, s.written)) {
            is VerifyResult.Done -> {
                val report = r.report
                if (report.passed) {
                    feedback.play(Cue.SUCCESS)
                    _state.value = FlowState.Success(s.job, report)
                    _history.update { listOf(HistoryEntry(s.job.payload, report, System.currentTimeMillis())) + it }
                    if (settings.value.autoLoop) {
                        timerJob = viewModelScope.launch {
                            delay(AUTO_LOOP_MS)
                            _state.update { if (it is FlowState.Success) FlowState.Scanning else it }
                        }
                    }
                } else {
                    feedback.play(Cue.ERROR)
                    val reason = if (!report.dataMatches) FailReason.DATA_MISMATCH else FailReason.NOT_LOCKED
                    _state.value = FlowState.Failed(s.job, reason, report)
                }
            }
            is VerifyResult.Error -> {
                feedback.play(Cue.ERROR)
                _state.value = s.copy(ready = true, problem = r.problem)
            }
        }
    }

    /** Back to scanning, from anywhere. */
    fun reset() {
        cancelJobs()
        _state.value = FlowState.Scanning
    }

    fun setLock(enabled: Boolean) = viewModelScope.launch {
        store.setLock(enabled)
        if (enabled) store.markLockWarningSeen()
    }

    fun setAutoLoop(enabled: Boolean) = viewModelScope.launch { store.setAutoLoop(enabled) }
    fun setRawText(enabled: Boolean) = viewModelScope.launch { store.setRawText(enabled) }

    private fun cancelJobs() {
        timerJob?.cancel(); timerJob = null
    }

    override fun onCleared() {
        cancelJobs()
        feedback.release()
    }

    companion object {
        const val AUTO_LOOP_MS = 1800L
        private const val LOCK_SHOW_MS = 750L
        private const val LIFT_HINT_MS = 1200L
        private const val REDISCOVERY_WINDOW_MS = 700L
    }
}
