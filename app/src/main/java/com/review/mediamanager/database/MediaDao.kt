package com.review.mediamanager.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE inVault = :vault ORDER BY importedAt DESC")
    fun observeAll(vault: Boolean): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE inVault = 0 ORDER BY importedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<MediaEntity>>

    @Query("SELECT COUNT(*) FROM media WHERE inVault = 0 AND type = 'IMAGE'")
    fun observeImageCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media WHERE inVault = 0 AND type = 'VIDEO'")
    fun observeVideoCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media WHERE inVault = 1")
    fun observeVaultCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM media")
    fun observeTotalBytes(): Flow<Long>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun get(id: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE id = :id")
    fun observe(id: Long): Flow<MediaEntity?>

    @Query("SELECT * FROM media WHERE id IN (:ids)")
    suspend fun getMany(ids: List<Long>): List<MediaEntity>

    @Query("SELECT * FROM media")
    suspend fun getEverything(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE contentHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): MediaEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: MediaEntity): Long

    @Update
    suspend fun update(item: MediaEntity)

    @Delete
    suspend fun delete(item: MediaEntity)

    @Query("UPDATE media SET favorite = :fav WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<Long>, fav: Boolean)

    @Query("UPDATE media SET userRotation = :rotation WHERE id = :id")
    suspend fun setUserRotation(id: Long, rotation: Int)
}
