# Deadlines and owned cancellation

A request deadline is an expected timeout; cancellation means the owner no longer wants the work.

## Origin

Native HTTP slice, 2026-10-08. Source inspection and executed Swift task-group,
Kotlin coroutine, URLSession, and OkHttp tests establish the distinction in
this slice. [HTTP contract](../../contracts/behavior/http.md) owns the behavior.

## What and why

A screen owns its current request. Changing its input or leaving the screen
cancels that work. The cancellation must propagate through the native call and
release its resources. Reporting every navigation cancellation as a failure
would show an error for an ordinary user action.

The request owns its deadline. When that deadline expires while the screen still
wants an answer, the caller receives a timeout failure and can deliberately show
retry controls. Deadline timing is separate from `Retry-After`, which describes
server timing and grants no permission to retry.

## Example

Selecting Timeout in either health catalog starts a slow injected exchange with
a 100 ms budget. It produces Request timed out. Selecting another scenario
cancels the old operation, then displays the new service result. Native adapter
tests additionally observe URLSession stopping and OkHttp Call.cancel being called.

## Gotchas

Cancellation is cooperative. A Swift task group waits for child cleanup before
returning; an injected adapter that ignores cancellation can delay the deadline
result. Cancellation checks around awaits and before state publication prevent
obsolete work from publishing a successful result.

Stopping the local request does not establish that a remote write was undone.
A lost response creates uncertainty that future idempotency and synchronization
contracts must handle. These examples only read health.

## Used in

Screen work, search replacement, asset loading, and account-scoped operations.
Application lifecycle ownership is still separate from process-wide background scheduling.

## Related

[Expected failures and diagnostics](expected-failures-and-diagnostics.md) and [Transport, service, and screen](../patterns/transport-service-and-screen.md) explain the value and service boundaries.
