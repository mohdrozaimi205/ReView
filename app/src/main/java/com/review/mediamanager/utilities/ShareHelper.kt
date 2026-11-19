package com.review.mediamanager.utilities

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.review.mediamanager.imports.WhatsAppInfo
import com.review.mediamanager.security.VaultSession

object ShareHelper {
    /** targetPackage = null opens the normal Android share sheet. */
    fun shareMedia(context: Context, items: List<Pair<Uri, String>>, targetPackage: String?): Boolean {
        if (items.isEmpty()) return false
        val mime = when {
            items.all { it.second.startsWith("image/") } -> "image/*"
            items.all { it.second.startsWith("video/") } -> "video/*"
            else -> "*/*"
        }
        val intent = if (items.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = items[0].second
                putExtra(Intent.EXTRA_STREAM, items[0].first)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.first }))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        VaultSession.keepUnlockedOnce = true
        return try {
            if (targetPackage != null) {
                intent.setPackage(targetPackage)
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                context.startActivity(Intent.createChooser(intent, "Share with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /**
     * Consent workflow for View Once: ReView cannot and does not open View Once media.
     * Instead it helps the recipient politely ask the sender to send a normal, saveable copy.
     */
    fun requestSaveableCopy(context: Context, business: Boolean): Boolean {
        val text = "Hi! I received your View Once photo/video. If you're okay with me keeping it, " +
            "could you please send it again as a normal photo/video (with View Once turned off)? " +
            "No problem at all if you'd rather not. Thank you!"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage(if (business) WhatsAppInfo.PKG_BUSINESS else WhatsAppInfo.PKG_WHATSAPP)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try { context.startActivity(intent); true } catch (_: ActivityNotFoundException) { false }
    }
}
