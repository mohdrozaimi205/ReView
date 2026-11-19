package com.review.mediamanager.ui.gallery

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.review.mediamanager.database.MediaEntity
import com.review.mediamanager.database.pixels
import com.review.mediamanager.domain.GalleryCategory
import com.review.mediamanager.domain.MediaSource
import com.review.mediamanager.domain.MediaType
import com.review.mediamanager.domain.SortOrder
import com.review.mediamanager.repository.MediaRepository
import com.review.mediamanager.utilities.ShareHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

fun List<MediaEntity>.applyFilter(cat: GalleryCategory, query: String, sort: SortOrder): List<MediaEntity> {
    val q = query.trim().lowercase()
    val filtered = asSequence().filter {
        when (cat) {
            GalleryCategory.ALL, GalleryCategory.VAULT -> true
            GalleryCategory.IMAGES -> it.type == MediaType.IMAGE
            GalleryCategory.VIDEOS -> it.type == MediaType.VIDEO
            GalleryCategory.WHATSAPP -> it.source == MediaSource.WHATSAPP
            GalleryCategory.WHATSAPP_BUSINESS -> it.source == MediaSource.WHATSAPP_BUSINESS
            GalleryCategory.FAVORITES -> it.favorite
        }
    }.filter {
        q.isEmpty() || it.displayName.lowercase().contains(q) || it.extension.lowercase() == q ||
            it.source.label.lowercase().contains(q) || (it.codec?.lowercase()?.contains(q) == true)
    }.toList()
    return when (sort) {
        SortOrder.DATE_NEWEST -> filtered.sortedByDescending { it.importedAt }
        SortOrder.DATE_OLDEST -> filtered.sortedBy { it.importedAt }
        SortOrder.SIZE_LARGEST -> filtered.sortedByDescending { it.sizeBytes }
        SortOrder.SIZE_SMALLEST -> filtered.sortedBy { it.sizeBytes }
        SortOrder.RESOLUTION_HIGHEST -> filtered.sortedByDescending { it.pixels }
        SortOrder.RESOLUTION_LOWEST -> filtered.sortedBy { it.pixels }
        SortOrder.NAME -> filtered.sortedBy { it.displayName.lowercase() }
    }
}

private val MediaEntity.extension get() = fileName.substringAfterLast('.', "")

/** Shared multi-select + bulk actions for Gallery and Vault. */
abstract class SelectionViewModel(protected val repo: MediaRepository) : ViewModel() {
    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun toggle(id: Long) { _selection.value = _selection.value.let { if (id in it) it - id else it + id } }
    fun selectAll(ids: List<Long>) { _selection.value = ids.toSet() }
    fun clearSelection() { _selection.value = emptySet() }
    protected fun say(m: String) { _messages.tryEmit(m) }

    protected fun work(block: suspend (List<Long>) -> String?) {
        val ids = _selection.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            try {
                block(ids)?.let { say(it) }
                clearSelection()
            } catch (e: Exception) {
                say("Something went wrong: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _busy.value = false
            }
        }
    }

    fun deleteSelected() = work { ids -> repo.delete(ids); "${ids.size} item(s) permanently deleted" }
    fun favoriteSelected(fav: Boolean) = work { ids -> repo.setFavorite(ids, fav); if (fav) "Added to Favorites" else "Removed from Favorites" }
    fun exportSelected() = work { ids -> val n = repo.exportToGallery(ids); "$n item(s) saved to Pictures/Movies › ReView" }
    fun moveSelectedToVault() = work { ids -> val n = repo.moveToVault(ids); "$n item(s) encrypted into the Vault" }
    fun removeSelectedFromVault() = work { ids -> val n = repo.removeFromVault(ids); "$n item(s) moved back to the Gallery" }

    fun shareSelected(context: Context, targetPackage: String?) = work { ids ->
        val uris = repo.shareUris(ids)
        if (ShareHelper.shareMedia(context, uris, targetPackage)) null
        else "That app isn't installed"
    }
}
