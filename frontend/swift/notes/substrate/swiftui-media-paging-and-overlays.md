# SwiftUI media paging and overlays

Claim: native scroll target identity can drive a supplied selection binding
without making the carousel an owner of media or per-record values.

## Origin and evidence

Added 2026-10-09 for the eighth UI batch. Swift 6.2.3, Xcode/SwiftUI 26.2,
iOS 17 / macOS 14 package minimum. Primary references:
[Beyond scroll views](https://developer.apple.com/videos/play/wwdc2023/10159/),
[scrollPosition](https://developer.apple.com/documentation/swiftui/view/scrollposition(id:anchor:)),
[containerRelativeFrame](https://developer.apple.com/documentation/swiftui/view/containerrelativeframe(_:alignment:))
and [accessibilityHidden](https://developer.apple.com/documentation/swiftui/view/accessibilityhidden(_:)).
Source establishes wrapper structure; module notes distinguish actual hosted
geometry, manual interaction and remaining gesture/hardware coverage.

## What and why

Carousel combines horizontal ScrollView, zero-spacing LazyHStack, native
container-relative page width, a scroll target layout and paging behavior. A
bridge binding accepts the native optional scroll ID while keeping a valid
nonoptional caller selection. Nil/unrecognized updates do not manufacture a
selection. Construction requires nonempty unique IDs and a selected record.
The host handles changes to admitted data and supplies bounded height/width.

```swift
// Excerpt: native scroll ID writes through the caller binding when valid.
.scrollPosition(id: Binding<Item.ID?>(get: { selection }, set: { id in
    if let id, items.contains(where: { $0.id == id }) { selection = id }
}))
```

The app owns programmatic Previous/Next changes and wraps them in the token
animation. Its rating dictionary and favorite set live above the native link
and family switch. Binding(get:set:) projects only the selected record's rating;
page realization has no authority to initialize or discard that value.

IconAction composes ActionButton, hides passive icon semantics and supplies the
button label. RatingField's adaptive native grid retains minimum touch targets;
only the exact rating choice receives selected traits. Native title/value text
can grow independently of fixed decorative star symbols. MediaOverlay hides
only artwork/scrim, while its overlay retains independent controls. MediaTile
combines metadata separately from its actions.

## Gotchas, actual use and limits

Native hosted checks execute programmatic selected-ID changes and measure the
selected page aligned to the actual 320-point viewport, then measure media
metadata/rating copy growth at accessibility3. Manual simulator interactions
exercise explicit paging, rating and favorite retention. CUA's drag attempt did
not change page state; it is not counted as swipe evidence. Android separately
executes a native swipe in its instrumentation check.

A valid supplied selection does not imply a policy for arbitrary record removal.
The fixture never replaces its three-record set. No physical-device, complete
VoiceOver, localization, macOS runtime or image-loading audit is claimed. Next:
which collection mutation should preserve the current record or choose a neighbor?

## Related

- [UI walkthrough](../modules/packages/FoundryUI/README.md#media-browsing-and-actions).
- [Media gallery](../modules/apps/FoundryCatalog/README.md#media-gallery).
- [Generic content builders](../language/swift-generic-query-state-and-content-builders.md).
- [Shared selection and artwork ownership](../../../../notes/patterns/media-selection-and-passive-artwork.md).
