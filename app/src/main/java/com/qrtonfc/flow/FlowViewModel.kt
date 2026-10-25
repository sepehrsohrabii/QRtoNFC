package com.qrtonfc.flow

import android.app.Application
import android.nfc.Tag
import android.nfc.tech.Ndef
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job as CoroutineJob

data class HistoryEntry(val payload: QrPayload, val report: VerifyReport, val at: Long)

/**
 * The whole app is this state machine. Every transition is triggered by an event
 * (QR decoded, tag tapped, tag lifted, timer) so the user never has to press a button.
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
    private var watchJob: CoroutineJob? = null
    private var timerJob: CoroutineJob? = null

    @Volatile
    private var lifted = false

    /** Called by the camera analyzer with a stable QR value. */
    fun onQrDetected(raw: String) {
        if (_state.value != FlowState.Scanning) return
        val payload = QrParser.parse(raw)
        val message = NdefBuilder.build(payload, settings.value.rawText)
        _state.value = FlowState.AwaitWriteTap(Job(payload, message))
        feedback.play(Cue.SCANNED)
    }

    /** Called from the NFC reader-mode thread for every tag that enters the field. */
    fun onTag(tag: Tag) {
        if (!busy.compareAndSet(false, true)) return
        try {
            when (val s = _state.value) {
                is FlowState.AwaitWriteTap -> write(tag, s.job)
                is FlowState.Failed -> write(tag, s.job)
                is FlowState.AwaitVerifyTap -> verify(tag, s)
                else -> Unit
            }
        } finally {
            busy.set(false)
        }
    }

    private fun write(tag: Tag, job: Job) {
        cancelJobs()
        _state.value = FlowState.Writing(job)
        val lock = settings.value.lockEnabled

        when (val r = NfcOps.write(tag, job.message, lock) { _state.value = FlowState.Locking(job) }) {
            is WriteResult.Ok -> {
                lifted = r.ndef == null
                r.ndef?.let { watchRemoval(it) }
                if (r.written.lockApplied) {
                    feedback.play(Cue.LOCKED)
                    _state.value = FlowState.Locking(job)
                    // Let the padlock animation land before asking for the verify tap.
                    timerJob = viewModelScope.launch {
                        delay(750)
                        _state.update { s ->
                            if (s is FlowState.Locking) FlowState.AwaitVerifyTap(job, r.written, lifted) else s
                        }
                    }
                } else {
                    feedback.play(Cue.WRITTEN)
                    _state.value = FlowState.AwaitVerifyTap(job, r.written, lifted)
                }
            }
            is WriteResult.Error -> {
                feedback.play(Cue.ERROR)
                _state.value = FlowState.AwaitWriteTap(job, r.problem)
            }
        }
    }

    private fun watchRemoval(ndef: Ndef) {
        watchJob = viewModelScope.launch(Dispatchers.IO) {
            NfcOps.awaitRemoval(ndef) { !isActive }
            if (!isActive) return@launch
            lifted = true
            feedback.play(Cue.LIFTED)
            _state.update { s -> if (s is FlowState.AwaitVerifyTap) s.copy(lifted = true) else s }
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
                _state.value = s.copy(lifted = true, problem = r.problem)
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
        watchJob?.cancel(); watchJob = null
        timerJob?.cancel(); timerJob = null
    }

    override fun onCleared() {
        cancelJobs()
        feedback.release()
    }

    companion object {
        const val AUTO_LOOP_MS = 1800L
    }
}
