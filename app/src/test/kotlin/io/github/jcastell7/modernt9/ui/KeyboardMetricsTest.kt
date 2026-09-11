package io.github.jcastell7.modernt9.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardMetricsTest {

    @Test fun `default scale is the reference height`() {
        val m = KeyboardMetrics()
        assertEquals(1f, m.scale)
        assertEquals(T9Theme.baseRowHeight, m.rowHeight)
    }

    @Test fun `scaling changes row height proportionally`() {
        assertEquals(T9Theme.baseRowHeight * 1.25f, KeyboardMetrics(scale = 1.25f).rowHeight)
        assertEquals(T9Theme.baseRowHeight * 0.8f, KeyboardMetrics(scale = 0.8f).rowHeight)
    }

    @Test fun `the function row is shorter than a key row`() {
        // It carries labels, not letters; at full row height it dominated the keyboard.
        listOf(0.72f, 1f, 1.45f).forEach { scale ->
            val m = KeyboardMetrics(scale = scale)
            assertTrue("at $scale", m.bottomRowHeight < m.rowHeight)
            assertTrue("still tappable at $scale", m.bottomRowHeight > m.rowHeight * 0.6f)
        }
    }

    @Test fun `the scale range is sane`() {
        assertTrue(KeyboardMetrics.MIN_SCALE > 0.5f)
        assertTrue(KeyboardMetrics.MAX_SCALE < 2f)
        assertTrue(KeyboardMetrics.MIN_SCALE < KeyboardMetrics.MAX_SCALE)
    }

    @Test fun `clamping to the range keeps the keyboard usable`() {
        val tooSmall = 0.1f.coerceIn(KeyboardMetrics.MIN_SCALE, KeyboardMetrics.MAX_SCALE)
        val tooBig = 9f.coerceIn(KeyboardMetrics.MIN_SCALE, KeyboardMetrics.MAX_SCALE)
        assertEquals(KeyboardMetrics.MIN_SCALE, tooSmall)
        assertEquals(KeyboardMetrics.MAX_SCALE, tooBig)
    }

    @Test fun `content panes keep the same total height as the key pane`() {
        listOf(0.72f, 1f, 1.45f).forEach { scale ->
            val m = KeyboardMetrics(scale = scale)
            // A content pane is body + toolbar; it must equal the key pane, so switching
            // to emoji or clipboard never moves the keyboard. Compared with a tolerance
            // because the two sides sum their float terms in a different order.
            assertEquals(
                "at $scale",
                m.paneHeight.value,
                (m.contentHeight + m.toolbarHeight).value,
                0.01f,
            )
        }
    }

    @Test fun `the toolbar is shorter than a row of keys`() {
        val m = KeyboardMetrics()
        assertTrue(m.toolbarHeight < m.bottomRowHeight)
        assertTrue(m.toolbarHeight < m.rowHeight)
    }

    @Test fun `the toolbar scales with the keyboard`() {
        assertTrue(KeyboardMetrics(scale = 1.4f).toolbarHeight > KeyboardMetrics(scale = 0.8f).toolbarHeight)
    }

    @Test fun `content panes give most of their height to the body`() {
        val m = KeyboardMetrics()
        assertTrue("body should dominate", m.contentHeight > m.toolbarHeight * 3)
    }

    @Test fun `bottom inset adds clearance without changing rows`() {
        val m = KeyboardMetrics(scale = 1f, bottomInset = 12.dp)
        assertEquals(T9Theme.baseRowHeight, m.rowHeight)
        assertEquals(12.dp, m.bottomInset)
    }
}
