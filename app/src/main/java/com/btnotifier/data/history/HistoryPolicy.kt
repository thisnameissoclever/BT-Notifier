package com.btnotifier.data.history

object HistoryPolicy {
    const val MAX_ROWS = 10_000
    const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000

    fun coalesce(samples: Collection<HistorySample>): List<HistorySample> {
        val newest = linkedMapOf<Pair<String, Long>, HistorySample>()
        samples.forEach { sample ->
            val key = sample.address to minuteBucket(sample.observedAt)
            if (shouldReplace(newest[key]?.observedAt, sample.observedAt)) newest[key] = sample
        }
        return newest.values.toList()
    }

    fun minuteBucket(at: Long): Long = at / 60_000L
    fun shouldReplace(previousAt: Long?, incomingAt: Long): Boolean = previousAt == null || incomingAt > previousAt
    fun retentionCutoff(now: Long): Long = now - RETENTION_MS
    fun shouldRetain(at: Long, now: Long): Boolean = at >= retentionCutoff(now)
}
