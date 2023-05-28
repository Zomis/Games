package zomis.minesweeper.analyze

import net.zomis.minesweeper.analyze.FieldGroup
import net.zomis.minesweeper.analyze.FieldGroupSplit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FieldGroupSplitTest {
    private fun <T> split(a: FieldGroup<T>, b: FieldGroup<T>): FieldGroupSplit<T>? {
        return FieldGroupSplit.split(a, b)
    }

    @Test
    fun allFieldsCommon() {
        val a = FieldGroup(mutableListOf("a", "b", "c"))
        val b = FieldGroup(mutableListOf("a", "b", "c"))
        val split = split(a, b)
        assertTrue(split!!.onlyA.isEmpty)
        assertFalse(split!!.both.isEmpty)
        assertSame(a, split!!.both)
        assertTrue(split!!.onlyB.isEmpty)
        assertFalse(split!!.splitPerformed())
    }

    @Test
    fun sameFields() {
        val a = FieldGroup(mutableListOf("a", "a", "b", "c"))
        val b = FieldGroup(mutableListOf("a", "a", "d", "e"))
        val split = split(a, b)
        assertEquals(FieldGroup(mutableListOf("b", "c")), split!!.onlyA)
        assertEquals(FieldGroup(mutableListOf("a", "a")), split!!.both)
        assertEquals(FieldGroup(mutableListOf("d", "e")), split!!.onlyB)
    }

    @Test
    fun split() {
        val a = FieldGroup(mutableListOf("a", "b", "c"))
        val b = FieldGroup(mutableListOf("b", "c", "d"))
        val split = split(a, b)
        assertEquals(FieldGroup(mutableListOf("a")), split!!.onlyA)
        assertEquals(FieldGroup(mutableListOf("b", "c")), split!!.both)
        assertEquals(FieldGroup(mutableListOf("d")), split!!.onlyB)
        assertTrue(split!!.splitPerformed())
    }

    @Test
    fun disjointGroups() {
        val a = FieldGroup(mutableListOf("a", "b", "c"))
        val b = FieldGroup(mutableListOf("d", "e", "f"))
        val split = split(a, b)
        assertNull(split)
    }
}