package com.review.mediamanager.security

import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chunked AES-256-GCM file format (".rve") — allows random access so large encrypted
 * videos can be streamed and seeked directly by ExoPlayer without decrypting to disk.
 *
 * Layout:
 *   "RVE1" | int wrappedKeyLen | wrappedKey | long plainLength | int chunkSize
 *   chunk[i] = nonce(12) | ciphertext(len) | tag(16)     (AAD = chunk index)
 */
object VaultCrypto {
    const val CHUNK_SIZE = 64 * 1024
    private val MAGIC = byteArrayOf('R'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(), '1'.code.toByte())
    private const val NONCE = 12
    private const val TAG = 16
    private val random = SecureRandom()

    fun encrypt(input: InputStream, plainLength: Long, output: OutputStream, keys: KeyManager) {
        val dataKey = ByteArray(32).also { random.nextBytes(it) }
        val wrapped = keys.wrap(dataKey)
        val key = SecretKeySpec(dataKey, "AES")
        val out = DataOutputStream(output.buffered(256 * 1024))
        out.write(MAGIC)
        out.writeInt(wrapped.size)
        out.write(wrapped)
        out.writeLong(plainLength)
        out.writeInt(CHUNK_SIZE)
        val buf = ByteArray(CHUNK_SIZE)
        var index = 0L
        var total = 0L
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        while (true) {
            val n = readFully(input, buf)
            if (n <= 0) break
            val nonce = ByteArray(NONCE).also { random.nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG * 8, nonce))
            cipher.updateAAD(aad(index))
            val ct = cipher.doFinal(buf, 0, n)
            out.write(nonce)
            out.write(ct)
            total += n
            index++
            if (n < CHUNK_SIZE) break
        }
        out.flush()
        if (total != plainLength) throw IOException("Size changed while encrypting ($total of $plainLength bytes)")
        dataKey.fill(0)
    }

    fun encryptFile(src: File, dst: File, keys: KeyManager) {
        val tmp = File(dst.parentFile, dst.name + ".tmp")
        src.inputStream().use { input -> tmp.outputStream().use { encrypt(input, src.length(), it, keys) } }
        if (!tmp.renameTo(dst)) { tmp.delete(); throw IOException("Could not store encrypted file") }
    }

    fun encryptBytes(bytes: ByteArray, dst: File, keys: KeyManager) {
        dst.outputStream().use { encrypt(bytes.inputStream(), bytes.size.toLong(), it, keys) }
    }

    fun decryptToStream(src: File, output: OutputStream, keys: KeyManager) {
        EncryptedFileReader(src, keys).use { r ->
            var pos = 0L
            val buf = ByteArray(CHUNK_SIZE)
            while (pos < r.plainLength) {
                val n = r.read(pos, buf, 0, buf.size)
                if (n <= 0) break
                output.write(buf, 0, n)
                pos += n
            }
        }
        output.flush()
    }

    fun decryptFile(src: File, dst: File, keys: KeyManager) {
        val tmp = File(dst.parentFile, dst.name + ".tmp")
        tmp.outputStream().buffered(256 * 1024).use { decryptToStream(src, it, keys) }
        if (!tmp.renameTo(dst)) { tmp.delete(); throw IOException("Could not restore file") }
    }

    fun decryptBytes(src: File, keys: KeyManager): ByteArray {
        val bos = java.io.ByteArrayOutputStream(src.length().toInt().coerceAtLeast(1024))
        decryptToStream(src, bos, keys)
        return bos.toByteArray()
    }

    internal fun aad(index: Long): ByteArray = ByteBuffer.allocate(8).putLong(index).array()

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r < 0) break
            off += r
        }
        return off
    }

    internal const val NONCE_LEN = NONCE
    internal const val TAG_LEN = TAG
    internal fun magic() = MAGIC
}

/** Random-access reader for ".rve" files. Not thread safe — one per consumer. */
class EncryptedFileReader(file: File, keys: KeyManager) : Closeable {
    private val raf = RandomAccessFile(file, "r")
    private val key: SecretKeySpec
    val plainLength: Long
    private val chunkSize: Int
    private val headerLength: Long
    private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    private var cachedIndex = -1L
    private var cached: ByteArray = ByteArray(0)

    init {
        val magic = ByteArray(4)
        raf.seek(0)
        raf.readFully(magic)
        if (!magic.contentEquals(VaultCrypto.magic())) { raf.close(); throw IOException("Not a ReView vault file") }
        val wrappedLen = raf.readInt()
        if (wrappedLen <= 12 || wrappedLen > 1024) { raf.close(); throw IOException("Corrupted vault header") }
        val wrapped = ByteArray(wrappedLen)
        raf.readFully(wrapped)
        plainLength = raf.readLong()
        chunkSize = raf.readInt()
        headerLength = 4L + 4L + wrappedLen + 8L + 4L
        val dk = try { keys.unwrap(wrapped) } catch (e: Exception) {
            raf.close(); throw IOException("Vault key unavailable – the file can't be decrypted on this device", e)
        }
        key = SecretKeySpec(dk, "AES")
        dk.fill(0)
    }

    private fun chunk(index: Long): ByteArray {
        if (index == cachedIndex) return cached
        val stride = VaultCrypto.NONCE_LEN + chunkSize + VaultCrypto.TAG_LEN.toLong()
        val plainLen = minOf(chunkSize.toLong(), plainLength - index * chunkSize).toInt()
        val nonce = ByteArray(VaultCrypto.NONCE_LEN)
        val ct = ByteArray(plainLen + VaultCrypto.TAG_LEN)
        raf.seek(headerLength + index * stride)
        raf.readFully(nonce)
        raf.readFully(ct)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(VaultCrypto.TAG_LEN * 8, nonce))
        cipher.updateAAD(VaultCrypto.aad(index))
        cached = try { cipher.doFinal(ct) } catch (e: Exception) { throw IOException("Vault file is damaged (chunk $index)", e) }
        cachedIndex = index
        return cached
    }

    /** Reads plaintext starting at [position]. Returns bytes read, or -1 at end. */
    fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= plainLength) return -1
        val index = position / chunkSize
        val within = (position - index * chunkSize).toInt()
        val data = chunk(index)
        val n = minOf(length, data.size - within)
        System.arraycopy(data, within, buffer, offset, n)
        return n
    }

    override fun close() {
        runCatching { raf.close() }
        cached.fill(0)
    }
}
