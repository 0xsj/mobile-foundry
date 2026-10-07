# Infrastructure support

- `local`: Development dependencies shared by profiles.
- `testing`: Shared integration, fault, and performance tooling.
- `deployments`: Product or backend deployment configuration.

These are placeholders. Add infrastructure when a selected workflow requires
it. Native kernel and UI catalog development should run through fakes without
requiring backend services.

Profile-specific Compose stacks live with [Supabase](../backend/supabase/README.md)
and [Firebase](../backend/firebase/README.md) under `backend`, so their setup
and configuration have one owner.
