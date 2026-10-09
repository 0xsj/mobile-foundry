@testable import FoundryUI
import Foundation
import Testing

@Test func nativeTimeBridgePreservesMidnightNoonAndMinuteEdges() {
    for value in [ClockTime(hour: 0, minute: 0), ClockTime(hour: 0, minute: 59), ClockTime(hour: 12, minute: 0), ClockTime(hour: 23, minute: 59)] {
        #expect(ClockTime(pickerDate: value.pickerDate) == value)
        // Adding arbitrary dates does not change the admitted wall-clock reading.
        #expect(ClockTime(pickerDate: value.pickerDate.addingTimeInterval(86_400 * 42)) == value)
    }
}
