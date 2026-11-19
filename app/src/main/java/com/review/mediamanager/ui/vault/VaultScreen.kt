package com.review.mediamanager.ui.vault

import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.review.mediamanager.AppContainer
import com.review.mediamanager.components.EmptyState
import com.review.mediamanager.components.GhostButton
import com.review.mediamanager.components.ScreenHeader
import com.review.mediamanager.domain.GalleryCategory
import com.review.mediamanager.navigation.Routes
import com.review.mediamanager.navigation.ViewerQueue
import com.review.mediamanager.navigation.goTab
import com.review.mediamanager.security.BiometricHelper
import com.review.mediamanager.security.PinManager
import com.review.mediamanager.security.VaultSession
import com.review.mediamanager.ui.appContainer
import com.review.mediamanager.ui.appViewModel
import com.review.mediamanager.ui.findActivity
import com.review.mediamanager.ui.gallery.MediaCollection
import com.review.mediamanager.ui.gallery.SelectionBar
import com.review.mediamanager.ui.gallery.SelectionViewModel
import com.review.mediamanager.ui.gallery.applyFilter
import com.review.mediamanager.ui.importer.rememberImportLaunchers
import com.review.mediamanager.ui.theme.Aurora
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class VaultViewModel(c: AppContainer) : SelectionViewModel(c.repository) {
    val pins = c.pins
    val settings = c.settings
    val items = combine(repo.observeLibrary(true), c.settings.settings) { list, s ->
        list.applyFilter(GalleryCategory.ALL, "", s.sortOrder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/** Reference-counted FLAG_SECURE so overlapping screens (vault → viewer) never drop the protection. */
private object SecureFlag {
    private var count = 0
    fun acquire(activity: android.app.Activity) {
        if (count++ == 0) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    fun release(activity: android.app.Activity) {
        if (count > 0 && --count == 0) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/** Blocks screenshots & recent-apps preview while vault content is visible. */
@Composable
fun SecureWindow(enabled: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(enabled, activity) {
        val a = activity
        if (enabled && a != null) SecureFlag.acquire(a)
        onDispose { if (enabled && a != null) SecureFlag.release(a) }
    }
}

@Composable
fun VaultScreen(nav: NavController) {
    val vm = appViewModel { VaultViewModel(it) }
    val unlocked by VaultSession.unlocked.collectAsStateWithLifecycle()
    val settings by vm.settings.settings.collectAsStateWithLifecycle()
    SecureWindow(settings.blockScreenshotsInVault)
    if (!unlocked) LockScreen(vm) else VaultContent(nav, vm)
}

@Composable
private fun LockScreen(vm: VaultViewModel) {
    val ctx = LocalContext.current
    val activity = ctx.findActivity() as? FragmentActivity
    val settings by vm.settings.settings.collectAsStateWithLifecycle()
    val creating = !vm.pins.hasPin
    var entry by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var errorTick by remember { mutableIntStateOf(0) }
    val bioAvailable = remember { BiometricHelper.isAvailable(ctx) }
    val canBio = !creating && settings.biometricEnabled && bioAvailable && activity != null
    val pinLength = if (creating) 6 else 6

    fun bio() {
        if (activity != null) BiometricHelper.authenticate(activity, onSuccess = { VaultSession.unlock() }, onError = { message = it })
    }
    LaunchedEffect(canBio) { if (canBio) bio() }

    fun submit(pin: String) {
        if (creating) {
            if (firstPin == null) { firstPin = pin; entry = ""; message = "Enter the same PIN again" }
            else if (firstPin == pin) { vm.pins.setPin(pin); VaultSession.unlock(); Toast.makeText(ctx, "Vault PIN created", Toast.LENGTH_SHORT).show() }
            else { firstPin = null; entry = ""; errorTick++; message = "PINs didn't match. Start again." }
        } else {
            when (val r = vm.pins.verify(pin)) {
                PinManager.Result.Ok -> VaultSession.unlock()
                is PinManager.Result.Wrong -> { entry = ""; errorTick++; message = "Wrong PIN · ${r.attemptsLeft} attempt(s) left" }
                is PinManager.Result.LockedOut -> { entry = ""; errorTick++; message = "Too many attempts. Try again in ${(r.millis / 1000) + 1}s" }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(78.dp).clip(CircleShape).background(Aurora.brandSoft), contentAlignment = Alignment.Center) {
            Icon(if (creating) Icons.Rounded.Shield else Icons.Rounded.Lock, null, tint = Aurora.Mint, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(if (creating) (if (firstPin == null) "Create a 6-digit PIN" else "Confirm your PIN") else "Private Vault",
            style = MaterialTheme.typography.headlineSmall, color = Aurora.TextHi)
        Spacer(Modifier.height(6.dp))
        Text(message ?: if (creating) "Your vault files are encrypted with a key kept in Android Keystore." else "Enter your PIN to unlock",
            style = MaterialTheme.typography.bodyMedium, color = if (message != null && errorTick > 0) Aurora.Coral else Aurora.TextMid,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 30.dp))
        Spacer(Modifier.height(24.dp))
        PinDots(pinLength, entry.length, errorTick)
        Spacer(Modifier.height(30.dp))
        PinPad(
            onDigit = { d ->
                if (entry.length < pinLength) {
                    entry += d
                    if (entry.length == pinLength) submit(entry)
                }
            },
            onDelete = { entry = entry.dropLast(1) },
            onBiometric = if (canBio) ({ bio() }) else null
        )
    }
}

@Composable
private fun VaultContent(nav: NavController, vm: VaultViewModel) {
    val ctx = LocalContext.current
    val repo = appContainer().repository
    val items by vm.items.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val settings by vm.settings.settings.collectAsStateWithLifecycle()
    val progress by repo.progress.collectAsStateWithLifecycle()
    val l = rememberImportLaunchers(forceVault = true)
    LaunchedEffect(Unit) { vm.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader("Private Vault", "${items.size} encrypted item${if (items.size == 1) "" else "s"} · hidden from gallery") {
                IconButton(onClick = { nav.goTab(Routes.SETTINGS) }) { Icon(Icons.Rounded.Settings, "Vault settings", tint = Aurora.TextMid) }
                IconButton(onClick = { VaultSession.lock() }) { Icon(Icons.Rounded.Lock, "Lock", tint = Aurora.Mint) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Images", Icons.Rounded.Image, Modifier.weight(1f), Aurora.Mint, l.images)
                GhostButton("Videos", Icons.Rounded.Movie, Modifier.weight(1f), Aurora.Lavender, l.videos)
                GhostButton("Files", Icons.Rounded.Folder, Modifier.weight(1f), Aurora.Amber, l.files)
            }
            if (busy || progress.running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), color = Aurora.Lavender)
            if (items.isEmpty()) {
                EmptyState(Icons.Rounded.Shield, "Your vault is empty",
                    "Add media above, or select items in Gallery and tap “To Vault”. Vault files are AES-256 encrypted and never appear in other apps.",
                    Modifier.padding(bottom = 100.dp))
            } else {
                MediaCollection(
                    items, repo, settings.gridView, selection, bottomPadding = if (selection.isEmpty()) 110.dp else 200.dp,
                    onOpen = { m -> ViewerQueue.ids = items.map { it.id }; nav.navigate(Routes.viewer(m.id)) },
                    onToggle = { vm.toggle(it) }
                )
            }
        }
        AnimatedVisibility(selection.isNotEmpty(), modifier = Modifier.align(Alignment.BottomCenter)) {
            SelectionBar(selection.size, inVault = true, vm = vm, allIds = items.map { it.id }, modifier = Modifier.padding(bottom = 76.dp))
        }
    }
}
