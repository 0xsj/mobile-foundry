# Supabase Compose provenance

The Compose baseline and support files come from Supabase's
[`self-hosted/v0.8.2` release](https://github.com/supabase/supabase/tree/self-hosted/v0.8.2/docker),
commit `564eab8ad7840b13324f68b1bfac074ef8d51c21`, retrieved 2026-10-08.

The copied set is `docker-compose.yml`, `.env.example`, the two key generators
under `utils`, and the gateway, database, functions, and pooler support files
under `volumes`. The upstream image versions are retained. This folder omits
upstream optional deployment overrides, update/reset tooling, and upstream tests.

## Local adaptations

- Compose project is `mobile-foundry-supabase`; ordinary fixed container names
  are removed. Realtime retains its required `realtime-dev` name prefix with
  a foundry-specific suffix.
- Host ports bind to loopback by default: API 56321, session pooler 56322,
  transaction pooler 56329, and email UI 56324. Internal PostgreSQL stays 5432.
- Database, storage, Studio snippets, database configuration, and Deno cache
  use Docker volumes so mutable runtime data stays outside source files.
- Asymmetric auth configuration is enabled. `bootstrap.sh` runs the upstream
  key generators once, keeping generated credentials in ignored `.env` files.
- Mailpit 1.31.1 captures verification and recovery mail locally. SMTP points
  to it; external email credentials and an AI API key are not needed.
- Phone sign-up is disabled and the sample functions require token verification.

## Licensing

[LICENSE.upstream](LICENSE.upstream) contains the Supabase Apache 2.0 license.
The key-generation script preserves its attribution to code derived from
Inder Singh's Apache 2.0 setup script. [LICENSE.Apache-2.0](LICENSE.Apache-2.0)
contains the license text for that attribution.

When updating this snapshot, review upstream support files and image versions
together, reapply the local adaptations, and verify configuration and startup.
