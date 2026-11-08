package com.nickfouf.classwave.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoRoomScreenTest {

    @Test
    fun initialsUseFirstAndLastName() {
        assertEquals("ΔΣ", getInitialsFromName("ΔΗΜΗΤΡΙΟΣ ΣΤΕΦΑΝΟΠΟΥΛΟΣ"))
    }

    @Test
    fun initialsIgnoreTrailingDisplayLabel() {
        assertEquals("ΔΣ", getInitialsFromName("ΔΗΜΗΤΡΙΟΣ ΣΤΕΦΑΝΟΠΟΥΛΟΣ (Εγώ)"))
    }

    @Test
    fun initialsHandleSingleAndBlankNames() {
        assertEquals("Ν", getInitialsFromName("  Νικόλαος  "))
        assertEquals("", getInitialsFromName("   "))
    }
}
