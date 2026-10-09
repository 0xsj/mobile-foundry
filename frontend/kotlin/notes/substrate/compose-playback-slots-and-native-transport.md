# Compose playback slots and native transport

Claim: separate native transport targets and a passive identity group preserve
independent semantics while playback values remain in the feature owner.

Origin/evidence, 2026-10-09: Kotlin 2.3.20, AGP 9.0.1, Compose BOM 2026.03.01,
Material3 1.4.0, JDK 17, SDK 36/min 24, API 36 emulator. Android's
[drawing documentation](https://developer.android.com/develop/ui/compose/graphics/draw/overview)
explains Canvas/DrawScope and pixel coordinates. Here simple glyphs use fractions
of a fixed 24-dp canvas; the parent IconAction supplies native target semantics.

## Mechanism and example

PlaybackControls composes three existing IconAction values inside WrapLayout.
Each button has its own admission, label and callback. The middle action uses
stateDescription for current state; isPlaying only selects play/pause drawing.
Canvas paths use LocalContentColor so the primary action retains theme contrast.
Temporal glyph direction is independent of logical RTL layout placement.

```kotlin
// Conceptual: feature commands own transport eligibility and effects.
PlaybackControls(playing, "Previous", if (playing) "Pause" else "Play",
    "Next", stateCopy, previous, toggle, next,
    previousEnabled = canPrevious, toggleEnabled = canToggle, nextEnabled = canNext)
```

NowPlayingCard clears semantics only for passive artwork/title/detail and supplies
complete heading narration there. Timeline, controls and actions are sibling
ColumnScope slots. At font scale at least 1.5, artwork moves above growing identity.
Artwork size requires a finite positive Dp value and must fit the caller's bounds.
Drawing does not replace labels or independent native button roles.

Selected ID, per-track Double positions, favorites, playing/speed/repeat/scenario,
flags and counts are owned in rememberSaveable above route returns. These bounded
nonsecret maps/lists restore through the saved-state registry. Earlier owners stay
composed; the playback route cannot drop a previous cart owner from composition.
This saved state is neither durable media storage nor engine restoration.

## Evidence, gotchas and actual use

[Component checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/PlaybackComponentTest.kt)
exercise independent native roles/disabled state, passive artwork and 240-dp
font-scale-two RTL growth/target bounds.
[Gallery checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/PlaybackCatalogTest.kt)
exercise the native slider's SetProgress action, transport, menus, repeat/favorite,
retry/empty/disabled states, saved-state recreation and prior cart retention.
All four focused checks, both builds and seven Swift/six Kotlin UI package checks
pass. They do not drive a physical drag, full TalkBack, an audio engine or device
playback performance.

Keep clearAndSetSemantics away from the whole card or it will hide slot controls.
Favorite eligibility is independent of buffering; do not blanket-disable the
composition. Slider updates commit immediate local positions here. A real engine
adapter must decide commit/sequencing policy and reconcile observed playback.

Read [UI walkthrough](../modules/project/core/ui/README.md#playback-and-timeline),
[consumer](../modules/project/app/README.md#playback-gallery) and
[shared timeline pattern](../../../../notes/patterns/media-timeline-and-transport-admission.md).
Next: engine-backed seek completion, native drag/TalkBack observations and OS
media sessions outside these reusable UI slots.
