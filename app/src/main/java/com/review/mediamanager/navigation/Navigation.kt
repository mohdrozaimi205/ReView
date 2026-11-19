package com.review.mediamanager.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.review.mediamanager.ui.gallery.GalleryScreen
import com.review.mediamanager.ui.home.HomeScreen
import com.review.mediamanager.ui.importer.ImportScreen
import com.review.mediamanager.ui.importer.WhatsAppScreen
import com.review.mediamanager.ui.settings.AboutScreen
import com.review.mediamanager.ui.settings.PrivacyScreen
import com.review.mediamanager.ui.settings.SettingsScreen
import com.review.mediamanager.ui.splash.SplashScreen
import com.review.mediamanager.ui.theme.Aurora
import com.review.mediamanager.ui.theme.LocalPalette
import com.review.mediamanager.ui.vault.VaultScreen
import com.review.mediamanager.ui.viewer.ViewerScreen
import kotlinx.coroutines.flow.SharedFlow

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val GALLERY = "gallery?cat={cat}"
    const val IMPORT = "import"
    const val VAULT = "vault"
    const val SETTINGS = "settings"
    const val VIEWER = "viewer/{id}"
    const val WHATSAPP = "whatsapp"
    const val PRIVACY = "privacy"
    const val ABOUT = "about"
    fun gallery(cat: String = "ALL") = "gallery?cat=$cat"
    fun viewer(id: Long) = "viewer/$id"
}

/** The list the viewer swipes through, set by whichever screen opened it. */
object ViewerQueue {
    var ids: List<Long> = emptyList()
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Rounded.Home),
    Tab(Routes.GALLERY, "Gallery", Icons.Rounded.PhotoLibrary),
    Tab(Routes.IMPORT, "Import", Icons.Rounded.Add),
    Tab(Routes.VAULT, "Vault", Icons.Rounded.Lock),
    Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings)
)

fun NavHostController.goTab(route: String) {
    val target = if (route == Routes.GALLERY) Routes.gallery() else route
    navigate(target) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun ReViewNavHost(navEvents: SharedFlow<String>) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    var pending by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        navEvents.collect { r ->
            val cur = nav.currentDestination?.route
            if (cur == null || cur == Routes.SPLASH) pending = r else nav.goTab(r)
        }
    }

    Box(Modifier.fillMaxSize().background(LocalPalette.current.background)) {
        NavHost(nav, startDestination = Routes.SPLASH, modifier = Modifier.fillMaxSize()) {
            composable(Routes.SPLASH) {
                SplashScreen {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.SPLASH) { inclusive = true } }
                    pending?.let { p -> nav.goTab(p); pending = null }
                }
            }
            composable(Routes.HOME) { HomeScreen(nav) }
            composable(Routes.GALLERY, arguments = listOf(navArgument("cat") { type = NavType.StringType; defaultValue = "ALL" })) {
                GalleryScreen(nav, it.arguments?.getString("cat") ?: "ALL")
            }
            composable(Routes.IMPORT) { ImportScreen(nav) }
            composable(Routes.VAULT) { VaultScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.WHATSAPP) { WhatsAppScreen(nav) }
            composable(Routes.PRIVACY) { PrivacyScreen(nav) }
            composable(Routes.ABOUT) { AboutScreen(nav) }
            composable(Routes.VIEWER, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                ViewerScreen(nav, it.arguments?.getLong("id") ?: 0L)
            }
        }

        val showBar = route in tabs.map { it.route }
        AnimatedVisibility(
            visible = showBar,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FloatingNavBar(current = route) { nav.goTab(it) }
        }
    }
}

/** Floating pill navigation with a raised gradient Import button in the middle. */
@Composable
private fun FloatingNavBar(current: String?, onSelect: (String) -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(30.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().height(68.dp).clip(shape).background(p.surface.copy(alpha = 0.96f))
                .border(1.dp, p.stroke, shape).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { t ->
                val selected = current == t.route
                if (t.route == Routes.IMPORT) {
                    Box(
                        Modifier.size(54.dp).clip(CircleShape).background(Aurora.brand)
                            .clickable { onSelect(t.route) },
                        contentAlignment = Alignment.Center
                    ) { Icon(t.icon, t.label, tint = Color(0xFF07130F), modifier = Modifier.size(28.dp)) }
                } else {
                    Column(
                        Modifier.weight(1f).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(t.route) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(t.icon, t.label, tint = if (selected) Aurora.Mint else Aurora.TextLow, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(3.dp))
                        Text(t.label, style = MaterialTheme.typography.labelSmall, color = if (selected) Aurora.TextHi else Aurora.TextLow)
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.size(width = if (selected) 16.dp else 0.dp, height = 3.dp).clip(CircleShape).background(Aurora.brand))
                    }
                }
            }
        }
    }
}
