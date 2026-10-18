-- Extend the owned evidence model; preserve applied 0001 and existing fingerprints.
ALTER TABLE audit.entries ADD COLUMN principal_id uuid;
ALTER TABLE audit.entries ADD COLUMN session_id uuid;
ALTER TABLE audit.entries ADD COLUMN reason text NOT NULL DEFAULT '';
ALTER TABLE audit.entries ALTER COLUMN account_id DROP NOT NULL;
ALTER TABLE audit.entries DROP CONSTRAINT entries_action_check;
ALTER TABLE audit.entries DROP CONSTRAINT entries_check;
ALTER TABLE audit.entries ADD CONSTRAINT entries_scope_action_check CHECK (
    (account_id IS NOT NULL AND principal_id IS NULL AND session_id IS NULL AND reason = '' AND
        ((action = 'account.created' AND version = 1) OR (action = 'account.renamed' AND version >= 2)))
    OR
    (account_id IS NULL AND principal_id IS NOT NULL AND actor_id = principal_id AND
        ((action = 'identity.principal.registered' AND session_id IS NULL AND version = 1 AND reason = '')
         OR (action = 'identity.session.issued' AND session_id IS NOT NULL AND version = 1 AND reason = '')
         OR (action = 'identity.session.renewed' AND session_id IS NOT NULL AND version >= 2 AND reason = '')
         OR (action = 'identity.session.revoked' AND session_id IS NOT NULL AND version >= 2 AND reason IN ('sign_out','refresh_reuse'))))
);
CREATE INDEX entries_principal_sequence ON audit.entries(principal_id, sequence) WHERE principal_id IS NOT NULL;
