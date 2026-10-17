# A remote receipt is not a navigation request

Claim: transport admission, foreground receipt and user-requested navigation are
separate transitions, and notification identity includes its kind as well as ID.

Origin: native remote-preview slice, 2026-10-17. The backend already emitted a
generic versioned preview. Source inspection showed that treating it as the local
preview would merge unrelated opens and confuse receipt with a tap. Shared
negative fixtures and state-owner tests exercise the resulting boundaries.

## What and why

OS callbacks contain transport details that do not belong in an application value.
Apple wraps custom data beside aps. Firebase exposes data in a foreground callback,
but puts it beside SDK metadata in a launcher Intent after a background tap.
Decode each envelope deliberately; stripping arbitrary extras would hide an
unrecognized application command or account selector.

After decoding, a foreground receipt records only diagnostics. A user tap creates
an open request. That request cannot authorize data access: even an exported
Activity can receive a correctly shaped forged Intent. A generic diagnostics
destination needs no account; a future private destination must resolve current
session/account authority and fetch authoritative data.

Conceptual state sequence:

```text
foreground callback -> strict decoder -> receipt -> optional native presentation
user tap -> envelope decoder -> bounded pending open -> app navigation -> consume exact open
```

## Identity and gotchas

Two payload kinds can legitimately reuse one UUID. Deduplicating just the UUID
would suppress one, and consuming by UUID could clear the other. A tagged value
keeps both cases distinct without making the ID authoritative. A bounded window
is a useful process policy, not a durable receipt or exactly-once claim.

Background OS alerts can be displayed before app admission. Refusing their open
does not retract the visible notification. A foreground callback that can update
diagnostics is not evidence that a cold-process provider flow works. SDK-shaped
fixture Intents do not prove Firebase delivery either.

## Used in and related

[Construction and checks](../../docs/blueprints/remote-notifications.md) links both
native value/router implementations, adapters and actual acceptance evidence.
Continue from [device notifications](device-notifications-and-navigation-hints.md)
and [provider address ownership](provider-addresses-and-registration-intents.md).
Next question: what extra session/account admission would a private inbox-item
destination need, and where should that lookup run after a cold launch?
