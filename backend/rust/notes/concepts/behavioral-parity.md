# Behavioral parity

Claim: a second implementation is equivalent only within an explicitly checked
contract scope; matching directories and success responses are insufficient.

Origin/evidence: 2026-10-18 construction of a shared runtime corpus and observed
Go/Rust process execution. This is a project design conclusion from those checks,
not a claim that a framework supplies backend equivalence.

For example, a correct HEAD response has the GET representation's headers and
no body. A 405 must retain the public problem format and Allow header. Internal
failure projection must remove code/fields/diagnostics even when the condition's
author supplied public text. These are observable choices independent of how
either server stores an error or schedules a connection.

Rust can preserve those choices with Result, Arc and owned task handles while Go
uses error returns, contexts and goroutines. API clients care about the contract;
source readers benefit from corresponding module ownership rather than forced
language-level imitation. Protocol parser and router behavior can still diverge.

Gotcha: two implementations can agree on the same bug. Fixtures therefore contain
literal expected values and check each independently. A finite corpus proves
those cases, not all inputs. The inventory records excluded encoded/cleaned paths,
network budgets and business modules rather than promoting runtime acceptance
to overall backend parity.

Used in [the parity inventory](../../PARITY.md). Related:
[shared fixture technique](../techniques/shared-runtime-fixtures.md). Next:
which Identity refusal, revocation and renewal cases must be shared before native
clients can select Rust without changing behavior?
