# Account context and device capabilities

Claim: account-scoped actions and device capability state need distinct ownership,
and a delayed confirmation must validate the context in which it was created.

Origin/evidence, 2026-10-09: the Account UI slice supplies identity choices,
independent device actions and local photo-access scenarios on both platforms.
The native consumer checks distinguish account-qualified removal from device-wide
permission projection. This is a UI fixture, not an authentication implementation.

## What and why

A visible account choice is a context value. It is not a credential and does not
prove that a service is authenticated. The UI menu emits a supplied identity;
the feature decides whether to transition credentials, repositories or routes.
Unknown selection should remain unknown rather than quietly choosing the first
account, especially when actions depend on that identity.

A confirmation can outlive the row that created it. Capture original account and
device IDs, then check current context, availability and policy again before
admitting removal. Closing an obsolete prompt improves the interface; the guard
is still necessary for an already queued callback. Cancellation does not create
a command. Protecting the current device is example policy, not a reusable row
rule or backend authorization guarantee.

OS capabilities have another scope. Switching a visible workspace does not reset
photo authorization. A permission card receives readable status and actions;
platform adapters decide actual prompts, limited access, system settings and
refresh after the app returns to the foreground.

## Example and gotchas

Personal and Studio team begin with three local fixture devices. Removing
Personal's desktop stores a Personal-qualified key. Studio team still shows three;
returning to Personal shows two. An old Personal confirmation cannot remove a
Studio device. Allowing the local photo preview stays Allowed across both choices.
Pending prompts disappear on context/availability changes and are not restored
as outstanding work after Android saved-state recreation.

A production domain must decide whether multiple workspaces share one principal
and its sessions. The fixture's qualified keys demonstrate isolation; they do not
prescribe a real token/session model. Do not place secrets in saved UI state,
assume UI disabled state is authorization, or conflate an illustrative permission
status with an OS result. The permission example never calls a platform API.

## Actual use and next questions

Read [Swift account flow](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#account-gallery),
[Kotlin account flow](../../frontend/kotlin/notes/modules/project/app/README.md#account-gallery)
and [component usage](../../docs/blueprints/ui-components.md#accounts-and-access).
Related: [command admission and mutation ownership](forms-and-mutation-ownership.md).
Next: define principal/workspace/session scope for a real identity service, then
connect guarded commands and OS permission adapters through existing thin seams.
No full assistive-technology, localization, physical-device or secure-storage
audit is established by this UI slice.
