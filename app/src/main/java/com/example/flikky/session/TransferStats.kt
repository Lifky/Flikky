package com.example.flikky.session

import java.util.ArrayDeque

class TransferStats(private val nowMs: () -> Long) {
    private data class Sample(val timestampMs: Long, val bytes: Long)

    private val samples = ArrayDeque<Sample>()
    private var totalBytes: Long = 0L
    private var fileCount: Int = 0

    @Synchronized
    fun recordBytes(bytes: Long) {
        totalBytes += bytes
        samples.addLast(Sample(nowMs(), bytes))
        evict()
    }

    @Synchronized
    fun bytesPerSecond(): Long {
        evict()
        return samples.sumOf { it.bytes }
    }

    @Synchronized
    fun totalBytes(): Long = totalBytes

    @Synchronized
    fun incrementFileCount() {
        fileCount += 1
    }

    @Synchronized
    fun fileCount(): Int = fileCount

    private fun evict() {
        val cutoff = nowMs() - WINDOW_MS
        while (samples.isNotEmpty() && samples.first().timestampMs < cutoff) {
            samples.removeFirst()
        }
    }

    companion object { private const val WINDOW_MS = 1_000L }
}
