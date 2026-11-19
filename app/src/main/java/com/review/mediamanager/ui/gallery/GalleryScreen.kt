package com.review.mediamanager.ui.gallery

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.review.mediamanager.AppContainer
import com.review.mediamanager.components.Chip
import com.review.mediamanager.components.EmptyState
import com.review.mediamanager.components.GradientButton
import com.review.mediamanager.components.ScreenHeader
import com.review.mediamanager.data.SettingsStore
import com.review.mediamanager.domain.GalleryCategory
import com.review.mediamanager.domain.SortOrder
import com.review.mediamanager.navigation.Routes
import com.review.mediamanager.navigation.ViewerQueue
import com.review.mediamanager.navigation.goTab
import com.review.mediamanager.ui.appContainer
import com.review.mediamanager.ui.appViewModel
import com.review.mediamanager.ui.theme.Aurora
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class GalleryViewModel(c: AppContainer, initial: GalleryCategory) : SelectionViewModel(c.repository) {
    val settingsStore: SettingsStore = c.settings
    val category = MutableStateFlow(initial)
    val query = MutableStateFlow("")
    val items = combine(repo.observeLibrary(false), category, query, c.settings.settings) { list, cat, q, s ->
        list.applyFilter(cat, q, s.sortOrder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun GalleryScreen(nav: NavController, cat: String) {
    val initial = runCatching { GalleryCategory.valueOf(cat) }.getOrDefault(GalleryCategory.ALL)
    val vm = appViewModel { GalleryViewModel(it, initial) }
    val repo = appContainer().repository
    val ctx = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val settings by vm.settingsStore.settings.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() } }
    LaunchedEffect(initial) { vm.category.value = initial }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader("Gallery", "${items.size} item${if (items.size == 1) "" else "s"}") {
                IconButton(onClick = { searching = !searching; if (!searching) vm.query.value = "" }) {
                    Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Search", tint = Aurora.TextHi)
                }
                IconButton(onClick = { vm.settingsStore.update { it.copy(gridView = !it.gridView) } }) {
                    Icon(if (settings.gridView) Icons.Rounded.ViewList else Icons.Rounded.GridView, "Layout", tint = Aurora.TextHi)
                }
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.Rounded.Sort, "Sort", tint = Aurora.TextHi) }
                    DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortOrder.entries.forEach { o ->
                            DropdownMenuItem(
                                text = { Text(o.label) },
                                trailingIcon = { if (o == settings.sortOrder) Icon(Icons.Rounded.Check, null, tint = Aurora.Mint) },
                                onClick = { sortMenu = false; vm.settingsStore.update { it.copy(sortOrder = o) } }
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(searching) {
                OutlinedTextField(
                    value = query, onValueChange = { vm.query.value = it },
                    placeholder = { Text("Search name, format, source…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Aurora.Mint),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)
                )
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GalleryCategory.entries) { c ->
                    Chip(c.label, c == category) {
                        if (c == GalleryCategory.VAULT) nav.goTab(Routes.VAULT) else { vm.category.value = c; vm.clearSelection() }
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 18.dp), color = Aurora.Mint)
            if (items.isEmpty()) {
                EmptyState(
                    Icons.Rounded.PhotoLibrary,
                    if (query.isNotBlank()) "No matches" else "No media in ${category.label}",
                    if (query.isNotBlank()) "Try another search term." else "Import photos and videos to see them here.",
                    Modifier.padding(bottom = 100.dp)
                ) {
                    if (query.isBlank()) GradientButton("Import media", null) { nav.goTab(Routes.IMPORT) }
                }
            } else {
                MediaCollection(
                    items, repo, settings.gridView, selection, bottomPadding = if (selection.isEmpty()) 110.dp else 200.dp,
                    onOpen = { m -> ViewerQueue.ids = items.map { it.id }; nav.navigate(Routes.viewer(m.id)) },
                    onToggle = { vm.toggle(it) }
                )
            }
        }
        AnimatedVisibility(selection.isNotEmpty(), modifier = Modifier.align(Alignment.BottomCenter)) {
            SelectionBar(selection.size, inVault = false, vm = vm, allIds = items.map { it.id },
                modifier = Modifier.padding(bottom = 76.dp))
        }
    }
}
