package com.review.mediamanager.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.review.mediamanager.security.EncryptedFileReader
import com.review.mediamanager.security.KeyManager
import java.io.File

/**
 * Lets ExoPlayer stream and seek inside an encrypted vault video. Decryption happens
 * chunk-by-chunk in memory; no decrypted copy is ever written to disk.
 */
class EncryptedDataSource(private val keys: KeyManager) : BaseDataSource(false) {
    private var reader: EncryptedFileReader? = null
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val path = dataSpec.uri.path ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        val r = try {
            EncryptedFileReader(File(path), keys)
        } catch (e: Exception) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }
        reader = r
        position = dataSpec.position
        if (position > r.plainLength) throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else r.plainLength - position
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val r = reader ?: return C.RESULT_END_OF_INPUT
        val want = minOf(length.toLong(), remaining).toInt()
        val n = try {
            r.read(position, buffer, offset, want)
        } catch (e: Exception) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }
        if (n <= 0) return C.RESULT_END_OF_INPUT
        position += n
        remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        reader?.close()
        reader = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(private val keys: KeyManager) : DataSource.Factory {
        override fun createDataSource(): DataSource = EncryptedDataSource(keys)
    }
}
