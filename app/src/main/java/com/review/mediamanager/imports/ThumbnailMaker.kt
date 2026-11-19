package com.review.mediamanager.imports

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import java.io.ByteArrayOutputStream
import java.io.File

/** Creates small JPEG thumbnails once at import time, so the gallery never decodes full media. */
object ThumbnailMaker {
    private const val TARGET = 480

    fun forImage(file: File, rotation: Int): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET && bounds.outHeight / (sample * 2) >= TARGET) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        encode(rotate(scale(bmp), rotation))
    }.getOrNull()

    fun forVideo(file: File, durationMs: Long): ByteArray? = runCatching {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            val at = if (durationMs > 2000) 1_000_000L else 0L
            val frame = mmr.getScaledFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, TARGET, TARGET)
                ?: mmr.frameAtTime
                ?: return null
            encode(frame)
        } finally {
            runCatching { mmr.release() }
        }
    }.getOrNull()

    private fun scale(src: Bitmap): Bitmap {
        val longSide = maxOf(src.width, src.height)
        if (longSide <= TARGET * 1.3) return src
        val f = TARGET.toFloat() / minOf(src.width, src.height)
        val out = Bitmap.createScaledBitmap(src, (src.width * f).toInt().coerceAtLeast(1), (src.height * f).toInt().coerceAtLeast(1), true)
        if (out !== src) src.recycle()
        return out
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }

    private fun encode(bmp: Bitmap): ByteArray {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
        bmp.recycle()
        return bos.toByteArray()
    }
}
