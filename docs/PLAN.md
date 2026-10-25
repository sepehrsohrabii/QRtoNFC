# QR → NFC — Implementation Plan

An Android app that turns a QR code into an NFC tag in one hands-free run:

**Scan QR → tap tag (write) → [lock] → tap tag again (verify) → done → back to scanning**

No buttons between steps. Each step starts the next on its own.

---

## 1. Tech stack

| Concern | Choice | Why |
|---|---|---|
| Language / UI | Kotlin + Jetpack Compose (Material 3) | Modern, animation-friendly, single-Activity |
| QR scanning | CameraX `ImageAnalysis` + ML Kit Barcode Scanning (bundled model) | Fast (~50–150 ms per frame), works offline, no Play Services download wait |
| NFC | `NfcAdapter.enableReaderMode()` + `Ndef` / `NdefFormatable` | Reader mode keeps NFC in the foreground app, skips the system "new tag" sound and app chooser, and lets us poll only the tag types we need |
| Architecture | MVVM, one `FlowViewModel` holding a sealed `FlowState` (`StateFlow`) | The whole app is one state machine |
| DI | Hilt (optional; manual DI is enough at this size) | |
| Persistence | DataStore (settings) + Room (history log, optional) | |
| Min / target SDK | minSdk 24, targetSdk latest | Covers nearly all NFC phones |

Permissions: `CAMERA`, `NFC`, `VIBRATE`. Manifest: `<uses-feature android:name="android.hardware.nfc" android:required="true"/>`.

---

## 2. The automatic flow (state machine)

```
            ┌──────────────────────────────────────────────────────────┐
            ▼                                                          │
 [Scanning] ─QR decoded (stable)─▶ [AwaitWriteTap] ─tag─▶ [Writing]     │
                                        ▲                   │          │
                                        └─ error/tag lost ──┤          │
                                                            ▼          │
                                          lock on? ─yes─▶ [Locking]    │
                                                            │          │
                                                            ▼          │
                              [AwaitRemoval] ─tag gone─▶ [AwaitVerifyTap]
                                                            │ tag
                                                            ▼
                                                       [Verifying]
                                                    ┌───────┴────────┐
                                                 match            mismatch
                                                    ▼                ▼
                                               [Success]          [Failed]
                                                    └─ auto 1.5 s ─▶ Scanning
```

```kotlin
sealed interface FlowState {
    data object Scanning : FlowState
    data class AwaitWriteTap(val payload: QrPayload) : FlowState
    data class Writing(val payload: QrPayload) : FlowState
    data class Locking(val payload: QrPayload) : FlowState
    data class AwaitRemoval(val payload: QrPayload, val tagId: ByteArray) : FlowState
    data class AwaitVerifyTap(val payload: QrPayload, val tagId: ByteArray) : FlowState
    data class Verifying(val payload: QrPayload) : FlowState
    data class Success(val report: VerifyReport) : FlowState
    data class Failed(val reason: FailReason, val retryFrom: FlowState) : FlowState
}
```

### How each step runs without a button

1. **Scanning → AwaitWriteTap.** The camera is live as soon as the app opens. When ML Kit returns the **same value on 2 frames in a row** (stops misreads), we take it, stop the camera, vibrate, and move on. No "confirm" screen. The scanned value is shown during the next step, and a swipe-down cancels it.
2. **AwaitWriteTap → Writing.** Reader mode has been on since launch. A tag callback in this state starts the write.
3. **Writing → Locking.** Whether to lock is a **persistent toggle on the main screen**, set *before* scanning ("Lock tags: ON/OFF"). The flow never stops to ask. If it's on, we lock in the **same tap**, right after the write.
4. **→ AwaitRemoval → AwaitVerifyTap.** The verify step must be a *second* tap. After the write we keep the tag handle and poll `ndef.isConnected` / `connect()` every ~150 ms. When it fails, the tag has been removed. The UI then says "Tap again to verify". This stops the same tap from counting as the verify tap.
5. **Verifying → Success/Failed.** A tag in this state is read and compared. On success the screen celebrates for ~1.5 s and goes back to `Scanning` (good for batch work).

Errors (`TagLostException`, `IOException`) never need a button either. The state goes back to the matching "await tap" state with a short message ("Hold it a bit longer"), and the next tap retries.

---

## 3. NFC details

### Reader mode setup (in `MainActivity.onResume`)

```kotlin
nfcAdapter.enableReaderMode(
    this,
    { tag -> viewModel.onTag(tag) },          // runs on a binder thread
    NfcAdapter.FLAG_READER_NFC_A or
    NfcAdapter.FLAG_READER_NFC_B or
    NfcAdapter.FLAG_READER_NFC_F or
    NfcAdapter.FLAG_READER_NFC_V or
    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or // faster: we do our own NDEF handling
    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS, // we play our own sounds
    Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) }
)
```

Call `disableReaderMode` in `onPause`. If NFC is turned off, show a full-screen prompt with a deep link to `Settings.ACTION_NFC_SETTINGS`. Listen for `ACTION_ADAPTER_STATE_CHANGED` so the app continues on its own once NFC is back on.

### QR payload → NDEF record mapping (`NdefBuilder`)

| QR content | NDEF record |
|---|---|
| `http(s)://…`, `tel:`, `mailto:`, `sms:`, `geo:` | `NdefRecord.createUri()` (URI prefix compression saves bytes) |
| `WIFI:S:…;T:…;P:…;;` | Wi-Fi Simple Config MIME record `application/vnd.wfa.wsc` (Android connects on tap) |
| `BEGIN:VCARD` | MIME `text/vcard` |
| Anything else | `NdefRecord.createTextRecord("en", text)` |

Add a setting: "Write raw text only" (always a Text record).

### Write (`NfcWriter`)

1. `Ndef.get(tag)`:
   - If it's `null`, try `NdefFormatable.get(tag)?.format(message)` (blank tags).
   - If neither works, fail with `UNSUPPORTED_TAG`. MIFARE Classic often lands here on phones without NXP chips.
2. `connect()`. Check `isWritable`. If it's false, fail with `TAG_READ_ONLY`.
3. Check `message.byteArrayLength <= ndef.maxSize`. If not, fail with `TOO_LARGE` and show the sizes ("Needs 312 B, tag holds 137 B. Use NTAG215/216").
4. `writeNdefMessage(message)`.
5. If lock is on: `if (ndef.canMakeReadOnly()) ndef.makeReadOnly() else fail(LOCK_UNSUPPORTED)`.
6. Save `tag.id` (UID) for the verify step.

Run all I/O on `Dispatchers.IO`. Set `ndef.timeout` to ~1000 ms if the tech supports it.

### Verify (`NfcVerifier`)

On the second tap:
- **Same tag?** `tag.id` must match the saved UID. A different tag → "This is a different tag", and stay in `AwaitVerifyTap`.
- **Data matches?** Read `ndef.ndefMessage` (fresh read, not `cachedNdefMessage`). Compare its records byte-for-byte with the message we built (TNF, type, payload).
- **Locked?** If lock was on, check `ndef.isWritable == false`.
- Return a `VerifyReport(dataMatches, isLocked, uid, tagType, bytesUsed, capacity)` for the success screen.

### Locking caveat

`makeReadOnly()` is **permanent and can't be undone**. Before the toggle can be turned on for the first time, show a one-time warning sheet. NTAG21x password protection (`PWD_AUTH` over `NfcA.transceive`) could be a later, *reversible* "soft lock". It is out of scope for v1.

---

## 4. Speed budget

| Step | Target |
|---|---|
| App cold start → camera live | < 800 ms (start CameraX before the first frame is drawn, skip the splash) |
| QR detect | < 300 ms after the code is in frame |
| Write + lock in one tap | < 400 ms on NTAG213 |
| Verify read | < 200 ms |

Ways to hit these:
- Analyze frames at 1280×720 with `STRATEGY_KEEP_ONLY_LATEST`.
- Limit ML Kit to `FORMAT_QR_CODE` only.
- Turn on autofocus and tap-to-focus. Turn on the torch automatically in low light (use `ImageProxy` luminance).
- Use `FLAG_READER_SKIP_NDEF_CHECK` and build the NDEF message while the app is waiting for the tap, not after the tap.

---

## 5. UI / UX concept: "One Continuous Journey"

One screen, no navigation. A **hero shape** in the center changes form at each stage, so the user always knows what to do.

**Visual language:** dark, deep background (#0B0D12) with a soft animated mesh gradient. Each stage has its own accent color, blended in over ~400 ms. Rounded geometric type (e.g. *Space Grotesk* or *Manrope*). Glass panels (blur on API 31+, translucent fill on older APIs).

| Stage | Hero element | Accent | Copy |
|---|---|---|---|
| Scanning | Full-bleed camera view. A rounded square frame "breathes" and locks onto the QR's corners when it's found | Electric cyan | "Point at a QR code" |
| QR found | The frame shrinks into a glowing orb with the decoded text inside. Strong haptic click | Cyan → violet | Shows the content (domain, Wi-Fi name, etc.) |
| Await write tap | The orb becomes a phone outline with **NFC ripple rings** pulsing from the antenna spot, plus a floating tag card moving toward it | Violet | "Hold a tag to the back of your phone" |
| Writing | The rings collapse inward. A circular progress line fills | Violet | "Writing… keep holding" |
| Locking | A padlock shackle snaps shut, with a metallic tick sound and a heavy haptic | Amber | "Locking" |
| Await removal → verify | The tag card floats away, then comes back with a dashed outline | Teal | "Lift, then tap again to verify" |
| Success | Particle burst, a checkmark draws itself, summary chips (✓ Data match · 🔒 Locked · NTAG215 · 48/496 B) | Mint green | Auto-returns to scanning after 1.5 s (a progress ring on the chip shows the countdown) |
| Failed | A soft red shake, a clear reason, and a hint for the next tap. No dead ends | Coral | e.g. "Tag is too small: needs 312 B" |

**Persistent chrome (always visible, never blocking):**
- A **progress rail** across the top with 4 dots (Scan · Write · Lock · Verify). The Lock dot is hidden when locking is off. Dots fill as each stage finishes.
- The **Lock toggle** chip at the bottom left. The **History** chip at the bottom right (a sheet listing written tags).
- An **"Antenna finder"** (first run only): a short animation showing where to place the tag. It is set per device, because the antenna sits in different spots on different phones.

**Feedback channels:** each stage change gets a distinct haptic pattern (`VibrationEffect.Composition` on API 30+) and a short sound (SoundPool, preloaded). This way the user can do the whole flow without looking at the screen.

**Motion implementation:** Compose `updateTransition` keyed on `FlowState`, `AnimatedContent` for the hero, and `Canvas` for the ripples and particles. Lottie is optional, for the padlock and checkmark. Respect the system "remove animations" setting (fall back to cross-fades).

**Accessibility:** a TalkBack announcement on every state change (`LiveRegion.Polite`), contrast ≥ 4.5:1, and support for large text.

---

## 6. Project structure

```
app/src/main/java/com/qrtonfc/
├── MainActivity.kt              // reader mode lifecycle, permissions
├── flow/
│   ├── FlowViewModel.kt         // the state machine
│   ├── FlowState.kt
│   └── FailReason.kt
├── scan/
│   ├── QrAnalyzer.kt            // CameraX analyzer + ML Kit + 2-frame stability
│   └── QrPayload.kt             // parsed type (Url, Wifi, VCard, Text…)
├── nfc/
│   ├── NdefBuilder.kt           // QrPayload → NdefMessage
│   ├── NfcWriter.kt
│   ├── NfcVerifier.kt
│   ├── TagRemovalWatcher.kt
│   └── NfcAvailability.kt
├── ui/
│   ├── FlowScreen.kt            // single screen
│   ├── hero/ (ScanFrame, NfcRipples, Padlock, SuccessBurst…)
│   ├── ProgressRail.kt
│   └── theme/
├── feedback/ (Haptics.kt, Sounds.kt)
└── data/ (SettingsStore.kt, HistoryDb.kt)
```

---

## 7. Milestones

1. **Skeleton:** Compose project, theme, `FlowState` + ViewModel with fake transitions. Unit tests for every transition.
2. **Scanning:** CameraX + ML Kit, stability filter, `QrPayload` parser (with tests for each format).
3. **NFC write:** reader mode, `NdefBuilder` (unit tests), `NfcWriter`, error mapping.
4. **Lock + verify:** `makeReadOnly`, removal watcher, UID + byte comparison, `VerifyReport`.
5. **UI journey:** hero shapes, ripples, progress rail, stage colors, haptics and sounds.
6. **Polish:** history, NFC-off and permission screens, antenna finder, accessibility, reduced motion.
7. **Hardening:** run the test matrix below on real devices, then a release build with R8.

---

## 8. Testing

- **Unit (JVM):** state machine transitions, the QR parser, `NdefBuilder` byte output, the verify comparison logic.
- **Instrumented/UI:** Compose UI tests that drive the ViewModel with fake `TagIO` (wrap `Ndef` behind an interface so it can be faked).
- **Real-device matrix:**

| Tag | Expected |
|---|---|
| NTAG213 / 215 / 216 (blank) | Format + write + lock + verify ✓ |
| NTAG already holding data | Overwrite ✓ |
| Already locked tag | `TAG_READ_ONLY` message |
| MIFARE Classic | `UNSUPPORTED_TAG` on most non-NXP phones |
| Payload larger than capacity | `TOO_LARGE` with sizes |
| Lifting the tag mid-write | Auto-retry on the next tap |
| A different tag on verify | "Different tag" message, waits |

Test on at least one Samsung, one Pixel, and one Xiaomi device. Their NFC stacks and antenna positions differ.

---

## 9. Open decisions (defaults chosen)

- **Lock meaning:** permanent `makeReadOnly` (default), not password protection.
- **After success:** loop back to scanning (batch mode) by default. Setting: "Stop after each tag".
- **Payload encoding:** smart record type by default (URI/Wi-Fi/vCard/Text). Setting: raw Text.
