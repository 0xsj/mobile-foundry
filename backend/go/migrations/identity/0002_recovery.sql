ALTER TABLE identity.principals ADD COLUMN revision bigint NOT NULL DEFAULT 1 CHECK (revision BETWEEN 1 AND 9007199254740991);
CREATE TABLE identity.contacts (
 principal_id uuid PRIMARY KEY REFERENCES identity.principals(id),
 email text NOT NULL UNIQUE CHECK (length(email) BETWEEN 3 AND 254 AND email=lower(email)),
 verified_at timestamptz NOT NULL
);
CREATE TABLE identity.challenges (
 principal_id uuid NOT NULL REFERENCES identity.principals(id),
 purpose text NOT NULL CHECK (purpose IN ('verify_email','reset_password')),
 email text NOT NULL CHECK (length(email) BETWEEN 3 AND 254),
 digest text NOT NULL UNIQUE CHECK (digest ~ '^[a-f0-9]{64}$'),
 credential_hash text NOT NULL CHECK (length(credential_hash) BETWEEN 1 AND 512),
 created_at timestamptz NOT NULL,
 expires_at timestamptz NOT NULL CHECK (expires_at=created_at+interval '20 minutes'),
 PRIMARY KEY(principal_id,purpose)
);
CREATE INDEX challenges_expiry ON identity.challenges(expires_at);
