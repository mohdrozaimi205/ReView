package com.review.mediamanager.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.review.mediamanager.domain.MediaSource
import com.review.mediamanager.domain.MediaType

@Entity(
    tableName = "media",
    indices = [
        Index(value = ["contentHash"], unique = true),
        Index(value = ["inVault"]),
        Index(value = ["importedAt"])
    ]
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val mimeType: String,
    val type: MediaType,
    val source: MediaSource,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    /** Rotation stored in the container (videos) or EXIF (images), degrees. */
    val sourceRotation: Int = 0,
    /** Extra rotation the user applied in the viewer, degrees (0/90/180/270). */
    val userRotation: Int = 0,
    val durationMs: Long = 0,
    /** Measured frame rate, null when it could not be determined. Never estimated. */
    val frameRate: Float? = null,
    val codec: String? = null,
    val bitrate: Int? = null,
    val contentHash: String,
    /** File name inside the app's private storage. */
    val fileName: String,
    val thumbName: String?,
    val inVault: Boolean = false,
    val favorite: Boolean = false,
    val importedAt: Long = System.currentTimeMillis()
)

val MediaEntity.isVideo get() = type == MediaType.VIDEO
/** Width/height as the viewer sees it (after container rotation). */
val MediaEntity.displayWidth get() = if (sourceRotation % 180 != 0) height else width
val MediaEntity.displayHeight get() = if (sourceRotation % 180 != 0) width else height
val MediaEntity.pixels get() = width.toLong() * height.toLong()
val MediaEntity.extension get() = fileName.substringAfterLast('.', "").uppercase()
