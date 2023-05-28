package zomis.minesweeper.analyze

import net.zomis.minesweeper.analyze.factory.CharPoint
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CharPointTest {
    @Test
    fun notEquals() {
        assertFalse(CharPoint(5, 4, ' ').equals("A string"))
        assertNotEquals(CharPoint(5, 4, ' '), CharPoint(5, 4, 'x'))
        assertNotEquals(CharPoint(5, 3, ' '), CharPoint(5, 4, ' '))
        assertNotEquals(CharPoint(3, 4, ' '), CharPoint(5, 4, ' '))
    }

    @Test
    fun equals() {
        assertEquals(CharPoint(5, 4, ' '), CharPoint(5, 4, ' '))
    }
}