package com.maysk.music.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TrimRangeTest {
    @Test fun rejectsTooShortFragment() {
        assertNotNull(trimRangeError(0, 400, 10_000))
        assertNotNull(trimRangeError(0, 500, 10_000))
    }

    @Test fun acceptsMinimalValidFragment() {
        assertNull(trimRangeError(0, 501, 10_000))
    }

    @Test fun rejectsNegativeStart() {
        assertNotNull(trimRangeError(-1, 1_000, 10_000))
    }

    @Test fun rejectsEndFarBeyondDuration() {
        assertNotNull(trimRangeError(0, 12_001, 10_000))
    }

    @Test fun allowsSmallOvershootOfDuration() {
        assertNull(trimRangeError(0, 12_000, 10_000))
    }
}
