# Compose media paging and overlays

Claim: a caller-owned PagerState can supply native gesture/position state while
stable record IDs own rating/favorite values outside lazy page composition.

## Origin and evidence

Added 2026-10-09 for the eighth UI batch. Kotlin 2.3.20, AGP 9.0.1,
Compose BOM 2026.03.01, Material3 1.4.0, JDK 17, minSdk 24 / compileSdk 36.
Primary references: [native pager](https://developer.android.com/develop/ui/compose/layouts/pager),
[state and restoration](https://developer.android.com/develop/ui/compose/state)
and [semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics).
Source links and final execution evidence are in the module walkthroughs.

## What and why

Carousel wraps HorizontalPager with caller PagerState, stable keys and an
optional user-scroll flag. It owns no coroutine scope and performs no page
command. The app owns rememberPagerState above route replacement and uses
rememberCoroutineScope to execute Previous/Next. Reduced motion chooses
scrollToPage; ordinary preview motion uses animateScrollToPage.

```kotlin
// Excerpt: native position selects a record; its rating is keyed by record ID.
val study = MediaStudy.all[pager.currentPage]
val rating = values.ratings[study.id] ?: 0
```

currentPage is the native page nearest the snap position. settledPage is useful
when a future feature needs an event only after motion stops. The current
indicator/actions use currentPage and add no analytics collection or autoplay.
Native page identity is supplied with the study ID, rather than its title.

The app keeps a saveable primitive map of ratings, favorite-ID list, enabled flag
and counter above navigation. A MediaValues snapshot is passed to compositions;
its callback updates those owners. Native PagerState has its own saver. A custom
data class is not being assumed saveable simply because its fields are primitive.
The snapshot's per-ID map also avoids teaching list position as business identity.

IconAction uses native button semantics and hides only its passive icon slot.
RatingField uses native TextButton choices in FlowRow, allowing targets to wrap.
PageIndicator replaces dot semantics with one supplied summary. MediaOverlay
clears decorative artwork/scrim semantics and provides independent overlay
content; the host supplies clipping and bounds. Its content color is white,
while supplied native action surfaces retain their theme colors.

## Gotchas, actual use and limits

The API 36 checks execute a real swipe, programmatic paging, per-item ratings,
favorite toggling, exact selected/disabled states, clearing, Back/family/theme
retention and saved-state restoration. A font-scale-two narrow composition
checks minimum native target bounds and proves that decorative artwork/icon text
does not hide an independent overlay action or passive position summary.

The normal test AVD was unavailable through adb and its saved instance was
locked; a temporary read-only instance supplied the emulator checks without
removing or resetting saved data. This does not establish device performance,
complete TalkBack, arbitrary collection replacement, image loading or durable
review storage. Next: which selected-record policy should accompany live media changes?

## Related

- [UI walkthrough](../modules/project/core/ui/README.md#media-browsing-and-actions).
- [Media gallery](../modules/project/app/README.md#media-gallery).
- [Generic content slots](../language/kotlin-covariant-query-state-and-content-slots.md).
- [Shared selection and artwork ownership](../../../../notes/patterns/media-selection-and-passive-artwork.md).
