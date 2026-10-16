# MessageDigest partitions and authenticated OkHttp

Stable digest filenames and explicit coroutine ownership keep persistent account data separate from a renewed session lease.

## Origin, versions and evidence

Observed 2026-10-10 with Kotlin 2.3.20, JDK 17, AGP 9.0.1, coroutines 1.10.2,
API 36 and minSdk 24. Primary references:
[MessageDigest](https://developer.android.com/reference/java/security/MessageDigest),
[OkHttp call source](https://github.com/square/okhttp/blob/parent-5.3.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Call.kt)
and [RFC 6750](https://www.rfc-editor.org/rfc/rfc6750#section-2.1).
The JVM transport check uses actual OkHttp and a loopback MockWebServer; the
account partition fixture runs against Android's native digest and SQLite APIs.
Native editor evidence and executed counts belong to the app walkthrough.

## What and why

Create a MessageDigest for each canonical tuple rather than sharing its mutable
digest state. Convert signed JVM bytes to unsigned integers before hexadecimal
formatting. Versioned UTF-8 plus fixed two-digit lowercase hex produces the same
filename key as Swift, without relying on JVM hashCode or case-sensitive storage.

AuthenticatedHTTPClient is a suspend implementation of the domain-facing HTTP
port. Invoke it on the session's single UI executor. OkHttp owns network callbacks;
suspension returns to the caller's coroutine context for the post-response lease
check. SessionNotesRepository follows the same rule while SQLite confines its
native work to Dispatchers.IO. The Kotlin compiler does not enforce this executor
contract; source composition and tests must preserve it.

## Example and gotchas

```kotlin
// Conceptual: persistent partition identity and transient editor identity differ.
val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
val name = digest.joinToString("") { "%02x".format(it.toInt() and 255) }
key(lease) { AccountEditor() }
```

HTTP requests own one Bearer header, reject caller overrides and use HTTPS unless
loopback development is explicitly enabled. OkHttpTransport refuses redirects,
ambient cookies/authenticators and automatic retries. No interceptor logs tokens.
The request/client boundary does not renew or replay a 401. MockWebServer verifies
the bearer reaching a native request and a redirect remaining a single attempt.

An initial test used virtual-time runTest with a real network callback. Its deadline
could advance before the callback arrived, and an unbounded takeRequest could hang.
The corrected transport test uses runBlocking and a bounded server request wait;
controlled race tests continue to use deterministic virtual time/deferred gates.
Virtual-time tests establish ordering, not physical network latency.

## Used in and next

Read [Session](../modules/project/core/session/README.md#authenticated-http),
[Data](../modules/project/core/data/README.md#account-collections) and
[catalog](../modules/project/app/README.md#account-access). Compose remembers an
editor inside key(lease), collects state with lifecycle and shuts it down on
disposal; clearing TextFieldState removes the native transient draft too.
Next: which provider owns renewal policy and how should remote write uncertainty
and account membership be surfaced to a feature?
