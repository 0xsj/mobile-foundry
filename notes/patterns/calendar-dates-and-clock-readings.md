# Calendar dates, clock readings and scheduling drafts

Claim: reusable scheduling controls can edit calendar labels and clock readings
without choosing the feature's rules for instants, availability or reservation.

Origin/evidence: the Scheduling batch, source inspection and native checks on
2026-10-09. The fixed October 5–11 UTC fixture is a UI example, not a production
calendar model. See the [behavior contract](../../contracts/behavior/ui-components.md#dates-and-agendas),
[Swift walkthrough](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#scheduling-gallery)
and [Kotlin walkthrough](../../frontend/kotlin/notes/modules/project/app/README.md#scheduling-gallery)
for actual implementation and verification.

## What and why

| Value | Meaning in this slice | Owner |
| --- | --- | --- |
| Calendar date | A named day under a calendar/zone policy | Feature; native picker encoding is adapted by UI |
| ClockTime | An hour and minute, without a date or zone | Feature value, native modal draft in UI |
| DayOption ID | Identity of one supplied choice | Feature-generated list and selection |
| Applied session | One accepted copy of a chosen day and time | App fixture; a real command would use a service seam |

The native frameworks expose different picker state. SwiftUI's DatePicker binds
a Date even for time-only input; Material TimeInput exposes hour/minute state.
ClockTime makes the common meaning explicit. Swift uses a fixed UTC reference
solely to satisfy the native API. This avoids tying an arbitrary reading to
today's local offset or daylight-saving transition. It still does not determine
the time of a real appointment.

## Example

Start with Monday selected and a 09:30 draft. Use time can change the feature
draft to 10:45; the agenda's already applied 09:30 session remains until Apply.
Cancel closes only the picker draft. Reverse the available dates and Apply is
disabled, while the accepted session stays visible. Bounds can make the selected
day unavailable without silently moving selection to another day.

## Gotchas and limits

- UI validation is not a booking guarantee. Recheck availability/revision at the
  command boundary when another actor can change it.
- A date plus 02:30 can be nonexistent or repeated in a named zone. A real
  feature needs an explicit resolution policy before producing an instant.
- Adding 86,400 seconds is safe only for this fixed UTC fixture. Local calendar
  day generation should use calendar arithmetic rather than elapsed seconds.
- DateRangeField commits its two endpoints independently. It is not an atomic
  range dialog, and does not infer an inclusive or exclusive end.
- Supplied labels include the full date and unavailable copy; color and a short
  weekday alone cannot explain a day choice.
- Picker drafts/presentation are ephemeral. Restoring feature values should not
  replay a commit or reopen an unfinished modal.

## Actual use and related reading

Scheduling keeps its values above gallery families/routes. DayStrip wraps a
small supplied list; AgendaRow keeps readable copy separate from independent
status/actions. No recurring events, device calendar, alarms, durable booking
or remote availability is implemented.
Compare [component ownership](component-slots-and-caller-owned-state.md) and
[command admission](forms-and-mutation-ownership.md). Then read the
[Swift substrate](../../frontend/swift/notes/substrate/swiftui-time-and-date-drafts.md)
and [Compose substrate](../../frontend/kotlin/notes/substrate/compose-time-and-date-drafts.md).

Next questions: should the first real feature store a local date/reading/zone or
an instant; what happens when its calendar/zone changes; and which stale
availability revisions should a scheduling command reject?
