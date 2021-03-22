package com.github.jomof.ninjadepsparser

import com.github.jomof.ninjadepsparser.NinjaDepsDecoder.Record.EOF
import java.io.File
import java.io.RandomAccessFile
import java.nio.*
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets

/**
 * Ninja dependencies file format coder/decoder.
 * This is a compact representation of C/C++ file dependencies.
 *
 * Everything is aligned to four bytes, everything is little endian.
 *
 * First a 16 bytes header:
 *
 * The 12 bytes # ninjadeps\n
 * Followed an u32: The version: 3 or 4
 *
 * Then comes the data, which are records that start with a u32, of which the high bit indicates whether
 * this is a 'deps' record (1) or a path record (0), and the rest of the bits indicate the number of
 * bytes that follows the header (which is always a multiple of 4).
 *
 * A path records consists of:
 * The path itself (string) padded with zero to three NUL bytes to a 4-byte boundary.
 * Followed by an u32: The checksum, which is the binary complement of the ID.
 * The IDs of the paths are implicit: The first path in the file has ID 0, the second ID 1, etc.
 *
 * A 'deps' record consists of:
 *
 * The ID of the path for which we're listing the dependencies.
 * Followed by the mtime in nanoseconds as u64 (8 bytes) in version 4, or in seconds as u32 (4 bytes) in
 * version 3. On Windows, the year 2000 is used as the epoch instead of the Unix epoch.
 * The value 0 means 'does not exist'. 1 is used for mtimes that were actually 0.
 * Followed by the IDs of all the dependencies (paths).
 * Changes are simply appended, and later 'deps' records override earlier ones, which are considered 'dead'.
 *
 * When there are too many dead records (more than two thirds of more than 1000 records), the whole file
 * is written from scratch to 'recompact' it.
 */
private val MAGIC = "# ninjadeps\n".toByteArray()

class NinjaDepsDecoder(private val buffer : ByteBuffer) {
    private var schemaVersion = 0
    private var lastPathIndex = -1
    private var sizeOfTimestamp = 0

    init {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
    }

    sealed class Record {
        class PathRecord(val pathIndex : Int, val path : CharBuffer) : Record()
        class DependenciesRecord(val targetPath : Int, val timestamp: Long?, val dependencies : IntBuffer) : Record()
        object EOF : Record()
    }

    private fun readHeader() {
        MAGIC.forEach { expected ->
            val actual = buffer.get()
            if (actual != expected) {
                error("Was not a .ninja_deps file")
            }
        }
        schemaVersion = buffer.int
        assert(schemaVersion == 3 || schemaVersion == 4)
        sizeOfTimestamp = if (schemaVersion == 3) 4 else 8
    }

    fun read() : Record {
        if (buffer.position() == 0) readHeader()
        if (buffer.position() == buffer.capacity()) return EOF
        val typeAndSize = buffer.int
        val start = buffer.position()
        if (typeAndSize > 0) {
            val view = buffer.slice()
            var len = 0
            while(buffer.get().toInt() != 0 && len < (typeAndSize - 4)) ++len
            view.limit(len)
            val charBuffer : CharBuffer = StandardCharsets.UTF_8.decode(view)
            buffer.position(start + typeAndSize)
            ++lastPathIndex
            return Record.PathRecord(++lastPathIndex, charBuffer)
        } else {
            val size = typeAndSize - Int.MIN_VALUE
            val targetPath = buffer.int
            val timestamp : Long? =
                when(val provisional = if (schemaVersion == 3) buffer.int.toLong() else buffer.long) {
                    0L -> null // 'does not exist'
                    1L -> 0L
                    else -> provisional
                }
            val pathsSize = size - 4 - sizeOfTimestamp
            val view = buffer.slice()
            view.limit(pathsSize)
            view.order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(start + size)
            return Record.DependenciesRecord(targetPath, timestamp, view.asIntBuffer())
        }
    }

    companion object {
        /**
         * Stream all ninja dependencies inside the file the close the file at the end
         */
        fun readAllRecords(file : File, consumer : (Record) -> Unit) {
            RandomAccessFile(file, "r").use { raf ->
                val buffer = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.channel.size())
                val reader = NinjaDepsDecoder(buffer)

                var record = reader.read()
                while(record != EOF) {
                    consumer(record)
                    record = reader.read()
                }
            }
        }
    }
}