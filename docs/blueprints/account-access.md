# Account access construction

Authority: [account access](../../contracts/behavior/account-access.md),
[session](../../contracts/behavior/session.md), [local data](../../contracts/behavior/local-notes.md)
and [architecture](../../ARCHITECTURE.md).

| File pair / owner | Responsibility and symbols |
| --- | --- |
| Session/AuthenticatedHTTPClient.swift / .kt | HTTPS/local policy, bound lease, Bearer admission, option forwarding, pre/post guards; existing HTTPClient implementation |
| Data/AccountNotesRepositories.swift / .kt | AccountNotesPartition canonical naming; factory-owned Memory/SQLite collections by authority/account |
| Data/SessionNotesRepository.swift / .kt | NotesRepository facade with one lease, pre/post guards and original-target writes |
| Catalog Session model/composition | Retain shared session dependencies, partition factory and probe owner; use isolated local namespaces |
| Catalog Account notes panel | Existing command/query owner and UI primitives; transient drafts and teardown on lease replacement |
| Catalog authenticated probe | Scripted transport, public report and late-response suppression; no provider impersonation |
| Shared account-access fixtures | Bearer refusals and canonical account partition identifiers |
| Native-language and native-app checks | Header/401/race admission, real storage separation, guest preservation, editor lifetime |

Construction order: define the contract/fixtures; bind the existing HTTP port;
name account partitions without changing guest storage; bind CRUD to a lease;
compose lease-keyed editors/probes; verify storage and late results before
recording native reading step 43. Session consumes HTTP for the adapter; Data
consumes Session scope, keeping Services domain ports independent of Session.
Neither kernel nor the one-attempt HTTP implementation owns token renewal.

The next provider slice must validate issuer/audience/membership server-side,
define refresh/replay semantics and implement scoped remote CRUD before claiming
an authenticated connected application.
