package com.review.mediamanager.ui.viewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.review.mediamanager.database.MediaEntity
import com.review.mediamanager.repository.MediaRepository
import com.review.mediamanager.ui.theme.Aurora

@Composable
fun ImageViewer(
    item: MediaEntity,
    repo: MediaRepository,
    originalResolution: Boolean,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit
) {
    val ctx = LocalContext.current
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }
    val rotation by animateFloatAsState(item.userRotation.toFloat(), label = "rot")

    LaunchedEffect(scale > 1f) { onZoomChanged(scale > 1f) }

    val vaultBytes by produceState<Result<ByteArray>?>(null, item.id, item.inVault) {
        value = if (item.inVault) runCatching { repo.vaultImageBytes(item) } else null
    }
    val model: Any? = if (item.inVault) vaultBytes?.getOrNull() else repo.storage.mediaFile(item)

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        // When rotated sideways, shrink so the whole picture still fits on screen.
        val sideways = (item.userRotation / 90) % 2 == 1
        val fit = if (sideways) minOf(wPx, hPx) / maxOf(wPx, hPx) else 1f

        if (model == null) {
            if (item.inVault && vaultBytes?.isFailure == true) Text("This vault file can't be decrypted.", color = Aurora.Coral)
            else CircularProgressIndicator(color = Aurora.Mint)
        } else {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(model)
                    .apply { if (originalResolution) size(Size.ORIGINAL) }
                    .memoryCacheKey("full_${item.id}_${originalResolution}")
                    .crossfade(150)
                    .build(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.id) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onDoubleTap = { tap ->
                                if (scale > 1f) { scale = 1f; offset = Offset.Zero }
                                else {
                                    scale = 2.5f
                                    offset = Offset((wPx / 2 - tap.x) * 1.5f, (hPx / 2 - tap.y) * 1.5f)
                                }
                            }
                        )
                    }
                    .pointerInput(item.id) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pointers = event.changes.count { it.pressed }
                                if (pointers >= 2 || scale > 1f) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    scale = (scale * zoom).coerceIn(1f, 10f)
                                    offset = if (scale <= 1.001f) Offset.Zero else {
                                        val maxX = (wPx * (scale - 1)) / 2
                                        val maxY = (hPx * (scale - 1)) / 2
                                        Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale * fit
                        scaleY = scale * fit
                        translationX = offset.x
                        translationY = offset.y
                        rotationZ = rotation
                    }
            )
        }
    }
}
