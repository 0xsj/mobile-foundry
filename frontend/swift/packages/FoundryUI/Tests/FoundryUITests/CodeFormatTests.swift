import FoundryUI
import Testing

@Test func codeEditsAdmitCanonicalDigitsWithoutSilentlyTruncating() {
    let format = CodeFormat()
    #expect(format.admit("12-3 4\t5\r\n6") == "123456")
    #expect(format.admit("  -\n") == "")
    #expect(format.admit("000123") == "000123")
    #expect(format.isValid("") && format.isValid("123") && !format.isComplete("123"))
    #expect(format.isComplete("000123") && !format.isValid("123-456"))
    #expect(CodeFormat(length: 4).admit("12-34") == "1234")
    #expect(CodeFormat(length: 12).isComplete("000000000001"))
}
@Test func invalidCodePastesRejectTheWholeEdit() {
    let format = CodeFormat()
    for invalid in ["1234567", "12a456", "１２３４５６", "١٢٣٤٥٦", "12\u{200B}3456", "12\u{00A0}3456", "12_3456", "12345🙂"] {
        #expect(format.admit(invalid) == nil && !format.isValid(invalid))
    }
}
