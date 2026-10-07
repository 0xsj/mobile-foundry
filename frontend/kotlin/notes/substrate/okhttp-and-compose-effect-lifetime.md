# OkHttp and Compose effect lifetime

A shared OkHttp client supplies native calls while a keyed Compose effect owns the current screen request.

## Origin

Observed 2026-10-08: OkHttp/MockWebServer 5.4.0, coroutines 1.10.2,
kotlinx.serialization JSON 1.9.0, Kotlin 2.3.20, AGP 9.0.1, Gradle 9.1.0,
compile/target SDK 36, minimum SDK 24. Primary references:
[OkHttp releases](https://github.com/lysine-dev/okhttp/blob/main/CHANGELOG.md),
[Call cancellation](https://lysine.dev/okhttp/recipes/),
[Compose effects](https://developer.android.com/develop/ui/compose/side-effects),
[serialization 1.9.0](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.9.0).

## What and why

The adapter derives a client with retries, redirects, cache, cookies and
credential authenticators disabled. It shares the supplied client's dispatcher
and pool; create/reuse transport at app composition instead of making a new
network client for every request. OkHttp callback reads buffered bytes and
closes the response even when cancellation wins. Cancellation calls Call.cancel;
known I/O failures select values, and diagnostic observers receive original errors.

`LaunchedEffect(scenario, generation)` starts a coroutine for the current
inputs. Compose cancels it when keys change or the screen leaves composition.
The screen rethrows CancellationException, checks activity before state
publication, and reports unexpected exceptions separately from public copy.
`remember` stores UI selection for the current composition; it is not durable
application persistence or process restoration.

## Example and dependency finding

The host MockWebServer tests observe actual requests, delayed body cancellation,
redirect refusal and interrupted error bodies. The Android consumer initially
rejected OkHttp 5.5.0 because its Android artifact requires compile SDK 37.
The slice pins 5.4.0, whose artifact passed API 36 AAR validation and APK
assembly. The upstream changelog records 5.5.0's API 37 integration. JVM-only
compilation had passed with 5.5.0, so consumer builds were necessary evidence.

## Gotchas and limits

A custom client can still carry interceptors and native connect/read timeout
settings; the transport does not erase them. Interceptors must respect the
single-attempt policy. The application deadline covers full body reading;
an earlier native timeout may also become a timeout value. Buffered body reads
are not streaming. HTTP is JVM-testable but exposes OkHttp HttpUrl as a deliberate
native substrate type; this slice makes no Kotlin Multiplatform promise.

## Used in

Android foreground feature calls and host-tested services.

## Related

[Suspending ports](../language/kotlin-suspending-ports-and-cancellation.md),
[Gradle bootstrap](gradle-and-android-bootstrap.md).
