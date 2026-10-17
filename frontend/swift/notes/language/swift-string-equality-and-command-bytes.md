# Swift string equality and original command bytes

Claim: user-facing String equality is too permissive for an immutable command's
exact input identity. Origin: pending Notes slice, 2026-10-17, Swift 6.2.3. The
Services test demonstrates that precomposed and decomposed accented titles compare
equal as String while their UTF-8 sequences differ.

## What and why

Swift compares canonically equivalent Unicode text as equal. That is useful for
text semantics. A command ID, however, names one exact previously accepted input;
normalizing or substituting that input can disagree with a server receipt/replay
check. Store the original title and compare UTF-8 when establishing command identity.
[Swift string comparison reference](https://docs.swift.org/swift-book/documentation/the-swift-programming-language/stringsandcharacters/#Comparing-Strings).

```swift
// Conceptual: equivalent text does not establish identical original input.
let composed = "Café"
let decomposed = "Cafe\u{301}"
assert(composed == decomposed)
assert(!composed.utf8.elementsEqual(decomposed.utf8))
```

## Used in, evidence and limits

[PendingNoteRecord.matches](../../packages/FoundryServices/Sources/FoundryServices/PendingNotes.swift)
compares command fields and original title UTF-8. The existing remote receipt
matcher already compares bytes. Shared fixtures reject a canonically equivalent
but different receipt title; service tests verify exact JSON round-trips and
same-ID/different-byte conflicts, including embedded NUL.
[Tests](../../packages/FoundryServices/Tests/FoundryServicesTests/PendingNotesTests.swift)
pass. Disk stores keep JSON blobs, so no zero-terminated text read truncates input.
This is an operation identity rule, not a recommendation to replace ordinary text
equality throughout UI/search. Next: which other payload fields are byte-sensitive?

## Registration boundaries — 2026-10-17

Claim: command admission may normalize selected fields while retaining exact
password/display-response bytes. The registration command trims an explicit Go
whitespace set and lowercases only admitted ASCII login input; passwords are
unchanged. Count unicodeScalars for the Go rune minimum and utf8 for the byte cap,
not String.count. These measure different aspects of the same String.
The [Swift book source](https://raw.githubusercontent.com/swiftlang/swift-book/main/TSPL.docc/LanguageGuide/StringsAndCharacters.md)
explains the distinct Character/scalar/encoding views.

The HTTP reply must preserve the admitted display-name bytes. A precomposed name
cannot silently confirm a decomposed submitted name even when String equality
returns true. Registration tests exercise that mismatch, supplementary emoji,
combining marks, spaces and NUL. Read
[the adapter/checks](../../packages/FoundrySession/Tests/FoundrySessionTests/IdentityRegistrationTests.swift)
and [actual execution](../../../../docs/blueprints/native-registration.md).
Next: make internationalized login canonicalization an explicit provider contract
before widening this deliberately ASCII native field.
