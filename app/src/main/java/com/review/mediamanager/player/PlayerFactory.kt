package com.review.mediamanager.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import com.review.mediamanager.security.KeyManager
import java.io.File

object PlayerFactory {
    /**
     * Hardware-accelerated ExoPlayer (MediaCodec). Decoder fallback lets a 4K/60 stream drop to
     * another decoder instead of failing on phones whose primary decoder can't handle it.
     */
    fun create(context: Context, file: File, encrypted: Boolean, keys: KeyManager): ExoPlayer {
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        val builder = ExoPlayer.Builder(context, renderers)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
        val player = builder.build()
        val item = MediaItem.fromUri(Uri.fromFile(file))
        if (encrypted) {
            val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
            val source = ProgressiveMediaSource.Factory(EncryptedDataSource.Factory(keys), extractors).createMediaSource(item)
            player.setMediaSource(source)
        } else {
            player.setMediaItem(item)
        }
        player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
        player.prepare()
        return player
    }
}
