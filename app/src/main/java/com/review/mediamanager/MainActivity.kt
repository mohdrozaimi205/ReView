package com.review.mediamanager

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.review.mediamanager.domain.MediaSource
import com.review.mediamanager.navigation.ReViewNavHost
import com.review.mediamanager.navigation.Routes
import com.review.mediamanager.ui.theme.ReViewTheme
import kotlinx.coroutines.flow.MutableSharedFlow

/** Picture-in-picture bridge between the activity and the video player screen. */
class PipController(private val activity: MainActivity) {
    val inPip = mutableStateOf(false)
    var playerActive = false
    var isPlaying: () -> Boolean = { false }
    var aspect: Rational = Rational(16, 9)

    val supported get() = activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun enter(): Boolean {
        if (!supported) return false
        return runCatching { activity.enterPictureInPictureMode(params()) }.getOrDefault(false)
    }

    fun params(): PictureInPictureParams {
        val a = aspect
        val safe = when {
            a.toFloat() > 2.39f -> Rational(239, 100)
            a.toFloat() < 1f / 2.39f -> Rational(100, 239)
            else -> a
        }
        val b = PictureInPictureParams.Builder().setAspectRatio(safe)
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(playerActive && isPlaying()).setSeamlessResizeEnabled(true)
        return b.build()
    }

    fun refresh() {
        if (supported) runCatching { activity.setPictureInPictureParams(params()) }
    }
}

val LocalPip = staticCompositionLocalOf<PipController?> { null }

class MainActivity : AppCompatActivity() {
    private val navEvents = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 4)
    lateinit var pip: PipController
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pip = PipController(this)
        val container = (application as ReViewApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            ReViewTheme(settings.theme) {
                CompositionLocalProvider(LocalPip provides pip) {
                    ReViewNavHost(navEvents)
                }
            }
        }
        if (savedInstanceState == null) handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    /** Media shared to ReView from WhatsApp, WhatsApp Business, Gallery, Files… */
    private fun handleShare(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.toList().orEmpty()
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        val container = (application as ReViewApp).container
        val referrerPkg = referrer?.takeIf { it.scheme == "android-app" }?.host
        val s = container.settings.current
        container.repository.import(uris, MediaSource.SHARED, referrerPkg, s.importToVault, s.keepOriginalNames)
        navEvents.tryEmit(Routes.IMPORT)
        intent.action = null
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && pip.playerActive && pip.isPlaying()) pip.enter()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pip.inPip.value = isInPictureInPictureMode
    }
}
