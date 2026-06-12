package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.rng.Pcg32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Pcg32Test {

    @Test
    fun `matches reference output for seed 42 sequence 54`() {
        // Golden vectors computed with an independent Python implementation of
        // PCG-XSH-RR; first value matches the official pcg32 demo output.
        val rng = Pcg32.seeded(42, 54)
        val expected = listOf(0xa15c02b7u, 0x7b47f409u, 0xba1d3330u, 0x83d2f293u, 0xbfa4784bu, 0xcbed606eu)
        assertContentEquals(expected, List(6) { rng.nextUInt() })
    }

    @Test
    fun `matches reference output for seed 123456789 sequence 1`() {
        val rng = Pcg32.seeded(123456789, 1)
        val expected = listOf(0xb4a33584u, 0x6c7617d9u, 0x2736c6ceu, 0x36f57889u, 0xffe8b0a6u, 0x02b716ecu)
        assertContentEquals(expected, List(6) { rng.nextUInt() })
    }

    @Test
    fun `snapshot and restore continue the identical stream`() {
        val rng = Pcg32.seeded(7, 3)
        repeat(10) { rng.nextUInt() }
        val snapshot = rng.snapshot()
        val continued = List(5) { rng.nextUInt() }
        val restored = Pcg32.fromState(snapshot)
        assertContentEquals(continued, List(5) { restored.nextUInt() })
    }

    @Test
    fun `nextIn stays in range and hits every face roughly uniformly`() {
        val rng = Pcg32.seeded(2026, 11)
        val counts = IntArray(7)
        repeat(6_000) {
            val roll = rng.nextIn(1..6)
            assertTrue(roll in 1..6, "roll $roll out of range")
            counts[roll]++
        }
        for (face in 1..6) {
            assertTrue(counts[face] in 700..1300, "face $face count ${counts[face]} outside 700..1300")
        }
    }

    @Test
    fun `single-value range is fixed point`() {
        val rng = Pcg32.seeded(1, 1)
        assertEquals(5, rng.nextIn(5..5))
    }

    @Test
    fun `invalid bounds are rejected`() {
        val rng = Pcg32.seeded(1, 1)
        assertFailsWith<IllegalArgumentException> { rng.nextBelow(0) }
        assertFailsWith<IllegalArgumentException> { rng.nextBelow(-3) }
        assertFailsWith<IllegalArgumentException> { rng.nextIn(5..4) }
    }
}
