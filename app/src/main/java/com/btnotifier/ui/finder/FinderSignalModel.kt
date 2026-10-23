package com.btnotifier.ui.finder

enum class SignalProximity { NO_RECENT_SIGNAL, WEAK, MODERATE, STRONG }
enum class SignalDirection { UNKNOWN, STEADY, STRONGER, WEAKER }
data class SignalSample(val at: Long, val rssi: Double)
data class FinderSignalSnapshot(
    val samples: List<SignalSample>,
    val smoothedRssi: Double?,
    val proximity: SignalProximity,
    val direction: SignalDirection
)

class FinderSignalModel(
    private val maxSamples: Int = 40,
    private val staleAfterMs: Long = 10_000L
) {
    private val samples = ArrayDeque<SignalSample>()
    private var smoothed: Double? = null
    private var lastAt: Long? = null
    private var direction = SignalDirection.UNKNOWN

    init {
        require(maxSamples > 0)
        require(staleAfterMs > 0)
    }

    fun add(rssi: Int, at: Long): Boolean {
        if (rssi !in -127..-1 || lastAt?.let { at <= it } == true) return false
        val previous = smoothed.takeUnless { lastAt?.let { at - it >= staleAfterMs } == true }
        val next = previous?.let { it + 0.25 * (rssi - it) } ?: rssi.toDouble()
        direction = when {
            previous == null -> SignalDirection.UNKNOWN
            next - previous >= 2.0 -> SignalDirection.STRONGER
            next - previous <= -2.0 -> SignalDirection.WEAKER
            else -> SignalDirection.STEADY
        }
        smoothed = next
        lastAt = at
        samples.addLast(SignalSample(at, next))
        while (samples.size > maxSamples) samples.removeFirst()
        return true
    }

    fun snapshot(now: Long): FinderSignalSnapshot {
        val current = smoothed.takeIf { lastAt?.let { now >= it && now - it < staleAfterMs } == true }
        val proximity = when {
            current == null -> SignalProximity.NO_RECENT_SIGNAL
            current >= -55 -> SignalProximity.STRONG
            current >= -75 -> SignalProximity.MODERATE
            else -> SignalProximity.WEAK
        }
        return FinderSignalSnapshot(samples.toList(), current, proximity,
            if (current == null) SignalDirection.UNKNOWN else direction)
    }
}
