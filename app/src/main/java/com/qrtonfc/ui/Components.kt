package com.qrtonfc.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qrtonfc.data.Settings
import com.qrtonfc.flow.FlowState
import com.qrtonfc.flow.FlowViewModel
import com.qrtonfc.flow.HistoryEntry
import com.qrtonfc.flow.Problem
import com.qrtonfc.flow.Stage
import com.qrtonfc.flow.VerifyReport
import com.qrtonfc.flow.job
import com.qrtonfc.nfc.TagSize
import com.qrtonfc.scan.QrPayload
import com.qrtonfc.ui.theme.Palette
import java.text.DateFormat
import java.util.Date

// ---------- Progress rail ----------

@Composable
fun ProgressRail(stage: Stage, lockEnabled: Boolean, accent: Color) {
    val steps = buildList {
        add(Stage.SCAN); add(Stage.WRITE)
        if (lockEnabled) add(Stage.LOCK)
        add(Stage.VERIFY)
    }
    val current = when (stage) {
        Stage.DONE -> steps.size
        Stage.FAILED -> steps.indexOf(Stage.VERIFY)
        else -> steps.indexOf(stage).coerceAtLeast(0)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, step ->
            if (i > 0) {
                val lineColor by animateColorAsState(if (i <= current) accent else Palette.GlassBorder, tween(400), label = "line")
                Box(Modifier.width(18.dp).height(2.dp).background(lineColor, CircleShape))
            }
            val done = i < current
            val active = i == current
            val dotColor by animateColorAsState(
                when {
                    stage == Stage.FAILED && active -> Palette.Coral
                    done || active -> accent
                    else -> Palette.GlassBorder
                }, tween(400), label = "dot",
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(if (active) 12.dp else 8.dp).clip(CircleShape)
                        .background(if (done || active) dotColor else Color.Transparent)
                        .border(1.5.dp, dotColor, CircleShape),
                )
            }
        }
    }
}

// ---------- Info card ----------

private data class Copy(val title: String, val subtitle: String)

private fun copyFor(state: FlowState): Copy = when (state) {
    FlowState.Scanning -> Copy("Point at a QR code", "It's captured the moment it's in focus")
    is FlowState.AwaitWriteTap -> Copy("Hold a tag to your phone", "Writing starts on contact")
    is FlowState.Writing -> Copy("Writing…", "Keep holding the tag")
    is FlowState.Locking -> Copy("Locked", "The tag is now read-only")
    is FlowState.AwaitVerifyTap ->
        if (state.ready) Copy("Tap again to verify", "We'll read it back and compare")
        else Copy("Written — lift the tag", "Then tap it once more to verify")
    is FlowState.Verifying -> Copy("Verifying…", "Comparing with the QR code")
    is FlowState.Success -> Copy("Perfect match", if (state.report.lockRequested) "Data verified · tag locked" else "Data verified")
    is FlowState.Failed -> Copy(state.reason.title, state.reason.hint)
}

@Composable
fun InfoCard(state: FlowState, accent: Color, autoLoop: Boolean, onScanNext: () -> Unit) {
    val copy = copyFor(state)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xCC121621))
            .border(1.dp, Palette.GlassBorder, RoundedCornerShape(28.dp))
            .padding(20.dp)
            .animateContentSize(tween(250)),
    ) {
        AnimatedContent(
            targetState = copy,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 }) togetherWith
                    (fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it / 3 })
            },
            label = "copy",
        ) { c ->
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(c.title, style = MaterialTheme.typography.displaySmall, color = Palette.Text)
                Spacer(Modifier.height(4.dp))
                Text(c.subtitle, style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim)
            }
        }

        val problem: Problem? = when (state) {
            is FlowState.AwaitWriteTap -> state.problem
            is FlowState.AwaitVerifyTap -> state.problem
            else -> null
        }
        AnimatedVisibility(problem != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            problem?.let { ProblemPill(it) }
        }

        state.job?.let { job ->
            Spacer(Modifier.height(16.dp))
            PayloadRow(job.payload, job.size, accent)
        }

        when (state) {
            is FlowState.Success -> {
                Spacer(Modifier.height(14.dp))
                ReportChips(state.report)
                Spacer(Modifier.height(14.dp))
                if (autoLoop) {
                    LoopCountdown(accent)
                } else {
                    TextButton(onClick = onScanNext) { Text("Scan next", color = accent, style = MaterialTheme.typography.labelLarge) }
                }
            }
            is FlowState.Failed -> {
                state.report?.let { Spacer(Modifier.height(14.dp)); ReportChips(it) }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onScanNext) { Text("Scan a different code", color = accent, style = MaterialTheme.typography.labelLarge) }
            }
            else -> Unit
        }
    }
}

@Composable
private fun ProblemPill(problem: Problem) {
    Row(
        Modifier.padding(top = 12.dp).clip(RoundedCornerShape(14.dp))
            .background(Palette.Coral.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = Palette.Coral, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text("${problem.reason.title} · ${problem.reason.hint}", style = MaterialTheme.typography.labelLarge, color = Palette.Coral)
            problem.detail?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.Coral.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun QrPayload.kindIcon(): ImageVector = when (this) {
    is QrPayload.Uri -> Icons.Rounded.Link
    is QrPayload.Wifi -> Icons.Rounded.Wifi
    is QrPayload.VCard -> Icons.Rounded.Contacts
    is QrPayload.Text -> Icons.AutoMirrored.Rounded.Notes
}

private fun QrPayload.kindLabel(): String = when (this) {
    is QrPayload.Uri -> "LINK"
    is QrPayload.Wifi -> "WI-FI"
    is QrPayload.VCard -> "CONTACT"
    is QrPayload.Text -> "TEXT"
}

private fun QrPayload.preview(): String = when (this) {
    is QrPayload.Wifi -> ssid
    is QrPayload.VCard -> Regex("(?m)^FN[^:]*:(.*)$").find(raw)?.groupValues?.get(1)?.trim() ?: "vCard"
    else -> raw.replace('\n', ' ')
}

@Composable
private fun PayloadRow(payload: QrPayload, size: Int, accent: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x14FFFFFF)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) { Icon(payload.kindIcon(), null, tint = accent) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(payload.kindLabel(), style = MaterialTheme.typography.labelSmall, color = accent)
            Text(payload.preview(), style = MaterialTheme.typography.bodyMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("$size B", style = MaterialTheme.typography.labelLarge, color = Palette.Text)
            val fit = TagSize.smallestFitting(size)
            Text(
                fit?.let { "fits ${it.label}+" } ?: "needs > 868 B",
                style = MaterialTheme.typography.labelMedium,
                color = if (fit == null) Palette.Coral else Palette.TextDim,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportChips(report: VerifyReport) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip(
            if (report.dataMatches) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            if (report.dataMatches) "Data match" else "Data differs",
            if (report.dataMatches) Palette.Mint else Palette.Coral,
        )
        StatusChip(
            if (report.isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
            if (report.isLocked) "Locked" else "Writable",
            when {
                report.isLocked -> Palette.Amber
                report.lockRequested -> Palette.Coral
                else -> Palette.TextDim
            },
        )
        val model = TagSize.entries.firstOrNull { it.bytes == report.capacity }?.label ?: report.tech
        StatusChip(Icons.Rounded.Memory, "$model · ${report.used}/${report.capacity} B", Palette.TextDim)
    }
}

@Composable
private fun StatusChip(icon: ImageVector, text: String, color: Color) {
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (color == Palette.TextDim) Palette.Text else color)
    }
}

@Composable
private fun LoopCountdown(accent: Color) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(FlowViewModel.AUTO_LOOP_MS.toInt(), easing = LinearEasing)) }
    Column {
        Text("Next scan starting…", style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Palette.GlassBorder)) {
            Box(Modifier.fillMaxWidth(progress.value).height(4.dp).clip(CircleShape).background(accent))
        }
    }
}

// ---------- Chips ----------

@Composable
fun ChipRow(
    lockEnabled: Boolean,
    torch: Boolean,
    showTorch: Boolean,
    written: Int,
    accent: Color,
    onLock: (Boolean) -> Unit,
    onTorch: () -> Unit,
    onMore: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        PillButton(
            icon = if (lockEnabled) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
            text = if (lockEnabled) "Lock on" else "Lock off",
            highlighted = lockEnabled,
            color = Palette.Amber,
            onClick = { onLock(!lockEnabled) },
        )
        AnimatedVisibility(showTorch, enter = fadeIn(), exit = fadeOut()) {
            PillButton(
                icon = if (torch) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                text = null, highlighted = torch, color = accent, onClick = onTorch,
            )
        }
        Spacer(Modifier.weight(1f))
        PillButton(icon = Icons.Rounded.Tune, text = if (written > 0) "$written written" else null, highlighted = false, color = accent, onClick = onMore)
    }
}

@Composable
private fun PillButton(icon: ImageVector, text: String?, highlighted: Boolean, color: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (highlighted) color.copy(alpha = 0.22f) else Color(0x1AFFFFFF), label = "pill")
    val fg by animateColorAsState(if (highlighted) color else Palette.Text, label = "pillFg")
    Row(
        Modifier.height(44.dp).clip(CircleShape).background(bg)
            .border(1.dp, if (highlighted) color.copy(alpha = 0.5f) else Palette.GlassBorder, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (text == null) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, text, tint = fg, modifier = Modifier.size(20.dp))
        if (text != null) {
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}

// ---------- Dialog & sheet ----------

@Composable
fun LockWarningDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, null, tint = Palette.Amber) },
        title = { Text("Locking is permanent") },
        text = {
            Text(
                "With Lock on, every tag is made read-only right after writing. " +
                    "A locked tag can never be changed or erased again.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Turn on lock", color = Palette.Amber) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.TextDim) } },
        containerColor = Palette.Surface,
        titleContentColor = Palette.Text,
        textContentColor = Palette.TextDim,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSheet(
    settings: Settings,
    history: List<HistoryEntry>,
    onAutoLoop: (Boolean) -> Unit,
    onRawText: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Spacer(Modifier.height(8.dp))
            SettingRow("Keep going", "Return to scanning after each verified tag", settings.autoLoop, onAutoLoop)
            SettingRow("Plain text only", "Write everything as a Text record instead of links, Wi-Fi or contacts", settings.rawText, onRawText)
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Palette.GlassBorder)
            Text("This session", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Spacer(Modifier.height(8.dp))
            if (history.isEmpty()) {
                Text("Verified tags will show up here.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            } else {
                LazyColumn(Modifier.height((history.size.coerceAtMost(5) * 64).dp)) {
                    items(history) { entry -> HistoryRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Cyan, checkedThumbColor = Palette.Bg),
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Mint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Icon(entry.payload.kindIcon(), null, tint = Palette.Mint, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.payload.preview(), style = MaterialTheme.typography.bodyMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.at))} · UID ${entry.report.uid}",
                style = MaterialTheme.typography.labelMedium, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (entry.report.isLocked) Icon(Icons.Rounded.Lock, "Locked", tint = Palette.Amber, modifier = Modifier.size(18.dp))
    }
}
