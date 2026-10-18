# Retained job conformance

[Rust scope](../../behavior/rust-push-jobs.md) mirrors the shared
[job](../../behavior/push-jobs.md) and [recovery](../../behavior/push-job-recovery.md)
contracts.

- [transitions.json](transitions.json): 57 independent expected domain outcomes.
  Each case restores a valid snapshot at fixed microsecond time, applies one
  operation and checks status/failure kind, attempts, claim admission, result code
  or backoff. Optional fields override fixture defaults. Sources retain their own
  outcome when a new replacement is admitted.
- [http.json](http.json): 21 replacement guard cases under a retained uncertain
  source and active owning session. Omitted fields select POST, current auth/proof,
  revision 1 and a valid true acknowledgement. Explicit null if_match means absent.
  $CHILD$/$SOURCE$/$PROOF$ are owned harness values. Refusals must create no child.

Run `make backend-push-jobs-parity-test` for Go/Rust rules and memory behavior.
Run `make backend-postgres-parity-test` for actual compiled HTTP workers, shared
guard cases and Rust→Go→Rust retained outcome/lineage handoff. That runner owns
its database and private ephemeral test keys and clears host app selections.
Provider encoding refusal occurs before network; no cloud service is contacted.
The separate crash helper records a simulated file effect, not a real notification.

The Go transition test is repository-only under parity and stays outside starter
source copies. No Go production source or migration bytes change in this slice.
