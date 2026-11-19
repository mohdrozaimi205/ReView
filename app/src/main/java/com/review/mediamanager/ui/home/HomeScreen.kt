package com.review.mediamanager.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.review.mediamanager.AppContainer
import com.review.mediamanager.components.IconBadge
import com.review.mediamanager.components.MediaThumb
import com.review.mediamanager.components.Panel
import com.review.mediamanager.database.MediaEntity
import com.review.mediamanager.navigation.Routes
import com.review.mediamanager.navigation.ViewerQueue
import com.review.mediamanager.navigation.goTab
import com.review.mediamanager.ui.appContainer
import com.review.mediamanager.ui.appViewModel
import com.review.mediamanager.ui.theme.Aurora
import com.review.mediamanager.ui.theme.LocalPalette
import com.review.mediamanager.utilities.Fmt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeState(
    val images: Int = 0,
    val videos: Int = 0,
    val vault: Int = 0,
    val libraryBytes: Long = 0,
    val freeBytes: Long = 0,
    val totalBytes: Long = 0,
    val recent: List<MediaEntity> = emptyList(),
    val name: String = ""
)

class HomeViewModel(c: AppContainer) : ViewModel() {
    private val repo = c.repository
    val state = combine(
        repo.observeImageCount(), repo.observeVideoCount(), repo.observeVaultCount(),
        repo.observeTotalBytes(), combine(repo.observeRecent(14), c.settings.settings) { r, s -> r to s.userName }
    ) { i, v, va, bytes, (recent, name) ->
        HomeState(i, v, va, bytes, repo.storage.freeBytes(), repo.storage.totalBytes(), recent, name)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())
}

@Composable
fun HomeScreen(nav: NavController) {
    val vm = appViewModel { HomeViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val repo = appContainer().repository
    val p = LocalPalette.current

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
                Text(Fmt.greeting() + if (s.name.isNotBlank()) ", ${s.name}" else "", style = MaterialTheme.typography.bodyLarge, color = Aurora.TextMid)
                Text("Your library", style = MaterialTheme.typography.displaySmall, color = Aurora.TextHi)
            }
        }
        // Hero card
        item {
            Box(
                Modifier.padding(horizontal = 18.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0F2A24), Color(0xFF1E1638), Color(0xFF0B0B10))))
                    .padding(22.dp)
            ) {
                Column {
                    Text("STORED IN REVIEW", style = MaterialTheme.typography.labelSmall, color = Aurora.Mint)
                    Spacer(Modifier.height(6.dp))
                    Text(Fmt.bytes(s.libraryBytes), style = MaterialTheme.typography.displaySmall, color = Aurora.TextHi)
                    Spacer(Modifier.height(16.dp))
                    val used = (s.totalBytes - s.freeBytes).coerceAtLeast(0)
                    val frac = if (s.totalBytes > 0) used.toFloat() / s.totalBytes else 0f
                    val mine = if (s.totalBytes > 0) s.libraryBytes.toFloat() / s.totalBytes else 0f
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Color(0x22FFFFFF))) {
                        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(10.dp).clip(CircleShape).background(Color(0x55FFFFFF)))
                        Box(Modifier.fillMaxWidth(mine.coerceIn(0.01f, 1f)).height(10.dp).clip(CircleShape).background(Aurora.brand))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Phone: ${Fmt.bytes(s.freeBytes)} free of ${Fmt.bytes(s.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall, color = Aurora.TextMid)
                }
            }
        }
        // Stats
        item {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(Modifier.weight(1f), "Images", s.images, Icons.Rounded.Image, Aurora.Mint) { nav.navigate(Routes.gallery("IMAGES")) }
                Stat(Modifier.weight(1f), "Videos", s.videos, Icons.Rounded.Movie, Aurora.Lavender) { nav.navigate(Routes.gallery("VIDEOS")) }
                Stat(Modifier.weight(1f), "Vault", s.vault, Icons.Rounded.Lock, Aurora.Coral) { nav.goTab(Routes.VAULT) }
            }
        }
        // Quick actions
        item { SectionLabel("Quick access") }
        item {
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Quick(Modifier.weight(1f), "WhatsApp", "Save shared media", Icons.Rounded.Chat, Color(0xFF4ADE80)) { nav.navigate(Routes.WHATSAPP) }
                    Quick(Modifier.weight(1f), "WA Business", "Save shared media", Icons.Rounded.Storefront, Color(0xFF38BDF8)) { nav.navigate(Routes.WHATSAPP) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Quick(Modifier.weight(1f), "Gallery", "Browse everything", Icons.Rounded.PhotoLibrary, Aurora.Mint) { nav.goTab(Routes.GALLERY) }
                    Quick(Modifier.weight(1f), "Private Vault", "Encrypted & hidden", Icons.Rounded.Lock, Aurora.Lavender) { nav.goTab(Routes.VAULT) }
                }
            }
        }
        // Recent
        item {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recently imported", style = MaterialTheme.typography.titleLarge, color = Aurora.TextHi, modifier = Modifier.weight(1f))
                if (s.recent.isNotEmpty()) Row(Modifier.clickable { nav.goTab(Routes.GALLERY) }, verticalAlignment = Alignment.CenterVertically) {
                    Text("See all", style = MaterialTheme.typography.labelLarge, color = Aurora.Mint)
                    Icon(Icons.Rounded.ArrowForward, null, tint = Aurora.Mint, modifier = Modifier.size(16.dp))
                }
            }
        }
        item {
            if (s.recent.isEmpty()) {
                Panel(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), onClick = { nav.goTab(Routes.IMPORT) }) {
                    Text("Nothing here yet", style = MaterialTheme.typography.titleMedium, color = Aurora.TextHi)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap Import to add photos and videos you're allowed to keep — from your gallery, files, or media shared from WhatsApp.",
                        style = MaterialTheme.typography.bodyMedium, color = Aurora.TextMid)
                }
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(s.recent, key = { it.id }) { m ->
                        MediaThumb(m, repo, RoundedCornerShape(20.dp),
                            Modifier.size(width = 128.dp, height = 168.dp).clickable {
                                ViewerQueue.ids = s.recent.map { it.id }
                                nav.navigate(Routes.viewer(m.id))
                            })
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp).background(p.background)) }
    }
}

@Composable
private fun SectionLabel(t: String) {
    Text(t, style = MaterialTheme.typography.titleLarge, color = Aurora.TextHi, modifier = Modifier.padding(start = 22.dp, top = 6.dp, bottom = 10.dp))
}

@Composable
private fun Stat(modifier: Modifier, label: String, value: Int, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Panel(modifier, radius = 22.dp, onClick = onClick, padding = 14.dp) {
        IconBadge(icon, tint, 36.dp)
        Spacer(Modifier.height(12.dp))
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = Aurora.TextHi)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Aurora.TextMid)
    }
}

@Composable
private fun Quick(modifier: Modifier, title: String, sub: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Panel(modifier, radius = 22.dp, onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Aurora.TextHi)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = Aurora.TextMid)
            }
        }
    }
}
