package com.review.mediamanager.domain

enum class MediaType { IMAGE, VIDEO }

/** Where a media item came from. WhatsApp sources are tagged by the user or detected from the share/URI. */
enum class MediaSource(val label: String) {
    GALLERY("Gallery"),
    FILES("Files"),
    WHATSAPP("WhatsApp"),
    WHATSAPP_BUSINESS("WhatsApp Business"),
    SHARED("Shared to ReView")
}

enum class SortOrder(val label: String) {
    DATE_NEWEST("Newest first"),
    DATE_OLDEST("Oldest first"),
    SIZE_LARGEST("Largest size"),
    SIZE_SMALLEST("Smallest size"),
    RESOLUTION_HIGHEST("Highest resolution"),
    RESOLUTION_LOWEST("Lowest resolution"),
    NAME("Name A–Z")
}

enum class GalleryCategory(val label: String) {
    ALL("All Media"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    WHATSAPP("WhatsApp"),
    WHATSAPP_BUSINESS("WA Business"),
    FAVORITES("Favorites"),
    VAULT("Private Vault")
}

enum class AppTheme(val label: String) {
    AMOLED("AMOLED Black"),
    GRAPHITE("Graphite"),
    MIDNIGHT("Midnight Blue")
}

data class ImportReport(
    val imported: Int = 0,
    val duplicates: Int = 0,
    val errors: List<String> = emptyList()
) {
    operator fun plus(o: ImportReport) =
        ImportReport(imported + o.imported, duplicates + o.duplicates, errors + o.errors)
}

data class ImportProgress(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val currentName: String = "",
    val lastReport: ImportReport? = null
)
