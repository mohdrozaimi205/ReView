package com.review.mediamanager.imports

import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import java.io.File

data class MediaMeta(
    val width: Int = 0,
    val height: Int = 0,
    val rotation: Int = 0,
    val durationMs: Long = 0,
    val frameRate: Float? = null,
    val codec: String? = null,
    val bitrate: Int? = null
)

/** Reads real metadata from the stored file. Values that can't be read are left empty, never guessed. */
object MetadataReader {

    fun readImage(file: File): MediaMeta {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val rotation = runCatching {
            ExifInterface(file.absolutePath).rotationDegrees
        }.getOrDefault(0)
        return MediaMeta(
            width = opts.outWidth.coerceAtLeast(0),
            height = opts.outHeight.coerceAtLeast(0),
            rotation = rotation,
            codec = opts.outMimeType
        )
    }

    fun readVideo(file: File): MediaMeta {
        var width = 0; var height = 0; var rotation = 0; var duration = 0L; var bitrate: Int? = null
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            width = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            bitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
        } catch (_: Exception) {
        } finally {
            runCatching { mmr.release() }
        }
        val (fps, codec, fw, fh) = measureTrack(file)
        if (width == 0) width = fw
        if (height == 0) height = fh
        return MediaMeta(width, height, rotation, duration, fps, codec, bitrate)
    }

    private data class TrackInfo(val fps: Float?, val codec: String?, val w: Int, val h: Int)

    /**
     * Measures the real frame rate from sample timestamps (container FPS fields are often missing
     * or wrong for phone/WhatsApp videos). Reads up to ~240 frames from the start of the stream.
     */
    private fun measureTrack(file: File): TrackInfo {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(file.absolutePath)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) { track = i; format = f; break }
            }
            if (track < 0 || format == null) return TrackInfo(null, null, 0, 0)
            val mime = format.getString(MediaFormat.KEY_MIME)
            val w = if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else 0
            val h = if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else 0
            ex.selectTrack(track)
            val stamps = ArrayList<Long>(256)
            var guard = 0
            while (stamps.size < 240 && guard < 5000) {
                guard++
                val t = ex.sampleTime
                if (t < 0) break
                stamps.add(t)
                if (!ex.advance()) break
            }
            var fps: Float? = null
            if (stamps.size >= 3) {
                stamps.sort()
                val span = stamps.last() - stamps.first()
                if (span > 0) fps = ((stamps.size - 1) * 1_000_000.0 / span).toFloat()
            }
            if (fps == null && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                fps = runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                    .getOrElse { runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrNull() }
            }
            return TrackInfo(fps?.let { Math.round(it * 100f) / 100f }, prettyCodec(mime), w, h)
        } catch (_: Exception) {
            return TrackInfo(null, null, 0, 0)
        } finally {
            runCatching { ex.release() }
        }
    }

    private fun prettyCodec(mime: String?): String? = when (mime) {
        null -> null
        MediaFormat.MIMETYPE_VIDEO_AVC -> "H.264 / AVC"
        MediaFormat.MIMETYPE_VIDEO_HEVC -> "H.265 / HEVC"
        MediaFormat.MIMETYPE_VIDEO_VP9 -> "VP9"
        MediaFormat.MIMETYPE_VIDEO_VP8 -> "VP8"
        MediaFormat.MIMETYPE_VIDEO_AV1 -> "AV1"
        MediaFormat.MIMETYPE_VIDEO_MPEG4 -> "MPEG-4"
        else -> mime.removePrefix("video/").uppercase()
    }
}
