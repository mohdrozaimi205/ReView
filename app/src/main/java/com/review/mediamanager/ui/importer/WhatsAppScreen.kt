package com.review.mediamanager.ui.importer

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.review.mediamanager.components.GhostButton
import com.review.mediamanager.components.GradientButton
import com.review.mediamanager.components.IconBadge
import com.review.mediamanager.components.Panel
import com.review.mediamanager.components.ScreenHeader
import com.review.mediamanager.imports.WhatsAppInfo
import com.review.mediamanager.navigation.Routes
import com.review.mediamanager.navigation.goTab
import com.review.mediamanager.ui.appContainer
import com.review.mediamanager.ui.theme.Aurora
import com.review.mediamanager.utilities.ShareHelper

private val WaGreen = Color(0xFF4ADE80)
private val WaBlue = Color(0xFF38BDF8)

@Composable
fun WhatsAppScreen(nav: NavController) {
    val ctx = LocalContext.current
    val c = appContainer()
    val progress by c.repository.progress.collectAsStateWithLifecycle()
    val l = rememberImportLaunchers()
    var hasWa by remember { mutableStateOf(false) }
    var hasWab by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasWa = WhatsAppInfo.isInstalled(ctx, WhatsAppInfo.PKG_WHATSAPP)
        hasWab = WhatsAppInfo.isInstalled(ctx, WhatsAppInfo.PKG_BUSINESS)
    }
    val wasRunning = remember { progress.running }
    LaunchedEffect(progress.running) {
        if (progress.running && !wasRunning) nav.goTab(Routes.IMPORT)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 30.dp)) {
        ScreenHeader("WhatsApp", "Save media people shared with you", onBack = { nav.popBackStack() })
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            AppCard("WhatsApp Messenger", Icons.Rounded.Chat, WaGreen, hasWa,
                onPick = l.whatsapp,
                onOpen = { if (!WhatsAppInfo.launch(ctx, false)) Toast.makeText(ctx, "WhatsApp is not installed", Toast.LENGTH_SHORT).show() })
            AppCard("WhatsApp Business", Icons.Rounded.Storefront, WaBlue, hasWab,
                onPick = l.business,
                onOpen = { if (!WhatsAppInfo.launch(ctx, true)) Toast.makeText(ctx, "WhatsApp Business is not installed", Toast.LENGTH_SHORT).show() })

            Panel(Modifier.fillMaxWidth()) {
                Text("Two easy ways to import", style = MaterialTheme.typography.titleMedium, color = Aurora.TextHi)
                Spacer(Modifier.height(10.dp))
                Step(1, "Share from the chat", "Open the photo or video in WhatsApp → tap Share (or ⋮ → Share) → choose ReView. It is tagged automatically.")
                Step(2, "Pick from the WhatsApp folder", "Tap “Pick from folder” above. Android's file picker opens in WhatsApp's media folder (WhatsApp Images / WhatsApp Video). Select one or many files.")
                Step(3, "Export back", "In Gallery, select items → Share → WhatsApp or WA Business to send permitted media again.")
                Spacer(Modifier.height(4.dp))
                Text("If the picker shows an empty folder, open the chat media once in WhatsApp so it finishes downloading, or use “Share” instead.",
                    style = MaterialTheme.typography.bodySmall, color = Aurora.TextLow)
            }

            // View Once consent workflow
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Shield, Aurora.Coral, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("View Once messages", style = MaterialTheme.typography.titleMedium, color = Aurora.TextHi)
                }
                Spacer(Modifier.height(10.dp))
                Text("View Once photos and videos are protected by WhatsApp's end-to-end encryption and its privacy rules. ReView does not open, copy, screenshot or bypass them — ever.",
                    style = MaterialTheme.typography.bodyMedium, color = Aurora.TextMid)
                Spacer(Modifier.height(8.dp))
                Text("If you'd like to keep one, ask the sender. If they agree, they can send a normal copy (View Once off) and you can share it into ReView like any other file.",
                    style = MaterialTheme.typography.bodyMedium, color = Aurora.TextMid)
                Spacer(Modifier.height(14.dp))
                GradientButton("Ask sender for a saveable copy", Icons.Rounded.Send, Modifier.fillMaxWidth()) {
                    val business = !hasWa && hasWab
                    if (!ShareHelper.requestSaveableCopy(ctx, business))
                        Toast.makeText(ctx, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
                }
                if (hasWab && hasWa) {
                    Spacer(Modifier.height(8.dp))
                    GhostButton("Ask via WhatsApp Business", Icons.Rounded.Storefront, Modifier.fillMaxWidth(), WaBlue) {
                        ShareHelper.requestSaveableCopy(ctx, true)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCard(name: String, icon: ImageVector, tint: Color, installed: Boolean, onPick: () -> Unit, onOpen: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = Aurora.TextHi)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (installed) tint else Aurora.TextLow))
                    Spacer(Modifier.width(6.dp))
                    Text(if (installed) "Installed" else "Not installed", style = MaterialTheme.typography.bodySmall, color = Aurora.TextMid)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientButton("Pick from folder", Icons.Rounded.FolderOpen, Modifier.weight(1f), onClick = onPick)
            GhostButton("Open app", Icons.Rounded.OpenInNew, Modifier.weight(1f), tint, onOpen)
        }
    }
}

@Composable
private fun Step(n: Int, title: String, body: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Aurora.brand), contentAlignment = Alignment.Center) {
            Text(n.toString(), style = MaterialTheme.typography.labelLarge, color = Color(0xFF07130F))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Aurora.TextHi)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Aurora.TextMid)
        }
    }
}
