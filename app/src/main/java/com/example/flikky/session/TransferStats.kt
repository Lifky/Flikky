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
    fun decrementFileCount() {
        if (fileCount > 0) fileCount -= 1
    }

    @Synchronized
    fun fileCount(): Int = fileCount

    /**
     * 清零所有内部统计。ServiceLocator.reset 在 Service 销毁时调，
     * 这样下次启服从干净状态开始。代价：旧 stats 实例被 UI 缓存的引用
     * 也会"自动归零"——但这是有意为之（避免新启服时残留旧数字）。
     */
    @Synchronized
    fun reset() {
        samples.clear()
        totalBytes = 0L
        fileCount = 0
    }

    private fun evict() {
        val cutoff = nowMs() - WINDOW_MS
        while (samples.isNotEmpty() && samples.first().timestampMs < cutoff) {
            samples.removeFirst()
        }
    }

    companion object { private const val WINDOW_MS = 1_000L }
}
