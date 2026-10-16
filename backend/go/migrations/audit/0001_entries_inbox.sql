-- A row lock serializes sequence allocation through COMMIT. A PostgreSQL
-- sequence alone can commit out of order and make a cursor skip late evidence.
CREATE TABLE audit.append_state (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    next_sequence bigint NOT NULL CHECK (next_sequence BETWEEN 1 AND 9007199254740991)
);
INSERT INTO audit.append_state VALUES (true, 1);
CREATE TABLE audit.entries (
    sequence bigint PRIMARY KEY CHECK (sequence BETWEEN 1 AND 9007199254740991),
    event_id uuid NOT NULL,
    account_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    action text NOT NULL CHECK (action IN ('account.created', 'account.renamed')),
    version bigint NOT NULL CHECK (version BETWEEN 1 AND 9007199254740991),
    occurred_at timestamptz NOT NULL CHECK (occurred_at >= '1970-01-01' AND occurred_at < '10000-01-01'),
    recorded_at timestamptz NOT NULL CHECK (recorded_at >= '1970-01-01' AND recorded_at < '10000-01-01'),
    request_id text NOT NULL,
    correlation_id text NOT NULL,
    CHECK ((action = 'account.created' AND version = 1) OR (action = 'account.renamed' AND version >= 2))
);
CREATE INDEX entries_account_sequence ON audit.entries(account_id, sequence);
CREATE TABLE audit.inbox (
    consumer text NOT NULL CHECK (consumer <> ''),
    event_id uuid NOT NULL,
    evidence_sequence bigint NOT NULL REFERENCES audit.entries(sequence),
    fingerprint text NOT NULL,
    PRIMARY KEY (consumer, event_id)
);
