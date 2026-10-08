# SwiftUI refresh and lazy activity

Claim: a native refresh action must await host work, while row expansion needs
stable caller-owned identity independently of native list realization.

## Origin and evidence

Added 2026-10-09 for the seventh UI batch. Swift 6.2.3, Xcode/SwiftUI 26.2,
iOS 17 minimum and macOS 14 package minimum. Source inspection and simulator
checks are distinguished in the module walkthroughs. Primary references:
[List](https://developer.apple.com/documentation/swiftui/list),
[refreshable](https://developer.apple.com/documentation/swiftui/view/refreshable(action:)),
[view task](https://developer.apple.com/documentation/swiftui/view/task(id:priority:_:))
and [lineLimit](https://developer.apple.com/documentation/swiftui/view/linelimit(_:)-4hzfa).

## What and why

RefreshContainer attaches refreshable to supplied content. It does not create a
ScrollView or launch an unstructured task. The catalog supplies a bounded List,
so native refresh has a supported scrolling container. Native refresh awaits the
callback; the fixture sets its own admission flag and checks cancellation before
applying rows. A refresh button also offers an explicit accessible action.

```swift
// Excerpt: RefreshContainer's native adapter; Content is caller-supplied.
public var body: some View { content.refreshable(action: action) }
```

The app's page-request generation drives a view task. Duplicate/overlapping
requests are rejected before incrementing that generation. A cancellation catch
restores an idle footer without adding rows. Leaving increments a revision, so
an older refresh callback cannot apply results even if its suspension does not
cooperate with cancellation. The model lives above the navigation destination.
These are feature mechanics, not responsibilities of LoadMoreFooter.

ExpandableText changes native Text's line limit through a binding and exposes a
separate Button. It does not guess whether arbitrary copy overflows. Full copy
remains in native accessibility text even when the visual lines are limited;
manual AX inspection observed this in the example. Expansion has no added
animation, and native Dynamic Type determines its height. AvatarGroup ignores
individual passive artwork semantics in favor of a supplied complete summary.

## Gotchas, actual use and limits

The native test activates the hosted UIRefreshControl programmatically and
holds the async action at a gate. It verifies that the spinner stays active
until the work completes, then stops. This establishes control/action lifetime,
not a physical pull gesture. CUA drag attempts did not change the refresh counter
and are not counted as gesture evidence. Android's separate emulator check does
execute a pull gesture.

Other tests cover page recovery, exhausted state, cancelled refresh/page work,
late-result invalidation and actual hosted expansion geometry at accessibility3.
A real feed must still choose errors, merge policy, cache and durable ownership.
No physical-device, comprehensive VoiceOver or macOS runtime audit is claimed.
Next: how should an entry-owned store arbitrate refresh against a remote cursor?

## Related

- [UI walkthrough](../modules/packages/FoundryUI/README.md#activity-and-paged-collections).
- [Activity gallery](../modules/apps/FoundryCatalog/README.md#activity-gallery).
- [Native request lifetime](urlsession-and-view-task-lifetime.md).
- [Shared refresh/page ownership](../../../../notes/patterns/refresh-and-pagination-ownership.md).
