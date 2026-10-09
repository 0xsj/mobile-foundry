# SwiftUI time and date drafts

Claim: a fixed UTC Date bridge can host a native time picker while the public
component keeps only an hour/minute reading.

Origin/evidence, 2026-10-09: Swift 6.2.3, Xcode 26.2, macOS 26.3, iOS 26.2
simulator; package minimum iOS 17/macOS 14. Apple documents
[DatePicker](https://developer.apple.com/documentation/swiftui/datepicker) and
[hourAndMinute](https://developer.apple.com/documentation/swiftui/datepickercomponents/hourandminute).
Framework API documentation explains the binding/components; the chosen bridge,
cancel policy and theme inheritance are repository decisions established by
source and checks, not framework promises.

## Mechanism and example

ClockTime uses immutable validated Int properties and Equatable/Sendable value
semantics. Opening TimeField copies it into local State; confirmation converts
the draft back to hour/minute with the same UTC Gregorian calendar. The native
iOS wheel is inside one sheet, so the user can adjust it directly. macOS retains
the native default style. Locale remains native; displayed value copy is supplied
by the feature.

```swift
// Excerpt — TimeField's bridge, not a backend timestamp.
var pickerDate: Date {
    Date(timeIntervalSinceReferenceDate: Double(hour * 3600 + minute * 60))
}
```

The reference Date is only an API adapter. It is never the selected appointment
date. DateRangeField instead composes the existing DateField and retains its
calendar/time-zone environment. Each endpoint owns its own temporary modal
draft. Date/time sheets inherit the scoped appearance/material, and disabling
their trigger dismisses the draft and guards confirmation.

## Evidence, gotchas and actual use

[ClockTime checks](../../packages/FoundryUI/Tests/FoundryUITests/ClockTimeTests.swift)
exercise midnight/noon/minute edges and arbitrary UTC date offsets. The
[native consumer checks](../../apps/FoundryCatalog/Tests/SchedulingComponentTests.swift)
exercise command guards and actual hosted 240-point layouts at ordinary and
accessibility text sizes. Building the iOS consumer checks the wheel-specific
branch; a macOS package check alone cannot do that.
Those checks do not establish real-zone DST resolution, VoiceOver traversal,
calendar permissions or notification behavior. Material/style decisions do not
replace native picker semantics.

Read the [UI walkthrough](../modules/packages/FoundryUI/README.md#dates-and-agendas),
[gallery](../modules/apps/FoundryCatalog/README.md#scheduling-gallery) and
[shared date/reading ownership](../../../../notes/patterns/calendar-dates-and-clock-readings.md).
Next: define calendar/zone resolution before adapting these readings to a real
session command, and observe both 12-hour and 24-hour locales on devices.
