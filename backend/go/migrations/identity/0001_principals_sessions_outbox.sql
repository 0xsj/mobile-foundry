CREATE TABLE identity.principals (
    id uuid PRIMARY KEY,
    login text UNIQUE NOT NULL CHECK (login ~ '^[a-z0-9][a-z0-9._@+-]{2,127}$'),
    display_name text NOT NULL CHECK (char_length(display_name) BETWEEN 1 AND 80),
    password_hash text NOT NULL CHECK (length(password_hash) BETWEEN 1 AND 512),
    created_at timestamptz NOT NULL
);
CREATE TABLE identity.sessions (
    id uuid PRIMARY KEY,
    principal_id uuid NOT NULL REFERENCES identity.principals(id),
    generation bigint NOT NULL CHECK (generation BETWEEN 1 AND 9007199254740991),
    access_digest text UNIQUE NOT NULL CHECK (access_digest ~ '^[a-f0-9]{64}$'),
    refresh_digest text UNIQUE NOT NULL CHECK (refresh_digest ~ '^[a-f0-9]{64}$'),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at >= created_at),
    access_expires_at timestamptz NOT NULL CHECK (access_expires_at > created_at),
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at AND access_expires_at <= expires_at),
    revoked_at timestamptz,
    CHECK (revoked_at IS NULL OR revoked_at = updated_at)
);
CREATE TABLE identity.refresh_credentials (
    digest text PRIMARY KEY CHECK (digest ~ '^[a-f0-9]{64}$'),
    session_id uuid NOT NULL REFERENCES identity.sessions(id),
    generation bigint NOT NULL CHECK (generation BETWEEN 1 AND 9007199254740991),
    UNIQUE (session_id, generation)
);
CREATE TABLE identity.outbox (
    sequence bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id uuid UNIQUE NOT NULL,
    stream_id uuid NOT NULL,
    aggregate_version bigint NOT NULL CHECK (aggregate_version BETWEEN 1 AND 9007199254740991),
    envelope jsonb NOT NULL CHECK (jsonb_typeof(envelope) = 'object'),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    failure_code text NOT NULL DEFAULT '',
    dead boolean NOT NULL DEFAULT false,
    delivered_at timestamptz,
    lease_owner uuid,
    lease_until timestamptz,
    CHECK ((lease_owner IS NULL) = (lease_until IS NULL)),
    UNIQUE (stream_id, aggregate_version)
);
CREATE INDEX outbox_pending ON identity.outbox(sequence) WHERE delivered_at IS NULL AND NOT dead;
CREATE INDEX outbox_stream_pending ON identity.outbox(stream_id, sequence) WHERE delivered_at IS NULL;
