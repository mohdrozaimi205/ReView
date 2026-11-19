package com.review.mediamanager.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.review.mediamanager.components.Chip
import com.review.mediamanager.components.IconBadge
import com.review.mediamanager.components.Panel
import com.review.mediamanager.components.ScreenHeader
import com.review.mediamanager.domain.AppTheme
import com.review.mediamanager.navigation.Routes
import com.review.mediamanager.security.BiometricHelper
import com.review.mediamanager.security.PinManager
import com.review.mediamanager.security.VaultSession
import com.review.mediamanager.ui.appContainer
import com.review.mediamanager.ui.theme.Aurora
import com.review.mediamanager.utilities.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(nav: NavController) {
    val c = appContainer()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    var libBytes by remember { mutableLongStateOf(0) }
    var cacheBytes by remember { mutableLongStateOf(0) }
    var refresh by remember { mutableStateOf(0) }
    var confirmWipe by remember { mutableStateOf(false) }
    var changePin by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    val bioAvailable = remember { BiometricHelper.isAvailable(ctx) }

    LaunchedEffect(refresh) {
        withContext(Dispatchers.IO) {
            libBytes = c.storage.appStorageBytes()
            cacheBytes = c.storage.cacheBytes()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        ScreenHeader("Settings")
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Group("Appearance") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Palette, Aurora.Lavender, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Theme", style = MaterialTheme.typography.titleSmall, color = Aurora.TextHi)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTheme.entries.forEach { t -> Chip(t.label, s.theme == t) { c.settings.update { it.copy(theme = t) } } }
                }
                Spacer(Modifier.height(6.dp))
                Item(Icons.Rounded.TextFields, "Your name", s.userName.ifBlank { "Shown in the Home greeting" }) { editName = true }
            }

            Group("Security") {
                Item(Icons.Rounded.Password, if (c.pins.hasPin) "Change vault PIN" else "Set vault PIN", "6-digit PIN, stored only as a salted hash") { changePin = true }
                Toggle(Icons.Rounded.Fingerprint, "Fingerprint / face unlock",
                    if (bioAvailable) "Unlock the vault with biometrics" else "No biometrics enrolled on this phone",
                    s.biometricEnabled && bioAvailable, enabled = bioAvailable) { v -> c.settings.update { it.copy(biometricEnabled = v) } }
                Toggle(Icons.Rounded.LockClock, "Auto-lock vault", "Lock whenever you leave ReView", s.autoLockVault) { v -> c.settings.update { it.copy(autoLockVault = v) } }
                Toggle(Icons.Rounded.ScreenLockPortrait, "Block screenshots in vault", "Hides vault content from screenshots & recents", s.blockScreenshotsInVault) { v ->
                    c.settings.update { it.copy(blockScreenshotsInVault = v) }
                }
                Item(Icons.Rounded.Lock, "Lock vault now", null, onClick = { VaultSession.lock(); Toast.makeText(ctx, "Vault locked", Toast.LENGTH_SHORT).show() })
            }

            Group("Import") {
                Toggle(Icons.Rounded.Lock, "Import straight into Vault", "Encrypt every new import", s.importToVault) { v -> c.settings.update { it.copy(importToVault = v) } }
                Toggle(Icons.Rounded.Badge, "Keep original file names", "Otherwise ReView names files IMG_/VID_", s.keepOriginalNames) { v -> c.settings.update { it.copy(keepOriginalNames = v) } }
                Text("Duplicates are always detected by content (SHA-256) and skipped. Originals are copied byte-for-byte.",
                    style = MaterialTheme.typography.bodySmall, color = Aurora.TextLow, modifier = Modifier.padding(top = 6.dp))
            }

            Group("Storage") {
                Item(Icons.Rounded.Storage, "Library size", "${Fmt.bytes(libBytes)} · ${Fmt.bytes(c.storage.freeBytes())} free on phone", onClick = null)
                Item(Icons.Rounded.CleaningServices, "Clear temporary files", Fmt.bytes(cacheBytes)) {
                    scope.launch {
                        withContext(Dispatchers.IO) { c.storage.clearCache() }
                        refresh++
                        Toast.makeText(ctx, "Temporary files cleared", Toast.LENGTH_SHORT).show()
                    }
                }
                Item(Icons.Rounded.DeleteForever, "Delete all ReView media", "Removes everything, including the vault", tint = Aurora.Coral) { confirmWipe = true }
            }

            Group("About") {
                Item(Icons.Rounded.Policy, "Privacy policy", null, onClick = { nav.navigate(Routes.PRIVACY) })
                Item(Icons.Rounded.Info, "About ReView", "Version ${com.review.mediamanager.BuildConfig.VERSION_NAME}") { nav.navigate(Routes.ABOUT) }
            }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete all media?") },
            text = { Text("Every imported photo and video — including the Private Vault — will be permanently deleted from ReView.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    scope.launch { c.repository.deleteEverything(); refresh++; Toast.makeText(ctx, "All media deleted", Toast.LENGTH_SHORT).show() }
                }) { Text("Delete all", color = Aurora.Coral) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } }
        )
    }
    if (changePin) PinChangeDialog(c.pins) { changePin = false }
    if (editName) {
        var name by remember { mutableStateOf(s.userName) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Your name") },
            text = { OutlinedTextField(name, { name = it.take(24) }, singleLine = true, placeholder = { Text("e.g. Mie") }) },
            confirmButton = { TextButton(onClick = { c.settings.update { it.copy(userName = name.trim()) }; editName = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PinChangeDialog(pins: PinManager, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val needsCurrent = pins.hasPin
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (needsCurrent) "Change vault PIN" else "Set vault PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val kb = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                if (needsCurrent) OutlinedTextField(current, { current = it.filter(Char::isDigit).take(6) }, label = { Text("Current PIN") },
                    singleLine = true, keyboardOptions = kb, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(new1, { new1 = it.filter(Char::isDigit).take(6) }, label = { Text("New 6-digit PIN") },
                    singleLine = true, keyboardOptions = kb, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(new2, { new2 = it.filter(Char::isDigit).take(6) }, label = { Text("Repeat new PIN") },
                    singleLine = true, keyboardOptions = kb, visualTransformation = PasswordVisualTransformation())
                err?.let { Text(it, color = Aurora.Coral, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (needsCurrent) {
                    when (val r = pins.verify(current)) {
                        PinManager.Result.Ok -> Unit
                        is PinManager.Result.Wrong -> { err = "Current PIN is wrong (${r.attemptsLeft} left)"; return@TextButton }
                        is PinManager.Result.LockedOut -> { err = "Locked. Try again in ${r.millis / 1000 + 1}s"; return@TextButton }
                    }
                }
                when {
                    new1.length != 6 -> err = "PIN must be 6 digits"
                    new1 != new2 -> err = "New PINs don't match"
                    else -> { pins.setPin(new1); Toast.makeText(ctx, "Vault PIN saved", Toast.LENGTH_SHORT).show(); onDone() }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Aurora.Mint, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Panel(Modifier.fillMaxWidth(), padding = 14.dp) { content() }
    }
}

@Composable
private fun Item(icon: ImageVector, title: String, sub: String?, tint: Color = Aurora.Mint, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, tint, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (tint == Aurora.Coral) Aurora.Coral else Aurora.TextHi)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Aurora.TextMid)
        }
        if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = Aurora.TextLow, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Toggle(icon: ImageVector, title: String, sub: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, Aurora.Mint, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Aurora.TextHi)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Aurora.TextMid)
        }
        Switch(checked, onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Aurora.Mint, checkedThumbColor = Color.Black))
    }
}

@Composable
fun PrivacyScreen(nav: NavController) {
    TextPage(nav, "Privacy policy", listOf(
        "Everything stays on your phone" to "ReView has no accounts, no servers, no ads, no analytics and no internet permission. Your photos and videos never leave the device unless you share or export them yourself.",
        "What ReView stores" to "Copies of the media you choose to import, small thumbnails, and basic information (size, resolution, duration, frame rate, source) in a local database. All of it lives in ReView's private app storage, which other apps and the gallery can't read.",
        "Private Vault" to "Vault files are encrypted with AES-256-GCM. The encryption key is generated inside Android Keystore and can't be exported. Your PIN is kept only as a salted PBKDF2 hash. If you uninstall ReView or clear its data, vault files can't be recovered.",
        "WhatsApp & WhatsApp Business" to "ReView only receives media through Android's official share sheet, Photo Picker or file picker — i.e. files you can already open. It never reads WhatsApp's private databases, never intercepts messages and never opens, copies or bypasses View Once media.",
        "Your responsibility" to "Only import media you are authorised to save. Respect the privacy of the people who sent it to you.",
        "Permissions" to "Biometric (optional, to unlock the vault). No storage, camera, microphone, contacts or location permissions are requested.",
        "Backups" to "ReView opts out of Android cloud backup so your private media isn't uploaded anywhere automatically.",
        "Deleting data" to "Delete items individually, or use Settings › Delete all ReView media. Uninstalling the app erases everything."
    ))
}

@Composable
fun AboutScreen(nav: NavController) {
    TextPage(nav, "About ReView", listOf(
        "ReView v${com.review.mediamanager.BuildConfig.VERSION_NAME}" to "Your Media. Your Control. A native Android media manager to import, organise, protect and re-watch the photos and videos you're allowed to keep.",
        "Built with" to "Kotlin · Jetpack Compose · Material 3 · AndroidX Media3 ExoPlayer · Room · Coroutines · Photo Picker · Storage Access Framework · Android Keystore · BiometricPrompt · Coil · WorkManager.",
        "Video engine" to "Hardware-accelerated MediaCodec playback of MP4, MKV, MOV and WebM, including 4K and 60 fps where your phone's decoder supports it. Frame rate is measured from the real frame timestamps — never assumed.",
        "Developer" to "github.com/mohdrozaimi205"
    ))
}

@Composable
private fun TextPage(nav: NavController, title: String, sections: List<Pair<String, String>>) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 30.dp)) {
        ScreenHeader(title, onBack = { nav.popBackStack() })
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            sections.forEach { (h, b) ->
                Panel(Modifier.fillMaxWidth(), radius = 20.dp) {
                    Text(h, style = MaterialTheme.typography.titleMedium, color = Aurora.TextHi)
                    Spacer(Modifier.height(6.dp))
                    Text(b, style = MaterialTheme.typography.bodyMedium, color = Aurora.TextMid)
                }
            }
        }
    }
}
