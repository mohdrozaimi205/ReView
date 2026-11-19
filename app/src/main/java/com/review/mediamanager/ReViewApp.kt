package com.review.mediamanager

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.review.mediamanager.data.MediaStorage
import com.review.mediamanager.data.SettingsStore
import com.review.mediamanager.database.ReViewDatabase
import com.review.mediamanager.imports.MediaImporter
import com.review.mediamanager.repository.MediaRepository
import com.review.mediamanager.security.KeyManager
import com.review.mediamanager.security.PinManager
import com.review.mediamanager.security.VaultSession
import com.review.mediamanager.utilities.CleanupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Simple manual dependency container (keeps the app light — no DI framework). */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database by lazy { ReViewDatabase.create(app) }
    val storage by lazy { MediaStorage(app) }
    val keys by lazy { KeyManager() }
    val settings by lazy { SettingsStore(app) }
    val pins by lazy { PinManager(app) }
    val repository by lazy {
        val dao = database.mediaDao()
        MediaRepository(app, dao, storage, keys, MediaImporter(app, dao, storage, keys), appScope)
    }
}

class ReViewApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.WARN).build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                VaultSession.onAppBackgrounded(container.settings.current.autoLockVault)
            }
        })
        CleanupWorker.schedule(this)
    }
}
