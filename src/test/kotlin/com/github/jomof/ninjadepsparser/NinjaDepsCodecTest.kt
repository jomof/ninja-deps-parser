package com.github.jomof.ninjadepsparser

import com.github.jomof.ninjadepsparser.NinjaDepsDecoder.Companion.readAllRecords
import com.github.jomof.ninjadepsparser.NinjaDepsDecoder.Record.DependenciesRecord
import com.github.jomof.ninjadepsparser.NinjaDepsDecoder.Record.PathRecord
import org.junit.Test
import java.nio.channels.FileChannel
import java.nio.MappedByteBuffer
import java.io.File
import java.io.RandomAccessFile

class NinjaDepsCodecTest {
    @Test
    fun basic() {
        var pathCounts = 0
        var dependencyCounts = 0
        readAllRecords(File("data/basic.ninja_deps")) { record ->
            when(record) {
                is PathRecord -> pathCounts++
                is DependenciesRecord -> dependencyCounts++
                else -> { }
            }
        }
        if (pathCounts != 128) error("$pathCounts")
        if (dependencyCounts != 1) error("$dependencyCounts")
    }
}