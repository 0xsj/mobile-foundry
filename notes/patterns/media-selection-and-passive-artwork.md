# Media selection and passive artwork

Claim: native page position, record identity and per-record values are distinct,
while decorative media must not absorb the semantics of its overlay actions.

## Origin and evidence

Added 2026-10-09 for the eighth reusable UI batch. The catalog uses three static
procedural studies and caller-owned ratings/favorites. Native walkthroughs link
source, executed tests and observation limits. This is UI/state evidence, not
photo admission, image loading, review persistence or a GPU performance result.

## What and why

A carousel is a presentation container. Swift's scroll position binds a stable
record ID; Kotlin's PagerState exposes a native page index. The host maps either
to the selected record. Stable page keys keep native identity separate from a
title or position; rating/favorite values stay keyed by record ID outside lazy
page content. A page can disappear without destroying the user's value.

PageIndicator projects current position through decorative dots and one supplied
accessible summary. It is not a tab control. Explicit Previous/Next actions are
separate native controls, useful when swiping is inconvenient. Native paging
owns gesture physics; the app owns any programmatic page command and reduced
motion policy. The component adds no autoplay or repeated timer.

Example: rate Orbit four of five and favorite it; move to Field, whose rating is
still zero; return to Orbit and recover four of five plus the favorite. Clearing
a rating changes only the selected record. Navigating to a separate preview uses
the same caller-owned values. Hoisting retains them across navigation; durable
storage is a different decision.

MediaOverlay treats its artwork and scrim as decorative, while title and actions
remain in an independent overlay slot. IconAction replaces passive icon text or
image semantics with a localized button label. MediaTile groups supplied metadata
without merging its action children into a single whole-card target. This avoids
putting a Favorite action inside another click target or hiding it with artwork.

## Gotchas and alternatives

- Before replacing/reordering records, admit a valid selection and preserve
  per-record values by identity. The current fixture has a fixed three-record
  order; its tests do not establish arbitrary live collection replacement.
- Empty collections should render an appropriate EmptyState outside Carousel.
  PageIndicator is intentionally limited to 20 pages; large collections need
  another position presentation rather than hundreds of dots.
- RatingField receives a valid integer from zero through maximum (at most ten).
  Zero is unrated; clearing, validation, aggregation and saving are host policy.
  A selected star button is the exact choice, while filled stars depict the total.
- MediaOverlay needs bounded dimensions and caller clipping. A bottom scrim aids
  white-copy contrast; arbitrary artwork/actions still need host design review.
- Hidden artwork slots must not contain controls. Put meaningful image copy or
  actions in the accessible overlay, or use another media composition.
- Lazy content is not an owner for important values. Native framework state and
  saved-instance restoration are also not a durable domain cache.

## Used in and related

Read [behavior](../../contracts/behavior/ui-components.md#media-browsing-and-actions),
[usage](../../docs/blueprints/ui-components.md#media-browsing-and-actions),
[Swift paging](../../frontend/swift/notes/substrate/swiftui-media-paging-and-overlays.md)
and [Compose paging](../../frontend/kotlin/notes/substrate/compose-media-paging-and-overlays.md).
Compare [component slots](component-slots-and-caller-owned-state.md),
[refresh/page ownership](refresh-and-pagination-ownership.md) and
[admitted assets versus GPU resources](editable-values-and-renderer-resources.md).
Next: what should a real media browser preserve when its admitted record set changes?
