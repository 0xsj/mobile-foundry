# Local Supabase profile

This folder runs the real self-hosted Supabase services for development using
a pinned upstream Compose release. It includes Postgres, Auth, REST, Realtime,
Storage, Studio, Edge Functions, the pooler, and local email capture. See
[upstream provenance](UPSTREAM.md) for versions and local adaptations.

## Start and stop

Docker, Docker Compose, OpenSSL, and Node.js 16 or newer are prerequisites.
From this directory:

```sh
cd /Users/sj/Desktop/dev/builds/mobile-foundry/backend/supabase
make up
make status
```

The first setup creates `.env` with generated credentials. Subsequent setup
retains it. Studio's username and generated dashboard password are in `.env`;
the native client configuration uses the publishable key and API URL from that
file. Server-only keys stay in trusted server configuration.

```sh
make logs
make down
```

Stopping retains Docker data volumes. Credential changes after database
initialization need the upstream rotation procedure rather than regenerating
`.env` against an existing database.

## Local endpoints

| Service | Host endpoint |
| --- | --- |
| API and Studio | `http://localhost:56321` |
| Auth API | `http://localhost:56321/auth/v1` |
| REST API | `http://localhost:56321/rest/v1` |
| Storage API | `http://localhost:56321/storage/v1` |
| PostgreSQL session pooler | `localhost:56322` |
| PostgreSQL transaction pooler | `localhost:56329` |
| Captured email | `http://localhost:56324` |

An iOS simulator can use localhost. The Android emulator reaches the host via
`10.0.2.2`. Physical devices need a reachable host address and an explicit bind
override; update public and Auth callback URLs for that environment too.

This Compose profile uses Supabase's self-hosted configuration rather than
the CLI's `supabase start` project format. Future schema migrations, policies,
and native adapters should be added with their connected workflow. The current
provider infrastructure does not define a foundry account or sync contract.

Read [the setup notes](notes/README.md) and the official
[Docker guide](https://supabase.com/docs/guides/self-hosting/docker).
