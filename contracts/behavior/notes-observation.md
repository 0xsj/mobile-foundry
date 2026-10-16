# Native Notes observation

Status: native foundation slice, 2026-10-10. No backend, remote sync or query cache.

NotesObserver is a separate read capability beside NotesRepository. Swift returns
an owned NotesSubscription containing AsyncThrowingStream and explicit idempotent
cancellation; Kotlin returns a cold Flow. ObservableNotesRepository
composes both capabilities without broadening existing CRUD ports or their fakes.
Each subscriber receives the current owned snapshot, including an empty list,
then snapshots after successful mutations. Order and admission match local Notes.
Equal snapshots may be suppressed and bursts coalesced: this is current state,
not a receipt, audit log or one-event-per-write guarantee. Slow readers have
bounded buffering and eventually see current state while collection stays active.

Memory observation covers that repository instance. SQLite observation registers
before its initial read and coordinates successful writes from repository instances
using the same admitted database path/name in this process. Notifications follow
commit, including a commit whose caller is canceled before acknowledgement.
Failed/missing mutations do not notify. Reads keep native handles operation-scoped;
subscription holds no open database handle. No schema or guest/account path changes.
External SQL, other processes, file aliases and server writes are not observed;
explicit restart/foreground resume rereads authoritative local storage.

Expected read failures are values and preserve the reader's last successful state.
A later committed write or explicit restart may produce a fresh read; no timer or
automatic error retry is added. Unexpected defects terminate the source and reach
the current feature's diagnostic boundary. Native cancellation remains cancellation.

The feature owns one collection task/job and generation. Pause cancels collection
and retains last content; resume makes a fresh subscription and initial read.
Restart replaces collection while retaining prior content. Shutdown is terminal,
cancels collection and clears data. Current-generation and optional session lease
admission cover values and errors. Closed account access clears the reader rather
than retaining the previous account's content. Consumers cancel their owned task;
they also cancel the Swift subscription on early exit or when never iterated.

Live Notes gallery composes a CRUD editor and two independent live readers using
Memory or a dedicated SQLite namespace. Pause the second reader, mutate with the
editor, then resume it to read current state. Sources remain owned by this route;
switching storage pauses outgoing subscriptions and returning resumes their state.
Memory survives ordinary reconstruction/configuration while the route owner lives,
and is discarded when that route owner closes. Device records survive reopening.
Guest Local notes and account collections stay separate. Identity and session also
embeds a lease-owned live reader for the current account.

Screen/lifecycle inactivity pauses readers. Foreground resume rereads local state;
Android STARTED lifecycle collection and Swift scene activity/view lifetime own
this policy. Explicitly paused second reader stays paused on foreground resume.
The catalog uses existing UI primitives; no new reusable UI family is introduced.

Shared scenarios and native tests establish current-state delivery, two subscribers,
independent pause/resume, burst convergence, failed mutations, SQLite cross-instance
notification, cancellation cleanup, read failure/defect admission and account closure.
No OS background observation, remote reconciliation, backup or physical-device
performance guarantee follows from this slice.
