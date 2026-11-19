package com.review.mediamanager.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.review.mediamanager.database.MediaEntity
import com.review.mediamanager.database.isVideo
import com.review.mediamanager.repository.MediaRepository
import com.review.mediamanager.ui.theme.Aurora
import com.review.mediamanager.ui.theme.LocalPalette
import com.review.mediamanager.utilities.Fmt

/** Thumbnail model: a plain File for normal media, decrypted bytes (memory only) for vault media. */
@Composable
fun rememberThumbModel(item: MediaEntity, repo: MediaRepository): Any? {
    if (!item.inVault) return repo.storage.thumbFile(item)?.takeIf { it.exists() }
    val bytes by produceState<ByteArray?>(null, item.id, item.inVault) { value = repo.vaultThumbBytes(item) }
    return bytes
}

@Composable
fun MediaThumb(
    item: MediaEntity,
    repo: MediaRepository,
    shape: Shape,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    showBadges: Boolean = true
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val model = rememberThumbModel(item, repo)
    Box(modifier.clip(shape).background(p.surfaceHigh)) {
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(model).crossfade(180)
                    .memoryCacheKey("thumb_${item.id}_${item.inVault}").build(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(if (item.isVideo) Icons.Rounded.Movie else Icons.Rounded.Image, null, tint = Aurora.TextLow,
                modifier = Modifier.align(Alignment.Center).size(30.dp))
        }
        if (showBadges) {
            if (item.isVideo) {
                Tag(Fmt.duration(item.durationMs), Aurora.TextHi, Modifier.align(Alignment.BottomStart).padding(6.dp))
            }
            val q = Fmt.qualityLabel(item.width, item.height)
            if (q == "4K UHD" || q == "2K QHD") Tag(if (q == "4K UHD") "4K" else "2K", Aurora.Amber, Modifier.align(Alignment.TopStart).padding(6.dp))
            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                when {
                    item.inVault -> Icon(Icons.Rounded.Lock, null, tint = Aurora.Lavender, modifier = Modifier.size(15.dp))
                    item.favorite -> Icon(Icons.Rounded.Favorite, null, tint = Aurora.Coral, modifier = Modifier.size(15.dp))
                }
            }
        }
        if (selectionMode) {
            Box(Modifier.fillMaxSize().background(if (selected) Color(0x667CF5D0) else Color(0x33000000))
                .then(if (selected) Modifier.border(2.dp, Aurora.Mint, shape) else Modifier))
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                tint = if (selected) Aurora.Mint else Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape)
                    .background(if (selected) Color.Black else Color(0x55000000))
            )
        }
    }
}
