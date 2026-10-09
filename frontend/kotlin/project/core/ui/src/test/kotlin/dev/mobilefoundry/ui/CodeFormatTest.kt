package dev.mobilefoundry.ui

import dev.mobilefoundry.ui.components.forms.onetimecode.CodeFormat
import org.junit.Assert.*
import org.junit.Test

class CodeFormatTest {
    @Test fun editsAdmitCanonicalDigitsWithoutSilentlyTruncating() {
        val format = CodeFormat()
        assertEquals("123456", format.admit("12-3 4\t5\r\n6"))
        assertEquals("", format.admit("  -\n")); assertEquals("000123", format.admit("000123"))
        assertTrue(format.isValid("")); assertTrue(format.isValid("123")); assertFalse(format.isComplete("123"))
        assertTrue(format.isComplete("000123")); assertFalse(format.isValid("123-456"))
        assertEquals("1234", CodeFormat(4).admit("12-34")); assertTrue(CodeFormat(12).isComplete("000000000001"))
    }
    @Test fun invalidPastesAndUnsupportedLengthsReject() {
        val format = CodeFormat()
        for (invalid in listOf("1234567", "12a456", "１２３４５６", "١٢٣٤٥٦", "12\u200B3456", "12\u00A03456", "12_3456", "12345🙂")) {
            assertNull(format.admit(invalid)); assertFalse(format.isValid(invalid))
        }
        assertThrows(IllegalArgumentException::class.java) { CodeFormat(0) }
        assertThrows(IllegalArgumentException::class.java) { CodeFormat(13) }
    }
}
