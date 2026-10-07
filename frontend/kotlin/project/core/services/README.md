# Services core

Implemented native HTTP/health foundation. See [HTTP and health contract](../../../../../contracts/behavior/http.md) for behavior and
[Module walkthrough](../../../notes/modules/project/core/services/README.md) for source flow, language mechanics, checks and limitations.

Run `make http-test` from the repository root. Runtime libraries do not load
checkout fixtures. The catalogs inject responses and need no running backend.
