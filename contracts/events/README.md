# Integration event contracts

Reserved contract home under the [module boundary policy](../behavior/module-boundaries.md).
[Account facts v1](account-v1.md), [Identity facts v1](identity-v1.md), local
delivery and a bounded dispatcher are implemented with memory/PostgreSQL
outbox/inbox storage. Broker adapters remain planned.

Each producer-owned version describes a committed fact, required envelope and
payload fields, allowed attribution, scope, ordering/version semantics and
privacy projection. Define independent consumer identities, duplicate/replay
fixtures and compatibility rules with the first delivered event.

The generic envelope carries stable identity and causal metadata; business
payloads belong here and internal domain facts remain with their producer.
State/outbox and consumer state/inbox atomicity are persistence requirements.
At-least-once delivery does not imply globally ordered or exactly-once execution.

Use names shaped like `<owner>.<fact>.v1`. Account's create/rename messages are
the first concrete schemas; business payloads remain outside generic `pkg/events`.
