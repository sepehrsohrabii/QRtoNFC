package com.qrtonfc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
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
        // camera was in use, so re-arm reader mode once it has had time to shut down.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.state
                    .map { it != FlowState.Scanning }
                    .distinctUntilChanged()
                    .collectLatest { waitingForTags ->
                        if (waitingForTags) {
                            delay(CAMERA_RELEASE_MS)
                            // Never restart discovery while a tag is mid-write.
                            if (viewModel.state.value is FlowState.AwaitWriteTap) refreshNfc()
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
        runCatching { nfcAdapter?.disableReaderMode(this) }
    }

    override fun onDestroy() {
        unregisterReceiver(nfcStateReceiver)
        super.onDestroy()
    }

    private fun refreshNfc() {
        val adapter = nfcAdapter
        nfcStatus.value = when {
            adapter == null -> NfcStatus.MISSING
            !adapter.isEnabled -> NfcStatus.DISABLED
            else -> NfcStatus.READY
        }
        if (adapter != null && adapter.isEnabled) enableReaderMode(adapter)
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
        adapter.enableReaderMode(this, { tag -> viewModel.onTag(tag) }, flags, extras)
    }

    private companion object {
        const val CAMERA_RELEASE_MS = 400L
    }
}
