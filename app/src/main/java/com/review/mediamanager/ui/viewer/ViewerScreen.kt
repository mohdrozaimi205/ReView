package com.review.mediamanager.ui.viewer

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.review.mediamanager.LocalPip
import com.review.mediamanager.components.MediaThumb
import com.review.mediamanager.database.MediaEntity
import com.review.mediamanager.database.displayHeight
import com.review.mediamanager.database.displayWidth
import com.review.mediamanager.database.extension
import com.review.mediamanager.database.isVideo
import com.review.mediamanager.imports.WhatsAppInfo
import com.review.mediamanager.navigation.ViewerQueue
import com.review.mediamanager.repository.MediaRepository
import com.review.mediamanager.security.VaultSession
import com.review.mediamanager.ui.appContainer
import com.review.mediamanager.ui.findActivity
import com.review.mediamanager.ui.theme.Aurora
import com.review.mediamanager.ui.vault.SecureWindow
import com.review.mediamanager.utilities.Fmt
import com.review.mediamanager.utilities.ShareHelper
import kotlinx.coroutines.launch

@Composable
fun ViewerScreen(nav: NavController, startId: Long) {
    val repo = appContainer().repository
    val ids = remember { ViewerQueue.ids.takeIf { startId in it } ?: listOf(startId) }
    val pager = rememberPagerState(initialPage = ids.indexOf(startId).coerceAtLeast(0)) { ids.size }
    val uiVisible = remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf(false) }
    val handle = remember { PlayerHandle() }
    val unlocked by VaultSession.unlocked.collectAsStateWithLifecycle()
    val pip = LocalPip.current
    val inPip by (pip?.inPip ?: remember { mutableStateOf(false) })
    val settings by appContainer().settings.settings.collectAsStateWithLifecycle()

    val currentId = ids[pager.currentPage]
    val currentFlow = remember(currentId) { repo.observeItem(currentId) }
    val current by currentFlow.collectAsState(initial = null)

    // System bars follow the overlay: hidden = true fullscreen.
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    DisposableEffect(uiVisible.value, inPip) {
        val w = activity?.window
        if (w != null) {
            val c = WindowCompat.getInsetsController(w, view)
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (uiVisible.value && !inPip) c.show(WindowInsetsCompat.Type.systemBars()) else c.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }

    val item = current
    if (item?.inVault == true) {
        SecureWindow(settings.blockScreenshotsInVault)
        if (!unlocked) { LaunchedEffect(Unit) { nav.popBackStack() }; return }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !zoomed && item?.isVideo != true && !inPip,
            key = { ids[it] }
        ) { page ->
            val pageFlow = remember(ids[page]) { repo.observeItem(ids[page]) }
            val pageItem by pageFlow.collectAsState(initial = null)
            val m = pageItem
            when {
                m == null -> Box(Modifier.fillMaxSize())
                m.isVideo && page == pager.currentPage -> VideoPlayer(m, repo, handle, uiVisible)
                m.isVideo -> Box(Modifier.fillMaxSize()) {
                    MediaThumb(m, repo, androidx.compose.ui.graphics.RectangleShape, Modifier.fillMaxSize(), showBadges = false)
                    Icon(Icons.Rounded.PlayCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(64.dp))
                }
                else -> ImageViewer(m, repo, original && page == pager.currentPage,
                    onTap = { uiVisible.value = !uiVisible.value }, onZoomChanged = { zoomed = it })
            }
        }

        if (item != null && !inPip) {
            AnimatedVisibility(uiVisible.value, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
                TopBar(nav, item, repo, handle, original, { original = it }, pager.currentPage + 1, ids.size)
            }
        }
    }
    BackHandler(enabled = handle.landscape.value) { handle.landscape.value = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    nav: NavController,
    item: MediaEntity,
    repo: MediaRepository,
    handle: PlayerHandle,
    original: Boolean,
    setOriginal: (Boolean) -> Unit,
    position: Int,
    total: Int
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pip = LocalPip.current
    var menu by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun act(block: suspend () -> String?) {
        scope.launch {
            try { block()?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() } }
            catch (e: Exception) { Toast.makeText(ctx, "Failed: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show() }
        }
    }

    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
            .statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(item.displayName, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$position / $total · ${item.displayWidth}×${item.displayHeight} · ${Fmt.qualityLabel(item.width, item.height)}" +
                    (if (item.isVideo && item.frameRate != null) " · ${Fmt.fps(item.frameRate)}" else ""),
                    color = Aurora.TextMid, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            if (!item.inVault) IconButton(onClick = { act { repo.setFavorite(listOf(item.id), !item.favorite); null } }) {
                Icon(if (item.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite",
                    tint = if (item.favorite) Aurora.Coral else Color.White)
            }
            IconButton(onClick = { info = true }) { Icon(Icons.Rounded.Info, "Info", tint = Color.White) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = Color.White) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Share…") }, onClick = { menu = false; act { if (!ShareHelper.shareMedia(ctx, repo.shareUris(listOf(item.id)), null)) "No app can share this" else null } })
                    DropdownMenuItem(text = { Text("Send to WhatsApp") }, onClick = { menu = false; act { if (!ShareHelper.shareMedia(ctx, repo.shareUris(listOf(item.id)), WhatsAppInfo.PKG_WHATSAPP)) "WhatsApp is not installed" else null } })
                    DropdownMenuItem(text = { Text("Send to WhatsApp Business") }, onClick = { menu = false; act { if (!ShareHelper.shareMedia(ctx, repo.shareUris(listOf(item.id)), WhatsAppInfo.PKG_BUSINESS)) "WhatsApp Business is not installed" else null } })
                    DropdownMenuItem(text = { Text("Export to phone gallery") }, onClick = { menu = false; act { repo.exportToGallery(listOf(item.id)); "Saved to " + (if (item.isVideo) "Movies" else "Pictures") + "/ReView" } })
                    if (item.inVault) DropdownMenuItem(text = { Text("Move out of Vault") }, onClick = { menu = false; act { repo.removeFromVault(listOf(item.id)); "Moved back to Gallery" } })
                    else DropdownMenuItem(text = { Text("Move to Private Vault") }, onClick = { menu = false; act { repo.moveToVault(listOf(item.id)); "Encrypted into the Vault" } })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Delete permanently", color = Aurora.Coral) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Aurora.Coral) },
                        onClick = { menu = false; confirmDelete = true })
                }
            }
        }
        // Secondary tools row
        Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.isVideo) {
                Box {
                    ToolChip(Icons.Rounded.Speed, "${handle.speed.value}x") { speedMenu = true }
                    DropdownMenu(speedMenu, onDismissRequest = { speedMenu = false }) {
                        listOf(0.5f, 1f, 1.5f, 2f).forEach { s ->
                            DropdownMenuItem(text = { Text("${s}x") }, onClick = { speedMenu = false; handle.setSpeed(s) })
                        }
                    }
                }
                ToolChip(Icons.Rounded.ScreenRotation, if (handle.landscape.value) "Portrait" else "Landscape") {
                    handle.landscape.value = !handle.landscape.value
                }
                if (pip?.supported == true) ToolChip(Icons.Rounded.PictureInPictureAlt, "PiP") {
                    if (pip.enter() != true) Toast.makeText(ctx, "Picture-in-picture isn't available", Toast.LENGTH_SHORT).show()
                }
            } else {
                ToolChip(Icons.Rounded.Rotate90DegreesCw, "Rotate") { act { repo.rotate(item); null } }
                ToolChip(Icons.Rounded.Hd, if (original) "Original ✓" else "Original") { setOriginal(!original) }
            }
        }
    }

    if (info) {
        ModalBottomSheet(onDismissRequest = { info = false }) {
            InfoPanel(item, handle)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete permanently?") },
            text = { Text("“${item.displayName}” will be removed from ReView. This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; act { repo.delete(listOf(item.id)); nav.popBackStack(); "Deleted" } }) { Text("Delete", color = Aurora.Coral) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ToolChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun InfoPanel(item: MediaEntity, handle: PlayerHandle) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 20.dp)) {
        Text(if (item.isVideo) "Video information" else "Image information", style = MaterialTheme.typography.titleLarge, color = Aurora.TextHi)
        Spacer(Modifier.height(12.dp))
        Row2("Name", item.displayName)
        Row2("Format", "${item.extension} · ${item.mimeType}")
        Row2("Resolution", "${item.displayWidth} × ${item.displayHeight} px (${Fmt.qualityLabel(item.width, item.height)})")
        Row2("Megapixels", String.format(java.util.Locale.US, "%.1f MP", item.width.toLong() * item.height / 1_000_000.0))
        Row2("File size", Fmt.bytes(item.sizeBytes))
        if (item.isVideo) {
            Row2("Duration", Fmt.duration(item.durationMs))
            Row2("Frame rate (measured)", Fmt.fps(item.frameRate))
            Row2("Codec", item.codec ?: "Unknown")
            Row2("Bitrate", Fmt.bitrate(item.bitrate))
            Row2("Orientation", if (item.displayWidth >= item.displayHeight) "Landscape" else "Portrait")
            handle.stats.streamSize.value?.let { Row2("Decoded stream", it + (handle.stats.streamFps.value?.let { f -> " @ ${Fmt.fps(f)}" } ?: "")) }
            handle.stats.decoder.value?.let { Row2("Decoder", it) }
            Row2("Dropped frames", handle.stats.dropped.value.toString())
        } else {
            item.codec?.let { Row2("Encoding", it) }
            if (item.sourceRotation != 0) Row2("EXIF rotation", "${item.sourceRotation}°")
        }
        Row2("Source", item.source.label)
        Row2("Imported", Fmt.date(item.importedAt))
        Row2("Storage", if (item.inVault) "Private Vault · AES-256-GCM encrypted" else "ReView private storage (hidden from gallery)")
        Row2("SHA-256", item.contentHash.take(16) + "…")
        Spacer(Modifier.height(8.dp))
        Text("Quality labels come from the real pixel size and measured frame timestamps — nothing is upscaled or relabelled.",
            style = MaterialTheme.typography.bodySmall, color = Aurora.TextLow)
    }
}

@Composable
private fun Row2(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(k, color = Aurora.TextMid, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.42f))
        Text(v, color = Aurora.TextHi, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.58f))
    }
}
