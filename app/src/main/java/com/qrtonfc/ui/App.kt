package com.qrtonfc.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.PortableWifiOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qrtonfc.flow.FlowState
import com.qrtonfc.flow.FlowViewModel
import com.qrtonfc.flow.stage
import com.qrtonfc.scan.QrStabilizer
import com.qrtonfc.ui.hero.AuroraBackground
import com.qrtonfc.ui.hero.BusyHero
import com.qrtonfc.ui.hero.FailHero
import com.qrtonfc.ui.hero.LockHero
import com.qrtonfc.ui.hero.ScanFrame
import com.qrtonfc.ui.hero.SuccessHero
import com.qrtonfc.ui.hero.TapHero
import com.qrtonfc.ui.theme.Palette
import com.qrtonfc.ui.theme.QrToNfcTheme
import com.qrtonfc.ui.theme.accentFor
import com.qrtonfc.ui.theme.secondaryFor

enum class NfcStatus { READY, DISABLED, MISSING }

@Composable
fun App(viewModel: FlowViewModel, nfcStatus: NfcStatus) {
    QrToNfcTheme {
        val context = LocalContext.current
        var cameraGranted by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }
        LaunchedEffect(Unit) { if (!cameraGranted) launcher.launch(Manifest.permission.CAMERA) }

        Box(Modifier.fillMaxSize().background(Palette.Bg)) {
            when {
                nfcStatus == NfcStatus.MISSING -> Blocker(
                    Icons.Rounded.PortableWifiOff, "No NFC on this device",
                    "This app writes NFC tags, so it needs a phone with NFC hardware.", null, null,
                )
                nfcStatus == NfcStatus.DISABLED -> Blocker(
                    Icons.Rounded.Nfc, "Turn on NFC",
                    "We'll continue automatically as soon as NFC is on.", "Open NFC settings",
                ) { context.startActivity(Intent(SystemSettings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                !cameraGranted -> Blocker(
                    Icons.Rounded.CameraAlt, "Camera access",
                    "The camera reads the QR code. Nothing is recorded or uploaded.", "Allow camera",
                ) { launcher.launch(Manifest.permission.CAMERA) }
                else -> FlowScreen(viewModel)
            }
        }
    }
}

@Composable
private fun FlowScreen(viewModel: FlowViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val motion = rememberMotionEnabled()

    val stage = state.stage
    val accent by animateColorAsState(accentFor(stage), tween(500), label = "accent")
    val secondary by animateColorAsState(secondaryFor(stage), tween(700), label = "secondary")
    val scanning = state == FlowState.Scanning

    var torch by rememberSaveable { mutableStateOf(false) }
    var box by remember { mutableStateOf<Rect?>(null) }
    var showLockWarning by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    val stabilizer = remember { QrStabilizer() }

    LaunchedEffect(scanning) {
        if (scanning) { stabilizer.reset(); box = null } else torch = false
    }

    val cameraAlpha by animateFloatAsState(if (scanning) 1f else 0f, tween(450), label = "cam")

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(accent, secondary, motion, Modifier.fillMaxSize())

        CameraLayer(
            active = scanning,
            torch = torch,
            onFrame = { value, rect ->
                box = rect
                stabilizer.offer(value)?.let(viewModel::onQrDetected)
            },
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = cameraAlpha },
        )
        if (cameraAlpha > 0.01f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = cameraAlpha }) {
                ScanFrame(box, accent, motion, Modifier.fillMaxSize())
            }
        }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(state, settings.lockEnabled, accent, onClose = viewModel::reset)
            Spacer(Modifier.weight(1f))

            AnimatedContent(
                targetState = heroKey(state),
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn(tween(380), initialScale = 0.82f)) togetherWith
                        (fadeOut(tween(200)) + scaleOut(tween(250), targetScale = 1.1f))
                },
                label = "hero",
            ) { key ->
                when (key) {
                    HeroKey.NONE -> Spacer(Modifier.size(280.dp))
                    HeroKey.TAP_WRITE -> TapHero(accent, Icons.Rounded.Nfc, dashed = false, waitingForLift = false, motion = motion)
                    HeroKey.WRITING -> BusyHero(accent, Icons.Rounded.Nfc, motion)
                    HeroKey.LOCKING -> LockHero(accent)
                    HeroKey.LIFT -> TapHero(accent, Icons.Rounded.Search, dashed = true, waitingForLift = true, motion = motion)
                    HeroKey.TAP_VERIFY -> TapHero(accent, Icons.Rounded.Search, dashed = true, waitingForLift = false, motion = motion)
                    HeroKey.VERIFYING -> BusyHero(accent, Icons.Rounded.Search, motion)
                    HeroKey.SUCCESS -> SuccessHero(accent)
                    HeroKey.FAILED -> FailHero(accent)
                }
            }

            Spacer(Modifier.weight(1f))
            InfoCard(state, accent, settings.autoLoop, onScanNext = viewModel::reset)
            Spacer(Modifier.height(14.dp))
            ChipRow(
                lockEnabled = settings.lockEnabled,
                torch = torch,
                showTorch = scanning,
                written = history.size,
                accent = accent,
                onLock = { enable ->
                    if (enable && !settings.lockWarningSeen) showLockWarning = true else viewModel.setLock(enable)
                },
                onTorch = { torch = !torch },
                onMore = { showSheet = true },
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showLockWarning) {
        LockWarningDialog(
            onConfirm = { showLockWarning = false; viewModel.setLock(true) },
            onDismiss = { showLockWarning = false },
        )
    }
    if (showSheet) {
        SessionSheet(
            settings = settings,
            history = history,
            onAutoLoop = { viewModel.setAutoLoop(it) },
            onRawText = { viewModel.setRawText(it) },
            onDismiss = { showSheet = false },
        )
    }
}

private enum class HeroKey { NONE, TAP_WRITE, WRITING, LOCKING, LIFT, TAP_VERIFY, VERIFYING, SUCCESS, FAILED }

private fun heroKey(state: FlowState): HeroKey = when (state) {
    FlowState.Scanning -> HeroKey.NONE
    is FlowState.AwaitWriteTap -> HeroKey.TAP_WRITE
    is FlowState.Writing -> HeroKey.WRITING
    is FlowState.Locking -> HeroKey.LOCKING
    is FlowState.AwaitVerifyTap -> if (state.lifted) HeroKey.TAP_VERIFY else HeroKey.LIFT
    is FlowState.Verifying -> HeroKey.VERIFYING
    is FlowState.Success -> HeroKey.SUCCESS
    is FlowState.Failed -> HeroKey.FAILED
}

@Composable
private fun TopBar(state: FlowState, lockEnabled: Boolean, accent: Color, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "QR→NFC",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
            color = Palette.Text,
        )
        Spacer(Modifier.weight(1f))
        ProgressRail(state.stage, lockEnabled, accent)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (state != FlowState.Scanning) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(40.dp).background(Palette.Glass.copy(alpha = 0.12f), CircleShape),
                ) { Icon(Icons.Rounded.Close, "Cancel and scan again", tint = Palette.Text) }
            }
        }
    }
}

@Composable
private fun Blocker(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: String?,
    onAction: (() -> Unit)?,
) {
    Box(Modifier.fillMaxSize()) {
        AuroraBackground(Palette.Violet, Palette.Cyan, motion = rememberMotionEnabled(), modifier = Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(96.dp).background(Palette.Glass.copy(alpha = 0.12f), CircleShape)
                    .border(1.dp, Palette.GlassBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Palette.Text, modifier = Modifier.size(44.dp)) }
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.displaySmall, color = Palette.Text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim, textAlign = TextAlign.Center)
            if (action != null && onAction != null) {
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Cyan, contentColor = Palette.Bg),
                ) { Text(action, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
            }
        }
    }
}

@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        SystemSettings.Global.getFloat(context.contentResolver, SystemSettings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}
