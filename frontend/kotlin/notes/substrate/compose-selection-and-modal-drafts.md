# Compose selection and modal drafts

Use one native semantic target for a choice and keep temporary modal edits
separate from the caller's committed value.

## Origin and evidence

Added 2026-10-08 for the second UI component batch. Source uses Kotlin 2.3.20,
Compose BOM 2026.03.01, AGP 9.0.1 and compile SDK 36 with minimum API 24. Native
DatePicker/ModalBottomSheet remain ExperimentalMaterial3Api in this dependency
set. Builds and focused emulator interaction outcomes are recorded in the app
and UI module walkthroughs rather than inferred from API documentation.

Primary references: [state hoisting](https://developer.android.com/develop/ui/compose/state-hoisting),
[checkboxes](https://developer.android.com/develop/ui/compose/components/checkbox),
[date pickers](https://developer.android.com/develop/ui/compose/components/datepickers), and
[rememberDatePickerState](https://developer.android.com/reference/kotlin/androidx/compose/material3/rememberDatePickerState).

## What and why

ToggleField, Checkbox and RadioGroup give the whole row a toggleable,
triStateToggleable or selectable modifier with the native role. The visual
Switch/TriStateCheckbox/RadioButton receives a null callback; it is passive
inside that target. This avoids two competing activation nodes. Checkbox maps
CheckState to ToggleableState but leaves the next state to the caller. Radio
rows belong to a selectableGroup, and labels can wrap without shrinking targets.

DateField remembers whether it is open. Inside that conditional composition,
rememberDatePickerState owns the temporary selection. Dismissing removes that
state; reopening creates a draft from the latest caller value. Only the enabled
Confirm button emits a nonnull value. rememberSaveable belongs to the gallery's
committed controls above its family switch, so preview themes and restoration
keep their values. This is screen saved state, not durable storage or syncing.

```kotlin
// Excerpt: DateField's temporary picker state and explicit commit boundary.
val draft = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
TextButton(enabled = draft.selectedDateMillis != null, onClick = {
    draft.selectedDateMillis?.let(onDateChange)
    presented = false
}) { Text(confirmLabel) }
```

## Date encoding and gotchas

The native millisecond picker API represents calendar dates at midnight UTC.
DateField formats committed values with a UTC DateFormat; formatting them in
America/Los_Angeles would otherwise show the previous calendar day. The Long
overload supports the project's API 24 minimum, while the LocalDate overload
requires API 26. Do not silently treat these values as local appointment times.
Nullable input allows no selection; confirmation stays disabled until a date is
chosen. Range validation and date/time appointments are outside this API.

Menu expansion is temporary UI state. Closing a menu or dialog never confirms
an action; disabled entries have native disabled semantics. A destructive menu
callback can open a caller-owned confirmation. SheetPanel uses ModalBottomSheet
with caller presence/dismissal and a native content slot. Provide scrolling for
large slot content. No component owns a coroutine, service or operation retry.

## Actual use and next questions

Controls demonstrates aggregate selection and bounded numeric/date input;
Overlays demonstrates native sheets, disabled menu entries and confirmed reset.
Read [the UI walkthrough](../modules/project/core/ui/README.md#selection-controls-and-native-overlays)
and [the catalog walkthrough](../modules/project/app/README.md#controls-and-overlays-gallery), then
compare [shared ownership](../../../../notes/patterns/component-slots-and-caller-owned-state.md#committed-values-and-transient-interaction--2026-10-08).
Next: when should a multi-field draft move from a component into a feature state
owner, and which date-only encoding should its service contract accept?
