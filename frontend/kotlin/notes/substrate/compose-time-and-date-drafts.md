# Compose time and date drafts

Claim: disposing native picker state on modal close keeps editing separate from
the caller's committed hour/minute and date values.

Origin/evidence, 2026-10-09: Kotlin 2.3.20, AGP 9.0.1, Compose BOM 2026.03.01,
Material3 1.4.0, API 36.1 test emulator; project minSdk 24. Google's
[time picker guide](https://developer.android.com/develop/ui/compose/components/time-pickers)
documents TimeInput/TimePickerState and reading hour/minute on confirmation.
The repository uses its installed experimental API with an explicit opt-in;
latest online examples are not a promise that a later API is installed.

## Mechanism and example

ClockTime is a validated immutable data class. TimeField remembers only its
presentation flag. rememberTimePickerState is created inside the presented
branch from the caller's current reading and system 12/24-hour preference.
Closing disposes that branch; reopening starts a new draft. The dialog has
vertical and horizontal scrolling for native input that exceeds compact bounds.

```kotlin
// Excerpt — the supplied state is read only on explicit confirmation.
if (presented && enabled) {
    val draft = rememberTimePickerState(selection.hour, selection.minute,
        DateFormat.is24HourFormat(LocalContext.current))
    // AlertDialog's confirm callback reads draft.hour and draft.minute.
}
```

Material owns editing details, including its last accepted hour/minute. The
wrapper does not promise arbitrary invalid-text validation. Its enabled branch
and confirmation guard prevent a disabled draft from committing. Existing
DateField now follows the same rule. DateRangeField composes two DateFields;
their UTC-midnight milliseconds name days rather than appointments. No java.time
desugaring dependency is needed for this small reading value.

## Evidence, gotchas and actual use

[Component checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/SchedulingComponentTest.kt)
edit real native hour/minute fields, cancel/reopen/confirm, disable an open dialog,
and commit independent date endpoints while discarding a disabled date draft,
and exercise 240-dp day/agenda controls at font scale 2. The
[gallery checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/SchedulingCatalogTest.kt)
exercise unavailable/invalid choices and saved primitive state across routes and
themes. Picker presentation uses remember, deliberately separate from saved
feature values. Tests do not claim all locales, invalid edit permutations,
physical-device keyboard behavior, complete TalkBack or booking guarantees.

Read the [UI walkthrough](../modules/project/core/ui/README.md#dates-and-agendas),
[gallery](../modules/project/app/README.md#scheduling-gallery) and
[shared ownership](../../../../notes/patterns/calendar-dates-and-clock-readings.md).
Next: choose an explicit calendar/zone and stale-availability policy for an
actual scheduling service; then test DST and locale behavior at that boundary.
