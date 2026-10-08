# SwiftUI selection and modal drafts

Keep committed selection in the caller and temporary modal edits in the view
that presents them, so canceling a native picker cannot accidentally save a draft.

## Origin and evidence

Added 2026-10-08 for the second UI component batch. The implementation builds
with Swift 6.2.3/Xcode 26.2, targets iOS 17 and macOS 14, and uses SwiftUI's native
Toggle, Picker, Slider, DatePicker, Menu, sheet and alert. Package compilation
also exercises the macOS declarations. App walkthroughs record observed iPhone
simulator interactions separately from build and existing app-test evidence.

Primary references: Apple's [Toggle](https://developer.apple.com/documentation/swiftui/toggle),
[DatePicker](https://developer.apple.com/documentation/swiftui/datepicker),
[State](https://developer.apple.com/documentation/swiftui/state), and
[modal presentations](https://developer.apple.com/documentation/swiftui/modal-presentations).

## What and why

Binding gives a component access to caller-owned selection. State is useful for
ephemeral expansion or the date draft that exists only while a sheet is open.
DateField copies selection into draft before presenting, binds DatePicker to
draft, and assigns the caller binding only in the confirmation action. A new
opening overwrites the old draft, so cancel followed by reopen starts from the
current committed value. Interactive sheet dismissal cannot execute confirmation.

```swift
// Excerpt: DateField's opening and confirmation boundaries, simplified.
ActionButton("Choose date") { draft = selection; presented = true }
ActionButton("Use date") { selection = draft; presented = false }
```

View identity controls State lifetime. The gallery stores its committed control
values above the family switch, so changing families does not recreate them.
The date component's temporary draft can disappear with its view. This is a
deliberate distinction; neither State nor Binding promises durable storage.

## Native differences and gotchas

SwiftUI on iOS does not provide the same checkbox/radio primitives as Compose.
Our rows are native buttons with a passive SF Symbol, selected trait, and
caller-provided state description; mixed remains explicit accessible value copy.
Do not put another action inside the full-row target. Radio/select options must
be unique and include the current value. Swift's Slider step is an interval,
so ValueSlider converts intermediate-stop count using range / (steps + 1).

Date is an instant. The picker and display interpret it using the environment's
calendar, time zone and locale; the component's FormatStyle reads all three.
A domain calendar date needs an intentional conversion at a service boundary.
This component supplies date-only selection, not time input or range constraints.

SheetPanel is also an independently usable body. Use a caller-owned sheet for
custom detents or presentation policy and provide scrolling for large content.
The convenience sheetPanel modifier and confirmationPrompt keep presentation
flags with the caller. Dismissal is not successful submission. Native modal
styling/gesture behavior and accessibility still require device-level inspection.

## Actual use and next questions

Controls uses toggles, aggregate checkboxes, radio/menu choice, a stepped slider
and date drafts. Overlays uses a sheet, menu and explicit reset confirmation.
Read [the UI walkthrough](../modules/packages/FoundryUI/README.md#selection-controls-and-native-overlays)
and [the catalog walkthrough](../modules/apps/FoundryCatalog/README.md#controls-and-overlays-gallery).
Compare [shared ownership](../../../../notes/patterns/component-slots-and-caller-owned-state.md#committed-values-and-transient-interaction--2026-10-08).
Next: which domain needs a date range, time selection, or a draft coordinated
across several fields rather than one picker?
