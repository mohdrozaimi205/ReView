package com.review.mediamanager.imports

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import com.review.mediamanager.domain.MediaSource

object WhatsAppInfo {
    const val PKG_WHATSAPP = "com.whatsapp"
    const val PKG_BUSINESS = "com.whatsapp.w4b"

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    /** SAF location of WhatsApp's user-visible media folder (shared storage, not private app data). */
    fun mediaFolderUri(business: Boolean): Uri {
        val path = if (business)
            "primary:Android/media/com.whatsapp.w4b/WhatsApp Business/Media"
        else
            "primary:Android/media/com.whatsapp/WhatsApp/Media"
        return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", path)
    }

    fun launch(context: Context, business: Boolean): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(if (business) PKG_BUSINESS else PKG_WHATSAPP)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}

/**
 * Works out whether an item came from WhatsApp or WhatsApp Business using only information
 * Android gives us legitimately: the user's chosen import button, the URI authority/path
 * and the package that shared it.
 */
object SourceDetector {
    fun detect(uri: Uri, referrerPackage: String?, requested: MediaSource): MediaSource {
        if (requested == MediaSource.WHATSAPP || requested == MediaSource.WHATSAPP_BUSINESS) return requested
        val text = (uri.authority.orEmpty() + " " + Uri.decode(uri.toString())).lowercase()
        val ref = referrerPackage.orEmpty().lowercase()
        return when {
            ref == WhatsAppInfo.PKG_BUSINESS || "com.whatsapp.w4b" in text || "whatsapp business" in text -> MediaSource.WHATSAPP_BUSINESS
            ref == WhatsAppInfo.PKG_WHATSAPP || "com.whatsapp" in text || "/whatsapp/" in text || "whatsapp images" in text || "whatsapp video" in text -> MediaSource.WHATSAPP
            else -> requested
        }
    }
}
