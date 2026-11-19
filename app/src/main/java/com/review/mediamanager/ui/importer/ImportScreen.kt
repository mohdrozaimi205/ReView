package com.review.mediamanager.ui.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.review.mediamanager.components.IconBadge
import com.review.mediamanager.components.Panel
import com.review.mediamanager.components.ScreenHeader
import com.review.mediamanager.domain.MediaSource
import com.review.mediamanager.navigation.Routes
import com.review.mediamanager.navigation.goTab
import com.review.mediamanager.security.VaultSession
import com.review.mediamanager.ui.appContainer
import com.review.mediamanager.ui.theme.Aurora

/** Document picker that can open directly in a starting folder (e.g. WhatsApp's media folder). */
class OpenDocsAt(private val initial: Uri?) : ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).apply {
            if (initial != null) putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
}

val MEDIA_MIMES = arrayOf("image/*", "video/*")

/** Remembers all import launchers so any screen (Import, WhatsApp, Vault) can trigger them. */
class ImportLaunchers(
    val images: () -> Unit,
    val videos: () -> Unit,
    val files: () -> Unit,
    val whatsapp: () -> Unit,
    val business: () -> Unit
)

@Composable
fun rememberImportLaunchers(forceVault: Boolean = false): ImportLaunchers {
    val c = appContainer()
    val repo = c.repository
    fun run(uris: List<Uri>, source: MediaSource) {
        val s = c.settings.current
        repo.import(uris, source, null, forceVault || s.importToVault, s.keepOriginalNames)
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { run(it, MediaSource.GALLERY) }
    val pickVideos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { run(it, MediaSource.GALLERY) }
    val pickFiles = rememberLauncherForActivityResult(OpenDocsAt(null)) { run(it, MediaSource.FILES) }
    val pickWa = rememberLauncherForActivityResult(OpenDocsAt(com.review.mediamanager.imports.WhatsAppInfo.mediaFolderUri(false))) { run(it, MediaSource.WHATSAPP) }
    val pickWab = rememberLauncherForActivityResult(OpenDocsAt(com.review.mediamanager.imports.WhatsAppInfo.mediaFolderUri(true))) { run(it, MediaSource.WHATSAPP_BUSINESS) }
    return ImportLaunchers(
        images = { VaultSession.keepUnlockedOnce = true; pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        videos = { VaultSession.keepUnlockedOnce = true; pickVideos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
        files = { VaultSession.keepUnlockedOnce = true; pickFiles.launch(MEDIA_MIMES) },
        whatsapp = { VaultSession.keepUnlockedOnce = true; pickWa.launch(MEDIA_MIMES) },
        business = { VaultSession.keepUnlockedOnce = true; pickWab.launch(MEDIA_MIMES) }
    )
}

@Composable
fun ImportScreen(nav: NavController) {
    val c = appContainer()
    val progress by c.repository.progress.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val l = rememberImportLaunchers()

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        ScreenHeader("Import", "Bring in photos & videos you're allowed to keep")
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VerifiedUser, null, tint = Aurora.Mint)
                    Spacer(Modifier.width(10.dp))
                    Text("Only media you can open and are authorised to save can be imported. ReView copies the original file — quality is never reduced. View Once media stays protected.",
                        style = MaterialTheme.typography.bodySmall, color = Aurora.TextMid)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigAction(Modifier.weight(1f), "Import Images", "Photo Picker", Icons.Rounded.Image, Aurora.Mint, l.images)
                BigAction(Modifier.weight(1f), "Import Videos", "Photo Picker", Icons.Rounded.Movie, Aurora.Lavender, l.videos)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigAction(Modifier.weight(1f), "Import from Files", "Any folder · MKV, MOV, WebM", Icons.Rounded.Folder, Aurora.Amber, l.files)
                BigAction(Modifier.weight(1f), "Import from WhatsApp", "Messenger & Business", Icons.Rounded.Chat, Color(0xFF4ADE80)) { nav.navigate(Routes.WHATSAPP) }
            }

            Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Lock, Aurora.Lavender, 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Import straight into Vault", style = MaterialTheme.typography.titleSmall, color = Aurora.TextHi)
                        Text("New files are encrypted immediately", style = MaterialTheme.typography.bodySmall, color = Aurora.TextMid)
                    }
                    Switch(settings.importToVault, { v -> c.settings.update { it.copy(importToVault = v) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Aurora.Mint, checkedThumbColor = Color.Black))
                }
            }

            AnimatedVisibility(progress.running) {
                Panel(Modifier.fillMaxWidth()) {
                    Text("Importing ${progress.done + 1} of ${progress.total}", style = MaterialTheme.typography.titleMedium, color = Aurora.TextHi)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth(), color = Aurora.Mint, trackColor = Color(0x22FFFFFF)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Copying original files and checking for duplicates…", style = MaterialTheme.typography.bodySmall, color = Aurora.TextMid)
                }
            }

            val report = progress.lastReport
            if (!progress.running && report != null) {
                Panel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (report.errors.isEmpty()) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null,
                            tint = if (report.errors.isEmpty()) Aurora.Mint else Aurora.Amber)
                        Spacer(Modifier.width(10.dp))
                        Text("Import finished", style = MaterialTheme.typography.titleMedium, color = Aurora.TextHi, modifier = Modifier.weight(1f))
                        IconButton(onClick = { c.repository.clearReport() }) { Icon(Icons.Rounded.Close, "Dismiss", tint = Aurora.TextMid) }
                    }
                    Text("${report.imported} imported", color = Aurora.Mint, style = MaterialTheme.typography.bodyMedium)
                    if (report.duplicates > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ContentCopy, null, tint = Aurora.TextMid, modifier = Modifier.padding(end = 6.dp))
                        Text("${report.duplicates} skipped — already in ReView", color = Aurora.TextMid, style = MaterialTheme.typography.bodyMedium)
                    }
                    report.errors.take(8).forEach {
                        Text("• $it", color = Aurora.Coral, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (report.errors.size > 8) Text("…and ${report.errors.size - 8} more", color = Aurora.TextMid, style = MaterialTheme.typography.bodySmall)
                    if (report.imported > 0) {
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.TextButton(onClick = { nav.goTab(if (settings.importToVault) Routes.VAULT else Routes.GALLERY) }) {
                            Text(if (settings.importToVault) "View in Vault" else "View in Gallery", color = Aurora.Mint)
                        }
                    }
                }
            }

            Text("Tip: in WhatsApp you can also long-press a photo or video → Share → ReView.",
                style = MaterialTheme.typography.bodySmall, color = Aurora.TextLow, modifier = Modifier.padding(6.dp))
        }
    }
}

@Composable
fun BigAction(modifier: Modifier, title: String, sub: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Panel(modifier.height(138.dp), radius = 24.dp, onClick = onClick, padding = 16.dp) {
        IconBadge(icon, tint, 44.dp)
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.titleSmall, color = Aurora.TextHi)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = Aurora.TextMid, maxLines = 2)
    }
}
