# SwiftUI playback slots and native transport

Claim: native action buttons and a passive identity group keep a now-playing
composition accessible without giving its view engine or clock ownership.

Origin/evidence, 2026-10-09: Swift 6.2.3, Xcode 26.2, macOS 26.3, iOS 26.2
simulator; UI package minimum iOS 17/macOS 14. Apple's
[accessibilityValue documentation](https://developer.apple.com/documentation/swiftui/view/accessibilityvalue(_:)-8esl7)
describes adding value narration distinct from a view's label. Here the middle
native button receives its current state separately from its action label.

## Mechanism and example

PlaybackControls builds three existing IconAction values. ViewThatFits tries an
HStack then a VStack when horizontal space is insufficient. Each action has its
own enabled value; isPlaying chooses only a supplied SF Symbol. The toggle keeps
its native button role and uses accessibilityValue rather than a selected trait.
State and action copy must be localized by the consumer.

```swift
// Conceptual: feature commands own transport eligibility and effects.
PlaybackControls(isPlaying: playing, previousLabel: "Previous",
    toggleLabel: playing ? "Pause" : "Play", nextLabel: "Next", stateLabel: stateCopy,
    previousEnabled: canPrevious, toggleEnabled: canToggle, nextEnabled: canNext,
    onPrevious: previous, onToggle: toggle, onNext: next)
```

NowPlayingCard stores four ViewBuilder values. Only its passive artwork/title/
detail group ignores child accessibility and receives full heading narration.
Timeline, controls and actions remain outside that group. Accessibility Dynamic
Type stacks the square cover above growing identity copy. Cover size must be
finite positive and fit the host; the caller supplies passive fitting artwork.

## Evidence, gotchas and actual use

[Hosted/owner checks](../../apps/FoundryCatalog/Tests/PlaybackComponentTests.swift)
exercise transport/timeline admission and 240-point accessibility-text/RTL growth,
independent timeline/action bounds and logical artwork placement. Both consumers
build; 59 iOS app checks, four focused Android Playback checks and seven Swift/
six Kotlin UI package checks pass. Hosted geometry does not drive an iOS slider
gesture or establish full VoiceOver traversal, real playback or device performance.

Do not ignore child accessibility on the whole card: the slider and transport
would disappear. Artwork must add no unique meaning beyond supplied narration.
The existing ValueSlider binding immediately invokes local seek admission; a real
player adapter decides drag/commit policy and reconciles engine acknowledgments.
Playing values survive route changes, but own no resource here.

Read [UI walkthrough](../modules/packages/FoundryUI/README.md#playback-and-timeline),
[consumer](../modules/apps/FoundryCatalog/README.md#playback-gallery) and
[shared timeline pattern](../../../../notes/patterns/media-timeline-and-transport-admission.md).
Next: native slider/VoiceOver observations and an engine-backed seek lifecycle.
