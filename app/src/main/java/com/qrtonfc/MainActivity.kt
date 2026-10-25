package com.qrtonfc

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.qrtonfc.flow.FlowState
import com.qrtonfc.flow.FlowViewModel
import com.qrtonfc.ui.App
import com.qrtonfc.ui.NfcStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: FlowViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null
    private val nfcStatus = mutableStateOf(NfcStatus.READY)

    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshNfc()
        }
    }

    /** Foreground dispatch target: tags come back to this activity via [onNewIntent]. */
    private val dispatchIntent by lazy {
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        ContextCompat.registerReceiver(
            this, nfcStateReceiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent { App(viewModel, nfcStatus.value) }

        // Leaving the scan step closes the camera. Some NFC controllers stay paused after the
        // camera was in use, so restart reader mode once it has had time to shut down.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.state
                    .map { it is FlowState.AwaitWriteTap }
                    .distinctUntilChanged()
                    .collectLatest { awaitingWrite ->
                        if (awaitingWrite) {
                            delay(CAMERA_RELEASE_MS)
                            // Never restart discovery while a tag is mid-write.
                            if (viewModel.state.value is FlowState.AwaitWriteTap) refreshNfc(restart = true)
                        }
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshNfc()
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.let { adapter ->
            runCatching { adapter.disableReaderMode(this) }
            runCatching { adapter.disableForegroundDispatch(this) }
        }
    }

    /**
     * A tag that reached us through foreground dispatch, i.e. while reader mode was not active.
     * Handled synchronously (a few hundred ms at most) so the I/O is finished before
     * [onResume] re-enables reader mode, which would otherwise interrupt it.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return
        viewModel.onTag(tag)
    }

    override fun onDestroy() {
        unregisterReceiver(nfcStateReceiver)
        super.onDestroy()
    }

    private fun refreshNfc(restart: Boolean = false) {
        val adapter = nfcAdapter
        nfcStatus.value = when {
            adapter == null -> NfcStatus.MISSING
            !adapter.isEnabled -> NfcStatus.DISABLED
            else -> NfcStatus.READY
        }
        if (adapter == null || !adapter.isEnabled) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return

        if (restart) runCatching { adapter.disableReaderMode(this) }
        // Safety net: if the system drops reader mode while we are in front, tags still come
        // to us instead of opening the system "new tag" screen.
        runCatching { adapter.enableForegroundDispatch(this, dispatchIntent, null, null) }
        enableReaderMode(adapter)
    }

    /**
     * Reader mode routes every tag straight to us: no system chooser, no platform sound,
     * and the next tap is only reported after the tag has left the field.
     */
    private fun enableReaderMode(adapter: NfcAdapter) {
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        val extras = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) }
        runCatching { adapter.enableReaderMode(this, { tag -> viewModel.onTag(tag) }, flags, extras) }
    }

    private companion object {
        const val CAMERA_RELEASE_MS = 400L
    }
}
