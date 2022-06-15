package net.zomis.games

import net.zomis.Best
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BestTest {

    private fun createBest(): Best<String> {
        val best = Best<String> { it.length.toDouble() }
        best.next("hi")
        best.next("hello")
        best.next("")
        best.next("world")
        return best
    }

    @Test
    fun empty() {
        val emptyBest = Best<Double> { it }
        assert(emptyBest.getBest().isEmpty())
        assertThrows<NoSuchElementException> { emptyBest.getBest().random() }
        assertThrows<NoSuchElementException> { emptyBest.firstBest() }
        assertFalse(emptyBest.isBest(4.2))
    }

    @Test
    fun random() {
        val randomBest = createBest().getBest().random()
        assertTrue(randomBest == "hello" || randomBest == "world")
    }

    @Test
    fun bestList() {
        val allBest = createBest().getBest()
        assertEquals(listOf("hello", "world"), allBest)
    }

    @Test
    fun firstBest() {
        assertEquals("hello", createBest().firstBest())
    }

    @Test
    fun isBest() {
        val best = createBest()
        assertFalse(best.isBest(""))
        assertFalse(best.isBest("hi"))
        assertFalse(best.isBest("something random that was never added"))
        assertTrue(best.isBest("hello"))
        assertTrue(best.isBest("world"))
    }

}