package com.review.mediamanager.data

import android.content.Context
import com.review.mediamanager.domain.AppTheme
import com.review.mediamanager.domain.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val theme: AppTheme = AppTheme.AMOLED,
    val gridView: Boolean = true,
    val sortOrder: SortOrder = SortOrder.DATE_NEWEST,
    val importToVault: Boolean = false,
    val biometricEnabled: Boolean = true,
    val autoLockVault: Boolean = true,
    val blockScreenshotsInVault: Boolean = true,
    val keepOriginalNames: Boolean = true,
    val userName: String = ""
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("review_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val current get() = _settings.value

    private fun load() = AppSettings(
        theme = runCatching { AppTheme.valueOf(prefs.getString("theme", AppTheme.AMOLED.name)!!) }.getOrDefault(AppTheme.AMOLED),
        gridView = prefs.getBoolean("grid", true),
        sortOrder = runCatching { SortOrder.valueOf(prefs.getString("sort", SortOrder.DATE_NEWEST.name)!!) }.getOrDefault(SortOrder.DATE_NEWEST),
        importToVault = prefs.getBoolean("import_vault", false),
        biometricEnabled = prefs.getBoolean("biometric", true),
        autoLockVault = prefs.getBoolean("auto_lock", true),
        blockScreenshotsInVault = prefs.getBoolean("secure_vault", true),
        keepOriginalNames = prefs.getBoolean("keep_names", true),
        userName = prefs.getString("user_name", "") ?: ""
    )

    fun update(block: (AppSettings) -> AppSettings) {
        val s = block(_settings.value)
        prefs.edit()
            .putString("theme", s.theme.name)
            .putBoolean("grid", s.gridView)
            .putString("sort", s.sortOrder.name)
            .putBoolean("import_vault", s.importToVault)
            .putBoolean("biometric", s.biometricEnabled)
            .putBoolean("auto_lock", s.autoLockVault)
            .putBoolean("secure_vault", s.blockScreenshotsInVault)
            .putBoolean("keep_names", s.keepOriginalNames)
            .putString("user_name", s.userName)
            .apply()
        _settings.value = s
    }
}
