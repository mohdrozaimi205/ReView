package com.review.mediamanager.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.review.mediamanager.domain.MediaSource
import com.review.mediamanager.domain.MediaType

class Converters {
    @TypeConverter fun typeToString(t: MediaType): String = t.name
    @TypeConverter fun stringToType(s: String): MediaType = MediaType.valueOf(s)
    @TypeConverter fun sourceToString(s: MediaSource): String = s.name
    @TypeConverter fun stringToSource(s: String): MediaSource =
        runCatching { MediaSource.valueOf(s) }.getOrDefault(MediaSource.FILES)
}

@Database(entities = [MediaEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class ReViewDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

    companion object {
        fun create(context: Context): ReViewDatabase =
            Room.databaseBuilder(context, ReViewDatabase::class.java, "review.db")
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
