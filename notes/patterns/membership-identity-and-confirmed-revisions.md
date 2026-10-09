# Membership identity and confirmed revisions

Claim: a membership command needs stable identity and current admission, while a
confirmation must identify the state that was reviewed rather than a row index.

Origin/evidence, 2026-10-09: the twenty-first UI batch builds Sharing preview
over fixed contacts and one local workspace. Native source/checks exercise role
changes, protected owners, admitted invitations, stale removal rejection and
retained values. These are UI fixtures rather than real authorization rules.

## What and why

Reusable member rows receive identity copy, passive avatar and independent
access/action slots. A link card receives link text or unavailable copy plus
status/actions. They do not know which role may invite, whether a link grants
access or how an operation reaches a provider. The feature supplies native menu
choices and callbacks at that seam.

Membership identity differs from an address or display name. The local contact
lookup matches trimmed, case-folded example addresses to known identities;
duplicates are rejected by ID. It is not email validation or canonicalization.
The owner is protected, and only present, available nonowners can change roles
or be removed. Disabled/pending UI is paired with the same command guard.

Removal confirmation captures member ID and the membership revision. Inviting,
changing a role, removing or resetting members increments that revision. Confirm
rechecks revision and current admission; any intervening membership change makes
the request stale. This deliberately conservative, workspace-wide check can reject
a removal after an unrelated member changes too. The transient dialog closes on
invalidated eligibility. Cancellation changes no membership.

## Example and gotchas

Invite River as Editor, then request Jamie's removal. Changing a membership role
before confirming invalidates that request. A new explicit request is needed.
Saved-state recreation retains members, roles, invite draft and link choice on
Android but discards the dialog; it does not remove anyone. Reset members restores
initial membership/roles and invalidates outstanding requests while preserving
action counts, link choice and invite draft/role.

Copy is a separate admitted effect. Turning link access Off removes the displayed
link and disables the app's copy button. It does not erase a previous clipboard
value. Native manual text selection is also separate from button admission: a
displayed link remains selectable while feature controls are disabled/pending.
The example URL and role labels grant no actual capability; a real provider owns
token creation, revocation and access enforcement.

This local revision is not a server concurrency guarantee. A real command needs
workspace/account scope, service-admitted identity/version, idempotency and outcome
handling before updating membership. Copying text, native share presentation and
sending an invitation are distinct effects; this preview sends nothing.

## Actual use and next questions

Read [Swift Sharing flow](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#sharing-gallery),
[Kotlin Sharing flow](../../frontend/kotlin/notes/modules/project/app/README.md#sharing-gallery),
[usage](../../docs/blueprints/ui-components.md#sharing-and-access) and
[behavior](../../contracts/behavior/ui-components.md#sharing-and-access).
Related: [account context](account-context-and-device-capabilities.md),
[forms/mutation ownership](forms-and-mutation-ownership.md) and
[selection identity](selection-identity-and-undo.md).
Next: bind commands to service versions and account scope; exercise localized
assistive traversal and native selection/clipboard presentation separately.
