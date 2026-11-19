package com.review.mediamanager.data

import android.content.Context
import android.os.StatFs
import com.review.mediamanager.database.MediaEntity
import java.io.File

/**
 * All media lives in the app's private internal storage, so it never shows up in the
 * public gallery. Vault files are additionally encrypted (see security/VaultCrypto).
 */
class MediaStorage(context: Context) {
    val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
    val thumbDir = File(context.filesDir, "thumbs").apply { mkdirs() }
    val vaultDir = File(context.filesDir, "vault").apply { mkdirs() }
    val vaultThumbDir = File(vaultDir, "thumbs").apply { mkdirs() }
    val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
    val tempDir = File(context.cacheDir, "import").apply { mkdirs() }

    fun mediaFile(e: MediaEntity): File =
        if (e.inVault) File(vaultDir, e.fileName + ENC_EXT) else File(mediaDir, e.fileName)

    fun thumbFile(e: MediaEntity): File? = e.thumbName?.let {
        if (e.inVault) File(vaultThumbDir, it + ENC_EXT) else File(thumbDir, it)
    }

    fun plainMediaFile(fileName: String) = File(mediaDir, fileName)
    fun plainThumbFile(name: String) = File(thumbDir, name)
    fun vaultMediaFile(fileName: String) = File(vaultDir, fileName + ENC_EXT)
    fun vaultThumbFile(name: String) = File(vaultThumbDir, name + ENC_EXT)

    fun freeBytes(): Long = runCatching { StatFs(mediaDir.absolutePath).availableBytes }.getOrDefault(0L)
    fun totalBytes(): Long = runCatching { StatFs(mediaDir.absolutePath).totalBytes }.getOrDefault(0L)

    fun dirSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    fun appStorageBytes(): Long = dirSize(mediaDir) + dirSize(thumbDir) + dirSize(vaultDir)
    fun cacheBytes(): Long = dirSize(shareDir) + dirSize(tempDir)

    fun clearCache() {
        shareDir.listFiles()?.forEach { it.deleteRecursively() }
        tempDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    companion object {
        const val ENC_EXT = ".rve"
    }
}
