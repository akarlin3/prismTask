package com.averycorp.prismtask.domain.model.notifications

/**
 * Expands [VibrationPreset] values into the `long[]` pattern format expected
 * by Android's [android.os.VibrationEffect.createWaveform] / the legacy
 * [android.os.Vibrator.vibrate]. Pattern format is:
 *
 *     [initial delay, vibrate, pause, vibrate, pause, ...]
 *
 * Durations are in milliseconds. [VibrationIntensity] maps to per-step
 * amplitude applied when callers target API 26+ waveform effects.
 *
 * Kept Android-free (pure `LongArray`) so this can be unit-tested without
 * instrumentation. The platform adapter lives in [NotificationHelper].
 */
object VibrationPatterns {
    /** The leading 0 is required: it's the initial wait. */
    private val SINGLE = longArrayOf(0L, 400L)
    private val DOUBLE = longArrayOf(0L, 200L, 120L, 200L)
    private val TRIPLE = longArrayOf(0L, 150L, 100L, 150L, 100L, 150L)
    private val LONG_BUZZ = longArrayOf(0L, 1_200L)
    private val SOS = longArrayOf(
        0L,
        // S: three short
        150L,
        100L,
        150L,
        100L,
        150L,
        300L,
        // O: three long
        350L,
        100L,
        350L,
        100L,
        350L,
        300L,
        // S: three short
        150L,
        100L,
        150L,
        100L,
        150L
    )
    private val HEARTBEAT = longArrayOf(
        0L,
        // Double beat, longer pause — repeats twice
        90L,
        90L,
        180L,
        500L,
        90L,
        90L,
        180L
    )
    private val WAVE = longArrayOf(
        0L,
        80L,
        80L,
        140L,
        80L,
        220L,
        80L,
        320L,
        80L,
        220L,
        80L,
        140L,
        80L,
        80L
    )

    /**
     * Looks up the pattern for [preset]. Returns `null` when the user
     * picked [VibrationPreset.NONE] or [VibrationPreset.CUSTOM] — callers
     * provide their own [LongArray] from the custom pattern recorder for
     * the latter case.
     */
    fun patternFor(preset: VibrationPreset): LongArray? = when (preset) {
        VibrationPreset.NONE -> null
        VibrationPreset.SINGLE_PULSE -> SINGLE.copyOf()
        VibrationPreset.DOUBLE_PULSE -> DOUBLE.copyOf()
        VibrationPreset.TRIPLE -> TRIPLE.copyOf()
        VibrationPreset.LONG_BUZZ -> LONG_BUZZ.copyOf()
        VibrationPreset.SOS -> SOS.copyOf()
        VibrationPreset.HEARTBEAT -> HEARTBEAT.copyOf()
        VibrationPreset.WAVE -> WAVE.copyOf()
        VibrationPreset.CUSTOM -> null
    }

    /**
     * Expands [basePattern] to [repeatCount] full repetitions. The initial
     * delay (index 0) is preserved once at the start; the on/off pairs are
     * repeated as-is. [repeatCount] is clamped to [1, 10] (or 0 meaning
     * continuous — callers should pass that value unchanged so the platform
     * adapter can request a repeating waveform).
     */
    fun repeat(basePattern: LongArray, repeatCount: Int): LongArray {
        require(basePattern.isNotEmpty()) { "pattern must be non-empty" }
        val safeCount = repeatCount.coerceIn(1, 10)
        if (safeCount == 1) return basePattern.copyOf()

        val initialDelay = basePattern[0]
        val pulsesAndGaps = basePattern.copyOfRange(1, basePattern.size)
        val out = LongArray(1 + pulsesAndGaps.size * safeCount)
        out[0] = initialDelay
        var writeIndex = 1
        repeat(safeCount) {
            for (v in pulsesAndGaps) {
                out[writeIndex++] = v
            }
        }
        return out
    }

    /**
     * Parses a CSV pattern string (e.g. "0,200,120,200") into a
     * [LongArray]. Non-numeric entries are dropped, mirroring the
     * `NotificationProfileEntity` CSV decoder's tolerant behavior.
     */
    fun decodeCsv(csv: String?): LongArray {
        if (csv.isNullOrBlank()) return longArrayOf()
        return csv.split(",").mapNotNull { it.trim().toLongOrNull() }.toLongArray()
    }

    /** Reverse of [decodeCsv]. */
    fun encodeCsv(pattern: LongArray): String = pattern.joinToString(",")

    /**
     * Total wall-clock duration of [pattern] in millis. Useful for UI
     * previews that want to show a progress bar or disable a "Test"
     * button while playback is in flight.
     */
    fun totalDurationMs(pattern: LongArray): Long {
        if (pattern.isEmpty()) return 0L
        var sum = 0L
        for (v in pattern) sum += v
        return sum
    }
}
