package com.review.mediamanager.imports

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.review.mediamanager.data.MediaStorage
import com.review.mediamanager.database.MediaDao
import com.review.mediamanager.database.MediaEntity
import com.review.mediamanager.domain.ImportReport
import com.review.mediamanager.domain.MediaSource
import com.review.mediamanager.domain.MediaType
import com.review.mediamanager.security.KeyManager
import com.review.mediamanager.security.VaultCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/**
 * Copies user-selected media (Photo Picker, document picker or share sheet) into ReView's
 * private storage byte-for-byte — original quality is preserved, nothing is re-encoded.
 */
class MediaImporter(
    private val context: Context,
    private val dao: MediaDao,
    private val storage: MediaStorage,
    private val keys: KeyManager
) {
    private val resolver: ContentResolver = context.contentResolver

    suspend fun importOne(
        uri: Uri,
        requestedSource: MediaSource,
        referrerPackage: String?,
        toVault: Boolean,
        keepOriginalName: Boolean
    ): ImportReport = withContext(Dispatchers.IO) {
        val (name, size) = queryNameAndSize(uri)
        val label = name ?: uri.lastPathSegment ?: "file"
        try {
            val mime = resolveMime(uri, name)
            val type = when {
                mime.startsWith("image/") -> MediaType.IMAGE
                mime.startsWith("video/") -> MediaType.VIDEO
                else -> return@withContext ImportReport(errors = listOf("$label: unsupported file type ($mime). Only images and videos can be imported."))
            }
            if (size != null && size > 0 && size * 2 + 50L * 1024 * 1024 > storage.freeBytes()) {
                return@withContext ImportReport(errors = listOf("$label: not enough free storage on this phone."))
            }

            // 1) Stream-copy to a temp file while hashing (handles multi-GB videos with little RAM).
            val temp = File(storage.tempDir, UUID.randomUUID().toString())
            val digest = MessageDigest.getInstance("SHA-256")
            val input = resolver.openInputStream(uri) ?: throw FileNotFoundException()
            input.use { ins ->
                temp.outputStream().buffered(256 * 1024).use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            }
            if (temp.length() == 0L) {
                temp.delete()
                return@withContext ImportReport(errors = listOf("$label: the file is empty or not downloaded yet. Open it in WhatsApp first so it finishes downloading."))
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }

            // 2) Duplicate check.
            if (dao.findByHash(hash) != null) {
                temp.delete()
                return@withContext ImportReport(duplicates = 1)
            }

            // 3) Move into private media storage.
            val ext = extensionFor(name, mime, type)
            val base = UUID.randomUUID().toString()
            val fileName = "$base.$ext"
            val dest = storage.plainMediaFile(fileName)
            if (!temp.renameTo(dest)) {
                temp.copyTo(dest, overwrite = true); temp.delete()
            }

            // 4) Metadata + thumbnail.
            val meta = if (type == MediaType.IMAGE) MetadataReader.readImage(dest) else MetadataReader.readVideo(dest)
            if (type == MediaType.IMAGE && meta.width <= 0) {
                dest.delete()
                return@withContext ImportReport(errors = listOf("$label: this image format can't be decoded on this phone."))
            }
            val thumbBytes = if (type == MediaType.IMAGE) ThumbnailMaker.forImage(dest, meta.rotation)
            else ThumbnailMaker.forVideo(dest, meta.durationMs)
            val thumbName = thumbBytes?.let { "$base.jpg" }

            val display = if (keepOriginalName && !name.isNullOrBlank()) name
            else (if (type == MediaType.IMAGE) "IMG_" else "VID_") + base.take(8) + ".$ext"

            var entity = MediaEntity(
                displayName = display,
                mimeType = mime,
                type = type,
                source = SourceDetector.detect(uri, referrerPackage, requestedSource),
                sizeBytes = dest.length(),
                width = meta.width,
                height = meta.height,
                sourceRotation = meta.rotation,
                durationMs = meta.durationMs,
                frameRate = meta.frameRate,
                codec = meta.codec,
                bitrate = meta.bitrate,
                contentHash = hash,
                fileName = fileName,
                thumbName = thumbName,
                inVault = false
            )

            if (toVault) {
                VaultCrypto.encryptFile(dest, storage.vaultMediaFile(fileName), keys)
                dest.delete()
                thumbBytes?.let { VaultCrypto.encryptBytes(it, storage.vaultThumbFile(thumbName!!), keys) }
                entity = entity.copy(inVault = true)
            } else {
                thumbBytes?.let { storage.plainThumbFile(thumbName!!).writeBytes(it) }
            }

            try {
                dao.insert(entity)
            } catch (e: Exception) {
                storage.mediaFile(entity).delete(); storage.thumbFile(entity)?.delete()
                return@withContext ImportReport(duplicates = 1)
            }
            ImportReport(imported = 1)
        } catch (e: SecurityException) {
            ImportReport(errors = listOf("$label: permission to read this file was revoked. Please select it again."))
        } catch (e: FileNotFoundException) {
            ImportReport(errors = listOf("$label: file is no longer available (it may have been deleted or not downloaded)."))
        } catch (e: IOException) {
            ImportReport(errors = listOf("$label: ${e.message ?: "could not be copied"}"))
        } catch (e: Exception) {
            ImportReport(errors = listOf("$label: unexpected error – ${e.javaClass.simpleName}"))
        } finally {
            storage.tempDir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 6 * 3600_000L }?.forEach { it.delete() }
        }
    }

    private fun queryNameAndSize(uri: Uri): Pair<String?, Long?> = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                Pair(if (ni >= 0) c.getString(ni) else null, if (si >= 0 && !c.isNull(si)) c.getLong(si) else null)
            } else Pair(null, null)
        } ?: Pair(null, null)
    } catch (_: Exception) { Pair(null, null) }

    private fun resolveMime(uri: Uri, name: String?): String {
        val fromResolver = runCatching { resolver.getType(uri) }.getOrNull()
        if (!fromResolver.isNullOrBlank() && fromResolver != "application/octet-stream") return fromResolver
        val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when (ext) {
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mp4", "m4v" -> "video/mp4"
            "3gp" -> "video/3gpp"
            "heic", "heif" -> "image/heif"
            "webp" -> "image/webp"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: (fromResolver ?: "application/octet-stream")
        }
    }

    private fun extensionFor(name: String?, mime: String, type: MediaType): String {
        val fromName = name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.length in 2..5 && it.all { c -> c.isLetterOrDigit() } }
        if (fromName != null) return fromName
        return when (mime) {
            "video/x-matroska" -> "mkv"
            "video/quicktime" -> "mov"
            "video/webm" -> "webm"
            else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: if (type == MediaType.IMAGE) "jpg" else "mp4"
        }
    }
}
