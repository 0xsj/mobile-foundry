# Health service walkthrough

HealthService owns endpoint selection and successful domain admission above the HTTP port.

## Origin

The 2026-10-08 slice follows Bento's health live/ready services.
[HTTP and health contract](../../../../../../../contracts/behavior/http.md) controls admission; this walkthrough mirrors the services package/module.

## Reading order

1. [HealthService source](../../../../../project/core/services/src/main/kotlin/dev/mobilefoundry/services/HealthService.kt), from injected HTTPClient through live/ready to admission.
2. [HTTP walkthrough](../http/README.md), for request and problem handling below this service.
3. [Health service tests](../../../../../project/core/services/src/test/kotlin/dev/mobilefoundry/services/HealthServiceTest.kt) and [Shared response/health cases](../../../../../../../contracts/fixtures/http/responses.json), for independent admitted/rejected shapes.

## Walkthrough

`live` selects `health/live`; `ready` selects `health/ready`. Each requests a GET
entity and passes options through. Expected failures return unchanged. A success
must contain a root JSON object whose status is the string `ok`. Array, null,
missing, wrong or numeric status is internal/http.invalid_response with IDs
retained. Extra response fields are discarded.

The admitted Health exposes status, IDs and an optional version from ETag.
The catalog never reads arbitrary wire JSON. There is no native session
singleton, repository, maintenance endpoint or HTTP catch in this service.
Structured cancellation and defects keep propagating to the caller.

## Verification and limits

Two host tests passed on this platform through `make http-test`: shared health
shapes and live endpoint, plus ready endpoint and failure metadata propagation.
The consumer app compiled. These are injected exchanges; they do not establish
a backend deployment or provider health semantics.

## Gotchas

HTTP success is necessary but insufficient for a successful domain result.
A 204 is a valid empty HTTP entity but invalid Health. An ETag is opaque; this
slice never interprets or manufactures a version number from it.

## Questions for the next session

- Why should unknown successful fields be discarded?
- What endpoint behavior belongs in a service rather than the client?
- Which layer will own session renewal when an operation is unauthenticated?

## Related

[Transport, service, screen](../../../../../../../notes/patterns/transport-service-and-screen.md) and [Catalog walkthrough](../../app/README.md).
