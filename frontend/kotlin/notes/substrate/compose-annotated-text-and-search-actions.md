# Compose annotated text and search actions

Claim: one AnnotatedString preserves supplied literal runs while native click
semantics keep primary and sibling result actions independent.

Origin/evidence, 2026-10-09: Kotlin 2.3.20, AGP 9.0.1, Compose BOM 2026.03.01,
Material3 1.4.0, JDK 17; compile/target SDK 36, min SDK 24. Google's
[styled-text guide](https://developer.android.com/develop/ui/compose/quick-guides/content/style-parts-text)
documents buildAnnotatedString/withStyle/SpanStyle for styles within one Text.
History, filter admission and row narration are repository policies.

## Mechanism and example

HighlightSegment carries complete text and emphasis. HighlightedText appends
literal strings into buildAnnotatedString; withStyle applies accent/semibold to
emphasized spans. Native Text supplies one readable value, wrapping and TextStyle
customization. Empty spans/lists are allowed; there is no markup parser or link
action. The feature computes any matches before rendering.

```kotlin
// Conceptual: supplied literal spans are styled without interpreting markup.
HighlightedText(listOf(HighlightSegment("Read "), HighlightSegment("notes", true),
    HighlightSegment(" <today>")))
```

SearchResultRow's clickable Column has native Button role, enabled semantics and
caller contentDescription. A passive child Column clears preview/artwork semantics;
the action slot is outside that region. Clearing the clickable node itself would
erase its native action/role, so only passive descendants are cleared. The host
supplies full open narration and independent sibling eligibility. Suggestion
artwork is decorative; its directional affordance follows LocalLayoutDirection.

## Evidence, gotchas and actual use

[Component checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/DiscoveryComponentTest.kt)
exercise literal Unicode/markup text, native action independence, disabled search,
240-dp font-scale-two growth, RTL and action bounds. A disabled native text field
removes SetText semantics; locate it by its supplied label to check disabled
state rather than requiring an editable-only action. An IME test call returns
Unit, so text replacement and keyboard submission are separate operations.

The fixture uses case-insensitive indexOf and substring for its first match;
these String offsets stay in the Kotlin feature and are never exported to Swift.
It does not establish locale/grapheme-aware matching. Applied/query/saved/history
primitives use rememberSaveable above the preview branch. Filter visibility/draft
use remember; recreation or loss of availability discards an outstanding sheet.
The sheet bounds its scrolling content, and the host clears focus before showing
it or admitting search. SearchField's new enabled parameter defaults true.

Read [UI source](../modules/project/core/ui/README.md#search-and-discovery),
[consumer checks](../modules/project/app/README.md#discovery-gallery) and
[projection reasoning](../../../../notes/patterns/search-projection-and-filter-drafts.md).
Next: test TalkBack and international search data, then connect real query
cancellation/history policy through the existing service seam. No backend or
durable search history is verified by this fixture.
