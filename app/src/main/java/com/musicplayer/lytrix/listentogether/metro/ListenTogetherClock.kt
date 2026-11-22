package com.musicplayer.lytrix.listentogether.metro

import android.os.SystemClock
import kotlin.math.max

/**
 * Maps the server's wall clock onto this device's monotonic clock, using ping/pong round trips.
 * This guarantees ultra-accurate synchronization between phones.
 */
class MetroClock(
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private val lock = Any()
    private var serverOffsetMs: Double? = null
    private var bestRoundTripMs = Long.MAX_VALUE

    fun reset() = synchronized(lock) {
        serverOffsetMs = null
        bestRoundTripMs = Long.MAX_VALUE
    }

    fun recordPong(
        clientTime: Long,
        serverReceiveTime: Long,
        serverSendTime: Long
    ): Boolean = synchronized(lock) {
        val receivedAt = elapsedRealtime()
        if (clientTime <= 0L || clientTime > receivedAt || receivedAt - clientTime > MAX_SAMPLE_AGE_MS) {
            return@synchronized false
        }
        if (serverReceiveTime <= 0L || serverSendTime < serverReceiveTime) return@synchronized false

        val roundTrip = receivedAt - clientTime
        val serverProcessing = serverSendTime - serverReceiveTime
        val networkRoundTrip = max(0L, roundTrip - serverProcessing)
        val sampleOffset = serverSendTime + networkRoundTrip / 2.0 - receivedAt
        val previousOffset = serverOffsetMs

        if (networkRoundTrip < bestRoundTripMs) bestRoundTripMs = networkRoundTrip
        val weight = if (networkRoundTrip <= bestRoundTripMs + GOOD_SAMPLE_MARGIN_MS) 0.25 else 0.05
        serverOffsetMs = previousOffset?.let { it + weight * (sampleOffset - it) } ?: sampleOffset
        previousOffset == null
    }

    fun now(): Long? = synchronized(lock) {
        serverOffsetMs?.let { (elapsedRealtime() + it).toLong() }
    }

    fun positionAt(
        position: Long,
        effectiveAtServerTime: Long?,
        isPlaying: Boolean
    ): Long {
        if (!isPlaying || effectiveAtServerTime == null || effectiveAtServerTime <= 0L) return position
        val serverNow = now() ?: return position
        return position + max(0L, serverNow - effectiveAtServerTime)
    }

    private companion object {
        const val MAX_SAMPLE_AGE_MS = 60_000L
        const val GOOD_SAMPLE_MARGIN_MS = 50L
    }
}
