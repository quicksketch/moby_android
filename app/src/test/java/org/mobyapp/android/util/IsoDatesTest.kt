package org.mobyapp.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class IsoDatesTest {

    private val denver = ZoneId.of("America/Denver")

    @Test
    fun formatsWithOffsetAndWithoutFractionalSeconds() {
        val date = ZonedDateTime.of(2026, 10, 27, 18, 30, 15, 123_000_000, denver)
        assertEquals("2026-10-27T18:30:15-06:00", IsoDates.format(date))
    }

    @Test
    fun parsesIntoTheGivenZone() {
        val parsed = IsoDates.parse("2026-10-28T00:30:00Z", denver)
        assertEquals(ZonedDateTime.of(2026, 10, 27, 18, 30, 0, 0, denver), parsed)
    }

    @Test
    fun invalidDatesAreNull() {
        assertNull(IsoDates.parse("2026-10-27 18:30"))
        assertEquals("not a date", IsoDates.display("not a date"))
    }
}
