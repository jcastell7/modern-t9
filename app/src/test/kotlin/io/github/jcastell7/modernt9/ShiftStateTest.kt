package io.github.jcastell7.modernt9

import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftStateTest {

    @Test fun `shift cycles off - once - lock - off`() {
        assertEquals(ShiftState.ONCE, ShiftState.OFF.next())
        assertEquals(ShiftState.LOCK, ShiftState.ONCE.next())
        assertEquals(ShiftState.OFF, ShiftState.LOCK.next())
    }

    @Test fun `three presses return to the start`() {
        var s = ShiftState.OFF
        repeat(3) { s = s.next() }
        assertEquals(ShiftState.OFF, s)
    }
}
