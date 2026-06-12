package com.cauldron.myriad.engine.rng

import kotlinx.serialization.Serializable

/**
 * PCG-XSH-RR ("pcg32") implemented in-repo so the stream is stable forever —
 * saves outlive Kotlin versions, so we never depend on kotlin.random (MASTER_PLAN §9).
 * Verified against the reference implementation's output (see Pcg32Test golden vectors).
 */
@Serializable
data class Pcg32State(val state: Long, val inc: Long)

class Pcg32 private constructor(private var state: ULong, private val inc: ULong) {

    fun snapshot(): Pcg32State = Pcg32State(state.toLong(), inc.toLong())

    fun nextUInt(): UInt {
        val old = state
        state = old * MULTIPLIER + inc
        val xorshifted = (((old shr 18) xor old) shr 27).toUInt()
        val rot = (old shr 59).toInt()
        return (xorshifted shr rot) or (xorshifted shl ((-rot) and 31))
    }

    /** Uniform in [0, bound) without modulo bias (rejection sampling). */
    fun nextBelow(bound: Int): Int {
        require(bound > 0) { "bound must be positive, was $bound" }
        val b = bound.toUInt()
        val threshold = (0u - b) % b
        while (true) {
            val r = nextUInt()
            if (r >= threshold) return (r % b).toInt()
        }
    }

    fun nextIn(range: IntRange): Int {
        require(!range.isEmpty()) { "empty range $range" }
        return range.first + nextBelow(range.last - range.first + 1)
    }

    companion object {
        private const val MULTIPLIER: ULong = 6364136223846793005uL

        /** Reference seeding sequence (pcg32_srandom from the PCG paper). */
        fun seeded(seed: Long, sequence: Long): Pcg32 {
            val rng = Pcg32(0uL, (sequence.toULong() shl 1) or 1uL)
            rng.nextUInt()
            rng.state += seed.toULong()
            rng.nextUInt()
            return rng
        }

        fun fromState(s: Pcg32State): Pcg32 = Pcg32(s.state.toULong(), s.inc.toULong())
    }
}
