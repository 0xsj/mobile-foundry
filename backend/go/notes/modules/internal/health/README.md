# Readiness through a narrow port

A Health query can ask whether the process is ready without knowing its HTTP
listener, module implementations or future database provider.

Origin: runtime batch, 2026-10-12. Source and runtime tests exercise this small
hexagonal example; it is not a business module or full command/query exemplar.

Read [query/port](../../../../internal/health/app/query/check.go),
[query tests](../../../../internal/health/app/query/check_test.go),
[transport](../../../../internal/health/transport/http/v1/handler.go),
[module wiring](../../../../internal/health/module.go), and
[probe adaptation](../../../../internal/health/build.go).

The process adapts readiness as ProbeFunc. The query checks context before and
after the probe, then deliberately maps a failing probe to unavailable with a
private cause. Transport invokes the query and writes the narrow status object.
Module composition connects those pieces. Health has no aggregate invariant,
write command, persistence or event, so those layers are not manufactured here.

The application imports neither HTTP nor process composition. Transport imports
shared HTTP helpers and depends on a Handle interface. AST policy tests protect
these directions and future reserved business layers. Race-enabled tests prove
cause preservation, cancellation, startup/drain readiness and wire behavior.
Health receives an assembled readiness probe. Memory has no database dependency;
PostgreSQL-selected module units probe their owned stores/pools without putting
driver imports or database ownership in Health.

Related: [ports and composition](../../../patterns/ports-and-module-composition.md)
and [startup](../../cmd/server/README.md). Next: should a remote provider failure
make an entire product unready, or only a specific workflow unavailable?
