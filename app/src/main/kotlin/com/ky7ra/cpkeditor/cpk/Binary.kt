package com.ky7ra.cpkeditor.cpk

import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Thrown when a file is not a CPK, or is a CPK that this library cannot (yet) understand. */
class CpkFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

// ---- Big/little endian readers over a ByteArray ----------------------------------------------

internal fun ByteArray.u8(o: Int): Int = this[o].toInt() and 0xFF

internal fun ByteArray.u16be(o: Int): Int = (u8(o) shl 8) or u8(o + 1)

internal fun ByteArray.u32be(o: Int): Long =
    (u8(o).toLong() shl 24) or (u8(o + 1).toLong() shl 16) or (u8(o + 2).toLong() shl 8) or u8(o + 3).toLong()

internal fun ByteArray.u64be(o: Int): Long = (u32be(o) shl 32) or u32be(o + 4)

internal fun ByteArray.u32le(o: Int): Long =
    u8(o).toLong() or (u8(o + 1).toLong() shl 8) or (u8(o + 2).toLong() shl 16) or (u8(o + 3).toLong() shl 24)

internal fun ByteArray.u64le(o: Int): Long = (u32le(o + 4) shl 32) or u32le(o)

internal fun alignUp(value: Long, alignment: Long): Long = (value + alignment - 1) / alignment * alignment

// ---- Random access input --------------------------------------------------------------------

/**
 * Minimal positional-read abstraction so the CPK code runs both on Android (content URIs) and on
 * the plain JVM (unit tests). Implementations must tolerate concurrent calls.
 */
interface RandomAccessSource : Closeable {
    val length: Long

    /** Reads up to [len] bytes at [position]. Returns the byte count, or -1 at end of file. */
    fun read(position: Long, dst: ByteArray, offset: Int, len: Int): Int
}

/** Reads exactly [len] bytes at [position] or throws [EOFException]. */
fun RandomAccessSource.readFully(position: Long, len: Int): ByteArray {
    require(len >= 0) { "negative length" }
    val out = ByteArray(len)
    var done = 0
    while (done < len) {
        val n = read(position + done, out, done, len - done)
        if (n <= 0) throw EOFException("Unexpected end of file at ${position + done}")
        done += n
    }
    return out
}

class ByteArraySource(private val data: ByteArray) : RandomAccessSource {
    override val length: Long get() = data.size.toLong()

    override fun read(position: Long, dst: ByteArray, offset: Int, len: Int): Int {
        if (position >= data.size) return -1
        val n = minOf(len.toLong(), data.size - position).toInt()
        System.arraycopy(data, position.toInt(), dst, offset, n)
        return n
    }

    override fun close() = Unit
}

/** Positional reads through a [FileChannel]; works for files and for Android file descriptors. */
class ChannelSource(
    private val channel: FileChannel,
    private val onClose: Closeable? = null,
) : RandomAccessSource {
    override val length: Long = channel.size()

    override fun read(position: Long, dst: ByteArray, offset: Int, len: Int): Int =
        channel.read(ByteBuffer.wrap(dst, offset, len), position)

    override fun close() {
        try {
            channel.close()
        } finally {
            onClose?.close()
        }
    }

    companion object {
        fun forFile(file: File): ChannelSource {
            val raf = RandomAccessFile(file, "r")
            return ChannelSource(raf.channel, raf)
        }
    }
}
