CREATE TABLE notifications.installation_guard (singleton boolean PRIMARY KEY CHECK(singleton));
INSERT INTO notifications.installation_guard(singleton) VALUES(true);
CREATE TABLE notifications.installations (
    id uuid PRIMARY KEY,
    principal_id uuid NOT NULL,
    session_id uuid NOT NULL,
    registration_id uuid NOT NULL,
    proof_digest text NOT NULL CHECK(proof_digest ~ '^[0-9a-f]{64}$'),
    provider text NOT NULL CHECK(provider IN ('apns','fcm')),
    application text NOT NULL CHECK(application ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'),
    environment text NOT NULL CHECK(environment IN ('sandbox','production') AND (provider <> 'fcm' OR environment='production')),
    token text NOT NULL,
    token_key text UNIQUE CHECK(token_key IS NULL OR token_key ~ '^[0-9a-f]{64}$'),
    version bigint NOT NULL CHECK(version BETWEEN 1 AND 9007199254740991),
    enabled boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CHECK((enabled AND token_key IS NOT NULL AND octet_length(token) BETWEEN 1 AND 4096) OR (NOT enabled AND token_key IS NULL AND token=''))
);
CREATE INDEX installations_session ON notifications.installations(principal_id,session_id);
CREATE TABLE notifications.revoked_installation_sessions (
    principal_id uuid NOT NULL,
    session_id uuid NOT NULL,
    PRIMARY KEY(principal_id,session_id)
);
CREATE TABLE notifications.installation_inbox (
    event_id uuid PRIMARY KEY,
    fingerprint text NOT NULL CHECK(fingerprint ~ '^[0-9a-f]{64}$')
);
