CREATE TABLE notifications.scope_state (
    account_id uuid NOT NULL,
    principal_id uuid NOT NULL,
    media_enabled boolean NOT NULL DEFAULT true,
    next_sequence bigint NOT NULL DEFAULT 1 CHECK (next_sequence BETWEEN 1 AND 9007199254740991),
    PRIMARY KEY (account_id, principal_id)
);
CREATE TABLE notifications.inbox (
    consumer text NOT NULL CHECK (consumer = 'notifications.files'),
    event_id uuid NOT NULL,
    fingerprint text NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    PRIMARY KEY (consumer,event_id)
);
CREATE TABLE notifications.items (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL,
    principal_id uuid NOT NULL,
    file_id uuid NOT NULL,
    sequence bigint NOT NULL CHECK (sequence BETWEEN 1 AND 9007199254740991),
    type text NOT NULL CHECK (type = 'files.upload_ready'),
    category text NOT NULL CHECK (category = 'media'),
    occurred_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    read_at timestamptz,
    UNIQUE (account_id, principal_id, sequence)
);
CREATE INDEX items_unread ON notifications.items(account_id,principal_id,sequence DESC) WHERE read_at IS NULL;
