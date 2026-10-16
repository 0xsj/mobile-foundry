CREATE TABLE account.accounts (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL,
    name text NOT NULL CHECK (char_length(name) BETWEEN 1 AND 80),
    version bigint NOT NULL CHECK (version BETWEEN 1 AND 9007199254740991),
    created_at timestamptz NOT NULL CHECK (created_at >= '1970-01-01' AND created_at < '10000-01-01'),
    updated_at timestamptz NOT NULL CHECK (updated_at >= created_at AND updated_at < '10000-01-01')
);
CREATE TABLE account.outbox (
    sequence bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id uuid UNIQUE NOT NULL,
    account_id uuid NOT NULL,
    aggregate_version bigint NOT NULL CHECK (aggregate_version BETWEEN 1 AND 9007199254740991),
    envelope jsonb NOT NULL CHECK (jsonb_typeof(envelope) = 'object'),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    failure_code text NOT NULL DEFAULT '',
    dead boolean NOT NULL DEFAULT false,
    delivered_at timestamptz,
    lease_owner uuid,
    lease_until timestamptz,
    CHECK ((lease_owner IS NULL) = (lease_until IS NULL)),
    UNIQUE (account_id, aggregate_version)
);
CREATE INDEX outbox_pending ON account.outbox(sequence) WHERE delivered_at IS NULL AND NOT dead;
CREATE INDEX outbox_account_pending ON account.outbox(account_id, sequence) WHERE delivered_at IS NULL;
