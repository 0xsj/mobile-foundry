# SwiftUI attributed text and search actions

Claim: one native attributed text value can retain literal copy while native
buttons separate result opening from independent actions.

Origin/evidence, 2026-10-09: Swift 6.2.3, Xcode 26.2, macOS 26.3 and iOS 26.2
simulator; package minimum iOS 17/macOS 14. Apple's
[Text documentation](https://developer.apple.com/documentation/swiftui/text)
documents styled AttributedString content. Literal segment assembly, complete
open narration and feature-owned history/drafts are repository choices.

## Mechanism and example

HighlightSegment stores complete text plus an emphasis flag. HighlightedText
creates AttributedString values directly from strings, attaches native Font and
Color to emphasized runs, appends them and renders a single Text. It never calls
the Markdown initializer. Supplied native font and emphasis color override the
default body/semibold/accent treatment. Joined literal copy supplies narration.

```swift
// Conceptual: complete literal runs come from a feature or admitted search result.
HighlightedText([HighlightSegment("Read "), HighlightSegment("notes", highlighted: true),
                 HighlightSegment(" <today>")])
```

SearchSuggestionRow composes ListRow inside one native Button. SearchResultRow
has a native open Button with passive leading/preview content; the actions builder
is its sibling. accessibilityElement(children: .combine) keeps one native button
target, with its narration replaced by the host's complete action label. Keep nested controls outside
the open region. Global row enabled applies only to that primary action.

## Evidence, gotchas and actual use

[Native checks](../../apps/FoundryCatalog/Tests/DiscoveryComponentTests.swift)
cover retained identity, history admission, hidden/disabled action guards,
literal Unicode/markup preservation and actual 240-point hosted geometry.
Dynamic Type grows the row; the save action remains below the excerpt with
minimum target bounds, and leading artwork follows RTL. These checks do not
establish complete VoiceOver traversal or multilingual search equivalence.

Manual native observation, 2026-10-09: the iOS 26.2 simulator catalog exposed
suggestions and open-result rows as buttons with complete supplied labels,
separate Save targets and initial zero open/save counts. The first implementation
used children: .ignore and exposed untyped elements; children: .combine corrected
the native button roles. Further manual workspace interaction was deferred when
the simulator was used for another app. Android's focused consumer checks cover
the full local workflow; Swift's hosted/state checks cover the stated limits.

The fixture obtains a String range for its first literal match and slices with
native String indices; the component never receives UTF-16 offsets. Do not
reinterpret offsets from a backend without a documented text/range encoding.
Filter draft/presentation are view-local State. The sheet copies committed
filters on opening and Apply alone writes them. A guarded query binding and
default-enabled SearchField keep edits/clear/submission outside disabled state.

Read [UI source](../modules/packages/FoundryUI/README.md#search-and-discovery),
[consumer flow](../modules/apps/FoundryCatalog/README.md#discovery-gallery) and
[projection reasoning](../../../../notes/patterns/search-projection-and-filter-drafts.md).
Next: test VoiceOver and international search data, then connect a query owner
without moving matching, debounce or service execution into the text/row APIs.
