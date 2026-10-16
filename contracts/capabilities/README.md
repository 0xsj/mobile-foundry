# Synchronous module capabilities

Reserved contract home under the [module boundary policy](../behavior/module-boundaries.md).
[account.access.v1](account-access-v1.md),
[identity.credential.resolve.v1](identity-credential-v1.md) and the local registry/invoker
are implemented with memory/PostgreSQL module profiles. Other capabilities and remote
invocation remain planned.

Each provider-owned version defines its name, purpose, allowed callers, admitted
request/response values, successful negative results, failures and deadline
behavior. Mutations additionally define operation identity, replay and uncertain
commit handling. Keep portable fixtures alongside concrete specifications.

Consumers implement their own application ports through contract clients;
providers admit requests at their boundary and invoke their own handlers.
Wire types stay inside adapters. Local and remote invocation must preserve the
same behavior and caller policy.

Use names shaped like `<owner>.<operation>.v1`. Each new capability requires its
own admitted contract and declared provider/consumer requirements.
