package com.maysk.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamesTest {
    @Test fun replacesForbiddenCharacters() {
        assertEquals("a_b_c", safeFileName("a/b:c", "x"))
    }

    @Test fun replacesControlCharacters() {
        assertEquals("a_b", safeFileName("a\u0000b", "x"))
    }

    @Test fun trimsDotsAndSpacesAtEdges() {
        assertEquals("hi", safeFileName("  ..hi.. ", "x"))
    }

    @Test fun usesFallbackForEmptyInput() {
        assertEquals("x", safeFileName(null, "x"))
        assertEquals("x", safeFileName("   ", "x"))
        assertEquals("x", safeFileName("...", "x"))
    }

    @Test fun limitsLength() {
        assertEquals(60, safeFileName("a".repeat(100), "x").length)
        assertEquals(10, safeFileName("a".repeat(100), "x", maxLength = 10).length)
    }

    @Test fun keepsCyrillic() {
        assertEquals("Привет мир", safeFileName("Привет мир", "x"))
    }
}
