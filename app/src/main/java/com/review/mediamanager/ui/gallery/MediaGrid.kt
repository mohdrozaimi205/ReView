package com.review.mediamanager.ui.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.review.mediamanager.components.MediaThumb
import com.review.mediamanager.database.MediaEntity
import com.review.mediamanager.database.isVideo
import com.review.mediamanager.imports.WhatsAppInfo
import com.review.mediamanager.repository.MediaRepository
import com.review.mediamanager.ui.theme.Aurora
import com.review.mediamanager.ui.theme.LocalPalette
import com.review.mediamanager.utilities.Fmt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCollection(
    items: List<MediaEntity>,
    repo: MediaRepository,
    grid: Boolean,
    selection: Set<Long>,
    bottomPadding: Dp,
    onOpen: (MediaEntity) -> Unit,
    onToggle: (Long) -> Unit
) {
    val selecting = selection.isNotEmpty()
    val click: (MediaEntity) -> Unit = { if (selecting) onToggle(it.id) else onOpen(it) }
    if (grid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items, key = { it.id }) { m ->
                MediaThumb(
                    m, repo, RoundedCornerShape(16.dp),
                    Modifier.aspectRatio(1f).combinedClickable(onClick = { click(m) }, onLongClick = { onToggle(m.id) }),
                    selectionMode = selecting, selected = m.id in selection
                )
            }
        }
    } else {
        val p = LocalPalette.current
        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items, key = { it.id }) { m ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(if (m.id in selection) Aurora.Mint.copy(alpha = 0.12f) else p.surface)
                        .combinedClickable(onClick = { click(m) }, onLongClick = { onToggle(m.id) })
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MediaThumb(m, repo, RoundedCornerShape(12.dp), Modifier.size(66.dp),
                        selectionMode = selecting, selected = m.id in selection, showBadges = false)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.displayName, style = MaterialTheme.typography.titleSmall, color = Aurora.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                append("${m.width}×${m.height} · ${Fmt.qualityLabel(m.width, m.height)}")
                                if (m.isVideo) append(" · ${Fmt.duration(m.durationMs)}")
                            },
                            style = MaterialTheme.typography.bodySmall, color = Aurora.TextMid
                        )
                        Text("${Fmt.bytes(m.sizeBytes)} · ${m.source.label} · ${Fmt.date(m.importedAt)}",
                            style = MaterialTheme.typography.bodySmall, color = Aurora.TextLow, maxLines = 1)
                    }
                    if (m.favorite) Icon(Icons.Rounded.Favorite, null, tint = Aurora.Coral, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Bottom action bar shown while items are selected. */
@Composable
fun SelectionBar(
    count: Int,
    inVault: Boolean,
    vm: SelectionViewModel,
    allIds: List<Long>,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    var confirmDelete by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)
            .clip(RoundedCornerShape(26.dp)).background(p.surfaceHigh).padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.clearSelection() }) { Icon(Icons.Rounded.Close, "Cancel", tint = Aurora.TextHi) }
            Text("$count selected", style = MaterialTheme.typography.titleSmall, color = Aurora.TextHi, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.selectAll(allIds) }) { Icon(Icons.Rounded.SelectAll, "Select all", tint = Aurora.TextMid) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (!inVault) {
                Action(Icons.Rounded.Favorite, "Favorite") { vm.favoriteSelected(true) }
                Action(Icons.Rounded.Lock, "To Vault") { vm.moveSelectedToVault() }
            } else {
                Action(Icons.Rounded.LockOpen, "Unhide") { vm.removeSelectedFromVault() }
            }
            Box {
                Action(Icons.Rounded.Share, "Share") { shareMenu = true }
                DropdownMenu(shareMenu, onDismissRequest = { shareMenu = false }) {
                    DropdownMenuItem(text = { Text("Share…") }, onClick = { shareMenu = false; vm.shareSelected(ctx, null) })
                    DropdownMenuItem(text = { Text("Send to WhatsApp") }, onClick = { shareMenu = false; vm.shareSelected(ctx, WhatsAppInfo.PKG_WHATSAPP) })
                    DropdownMenuItem(text = { Text("Send to WhatsApp Business") }, onClick = { shareMenu = false; vm.shareSelected(ctx, WhatsAppInfo.PKG_BUSINESS) })
                }
            }
            Action(Icons.Rounded.Download, "Export") { vm.exportSelected() }
            Action(Icons.Rounded.Delete, "Delete", Aurora.Coral) { confirmDelete = true }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete $count item(s)?") },
            text = { Text("They will be permanently removed from ReView. Copies in WhatsApp or your gallery are not affected.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteSelected() }) { Text("Delete", color = Aurora.Coral) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: androidx.compose.ui.graphics.Color = Aurora.TextHi, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Icon(icon, label, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Aurora.TextMid)
    }
}
