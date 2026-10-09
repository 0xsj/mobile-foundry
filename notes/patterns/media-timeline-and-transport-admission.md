# Media timeline and transport admission

Claim: a reusable playback surface presents supplied values and commands, while
the feature admits timeline changes and a native engine owns actual playback.

Origin/evidence, 2026-10-09: the twenty-second UI batch adds PlaybackControls and
NowPlayingCard over a manually advanced local timeline. Native checks cover
transport eligibility, seeking, track identity, repeat/replay boundaries and
retained values. The fixture plays no audio and has no automatic clock.

## What and why

Identity/artwork is passive, while timeline, transport and metadata actions stay
independent. A disabled previous button need not disable play or a favorite.
The feature supplies labels, current state and callbacks; the reusable controls
do not interpret loading, choose a queue policy or create a media session.

Store positions by stable track ID rather than visible queue index. Track changes
pause and recover that track's position. Filtering/unavailable tracks and empty
presentation do not erase remembered values. An unavailable row is visibly and
semantically disabled, and its selection command rejects direct admission too.

Seeking and time advancement are different commands. A seek accepts finite input,
clamps it and pauses at the end. A manual step requires playing, positive finite
elapsed time and a finite target. Speed scales that delta. Without repeat, the
end clamps and pauses; repeat wraps the target by duration. Starting again from
a paused end resets to zero. These are explicit preview policies, not universal
player semantics.

## Example and gotchas

Play Coastline, seek to 45 seconds, then switch to Orbit. Play and advance Orbit,
then return to Coastline: its position remains 45 and transport is paused. Set
Buffering: timeline/queue/transport admission stops, but favorite remains available.
Failed → Retry restores Ready without resuming or clearing position/speed/repeat.
Reset clears positions and restores the initial transport choices while retaining
favorites and diagnostic counts.

The existing slider invokes an immediate local callback. A real decoder may need
a drag draft, seek-on-commit, cancellation and sequencing of asynchronous results.
Do not send every slider update directly to an expensive engine without deciding
that policy. Engine time/duration, requested position and an acknowledged seek
may differ; stale completion must not overwrite a newer track or request.

A saved playing flag is UI data, not proof that a resource resumed. A real adapter
must reconcile engine state, lifecycle, interruptions and commands. Media loading,
buffering, failures, background audio, routes and OS transport remain separate
platform capabilities. Local Retry simply changes a fixture state.

## Actual use and next questions

Read [Swift Playback flow](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#playback-gallery),
[Kotlin Playback flow](../../frontend/kotlin/notes/modules/project/app/README.md#playback-gallery),
[usage](../../docs/blueprints/ui-components.md#playback-and-timeline) and
[behavior](../../contracts/behavior/ui-components.md#playback-and-timeline).
Related: [media identity](media-selection-and-passive-artwork.md),
[transport/service ownership](transport-service-and-screen.md) and
[renderer ownership](renderer-frame-ownership.md).
Next: define requested versus observed transport state and seek completion scope;
connect one real native engine before adding background/OS media-session behavior.
