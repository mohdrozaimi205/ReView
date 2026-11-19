package com.review.mediamanager.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.review.mediamanager.data.MediaStorage
import com.review.mediamanager.database.MediaDao
import com.review.mediamanager.database.MediaEntity
import com.review.mediamanager.domain.ImportProgress
import com.review.mediamanager.domain.ImportReport
import com.review.mediamanager.domain.MediaSource
import com.review.mediamanager.domain.MediaType
import com.review.mediamanager.imports.MediaImporter
import com.review.mediamanager.security.KeyManager
import com.review.mediamanager.security.VaultCrypto
import com.review.mediamanager.security.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class MediaRepository(
    private val context: Context,
    private val dao: MediaDao,
    val storage: MediaStorage,
    val keys: KeyManager,
    private val importer: MediaImporter,
    private val appScope: CoroutineScope
) {
    fun observeLibrary(vault: Boolean): Flow<List<MediaEntity>> = dao.observeAll(vault)
    fun observeRecent(limit: Int = 12) = dao.observeRecent(limit)
    fun observeImageCount() = dao.observeImageCount()
    fun observeVideoCount() = dao.observeVideoCount()
    fun observeVaultCount() = dao.observeVaultCount()
    fun observeTotalBytes() = dao.observeTotalBytes()
    fun observeItem(id: Long) = dao.observe(id)
    suspend fun get(id: Long) = dao.get(id)

    // ---------------------------------------------------------------- import
    private val importMutex = Mutex()
    private val _progress = MutableStateFlow(ImportProgress())
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

    /** Runs in the application scope so an import keeps going if the user changes screens. */
    fun import(uris: List<Uri>, source: MediaSource, referrer: String?, toVault: Boolean, keepNames: Boolean) {
        if (uris.isEmpty()) return
        appScope.launch {
            importMutex.withLock {
                var report = ImportReport()
                _progress.value = ImportProgress(running = true, done = 0, total = uris.size)
                uris.forEachIndexed { i, uri ->
                    _progress.value = _progress.value.copy(done = i, currentName = uri.lastPathSegment ?: "")
                    report += importer.importOne(uri, source, referrer, toVault, keepNames)
                }
                _progress.value = ImportProgress(running = false, done = uris.size, total = uris.size, lastReport = report)
            }
        }
    }

    fun clearReport() { _progress.value = _progress.value.copy(lastReport = null) }

    // ---------------------------------------------------------------- edits
    suspend fun setFavorite(ids: List<Long>, fav: Boolean) = dao.setFavorite(ids, fav)

    suspend fun rotate(item: MediaEntity, clockwise: Boolean = true) {
        val r = ((item.userRotation + if (clockwise) 90 else 270) % 360)
        dao.setUserRotation(item.id, r)
    }

    suspend fun delete(ids: List<Long>) = withContext(Dispatchers.IO) {
        dao.getMany(ids).forEach { e ->
            storage.mediaFile(e).delete()
            storage.thumbFile(e)?.delete()
            VaultSession.thumbCache.remove(e.id)
            dao.delete(e)
        }
    }

    suspend fun deleteEverything() = withContext(Dispatchers.IO) {
        delete(dao.getEverything().map { it.id })
        storage.clearCache()
    }

    /** Encrypts items into the Private Vault (removes the readable copies). */
    suspend fun moveToVault(ids: List<Long>): Int = withContext(Dispatchers.IO) {
        var moved = 0
        dao.getMany(ids).filter { !it.inVault }.forEach { e ->
            val plain = storage.plainMediaFile(e.fileName)
            VaultCrypto.encryptFile(plain, storage.vaultMediaFile(e.fileName), keys)
            e.thumbName?.let { t ->
                val pt = storage.plainThumbFile(t)
                if (pt.exists()) { VaultCrypto.encryptBytes(pt.readBytes(), storage.vaultThumbFile(t), keys); pt.delete() }
            }
            dao.update(e.copy(inVault = true))
            plain.delete()
            moved++
        }
        moved
    }

    suspend fun removeFromVault(ids: List<Long>): Int = withContext(Dispatchers.IO) {
        var moved = 0
        dao.getMany(ids).filter { it.inVault }.forEach { e ->
            val enc = storage.vaultMediaFile(e.fileName)
            VaultCrypto.decryptFile(enc, storage.plainMediaFile(e.fileName), keys)
            e.thumbName?.let { t ->
                val et = storage.vaultThumbFile(t)
                if (et.exists()) { storage.plainThumbFile(t).writeBytes(VaultCrypto.decryptBytes(et, keys)); et.delete() }
            }
            dao.update(e.copy(inVault = false))
            enc.delete()
            VaultSession.thumbCache.remove(e.id)
            moved++
        }
        moved
    }

    suspend fun vaultThumbBytes(e: MediaEntity): ByteArray? = withContext(Dispatchers.IO) {
        VaultSession.thumbCache.get(e.id)?.let { return@withContext it }
        val f = storage.thumbFile(e) ?: return@withContext null
        if (!f.exists()) return@withContext null
        runCatching { VaultCrypto.decryptBytes(f, keys) }.getOrNull()?.also { VaultSession.thumbCache.put(e.id, it) }
    }

    suspend fun vaultImageBytes(e: MediaEntity): ByteArray = withContext(Dispatchers.IO) {
        VaultCrypto.decryptBytes(storage.mediaFile(e), keys)
    }

    // ---------------------------------------------------------------- export & share
    /** Saves a copy to the public gallery (Pictures/ReView or Movies/ReView). User-initiated only. */
    suspend fun exportToGallery(ids: List<Long>): Int = withContext(Dispatchers.IO) {
        var count = 0
        dao.getMany(ids).forEach { e ->
            val resolver = context.contentResolver
            val isImage = e.type == MediaType.IMAGE
            val collection = if (isImage) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, e.displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, e.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, (if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES) + "/ReView")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: throw IOException("Gallery refused the file")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    if (e.inVault) VaultCrypto.decryptToStream(storage.mediaFile(e), out, keys)
                    else storage.mediaFile(e).inputStream().use { it.copyTo(out, 256 * 1024) }
                } ?: throw IOException("Could not write to gallery")
                values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                count++
            } catch (ex: Exception) {
                resolver.delete(uri, null, null)
                throw ex
            }
        }
        count
    }

    /** Produces content:// URIs for the share sheet. Vault items are decrypted into a short-lived cache copy. */
    suspend fun shareUris(ids: List<Long>): List<Pair<Uri, String>> = withContext(Dispatchers.IO) {
        storage.shareDir.listFiles()?.forEach { it.delete() }
        dao.getMany(ids).map { e ->
            if (!e.inVault) {
                return@map FileProvider.getUriForFile(context, context.packageName + ".fileprovider", storage.mediaFile(e)) to e.mimeType
            }
            val safe = e.displayName.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { e.fileName }
            val out = File(storage.shareDir, "${e.id}_$safe")
            VaultCrypto.decryptFile(storage.mediaFile(e), out, keys)
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", out) to e.mimeType
        }
    }
}
