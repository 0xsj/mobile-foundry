ALTER TABLE audit.entries DROP CONSTRAINT entries_scope_action_check;
ALTER TABLE audit.entries ADD CONSTRAINT entries_scope_action_check CHECK (
    (file_id IS NULL AND account_id IS NOT NULL AND principal_id IS NULL AND session_id IS NULL AND reason = '' AND
        ((action = 'account.created' AND version = 1) OR (action = 'account.renamed' AND version >= 2)))
    OR
    (file_id IS NULL AND account_id IS NULL AND principal_id IS NOT NULL AND actor_id = principal_id AND
        ((action = 'identity.principal.registered' AND session_id IS NULL AND version = 1 AND reason = '')
         OR (action IN ('identity.email.verified','identity.password.changed','identity.password.recovered') AND session_id IS NULL AND version>=2 AND reason='')
         OR (action = 'identity.session.issued' AND session_id IS NOT NULL AND version = 1 AND reason = '')
         OR (action = 'identity.session.renewed' AND session_id IS NOT NULL AND version >= 2 AND reason = '')
         OR (action = 'identity.session.revoked' AND session_id IS NOT NULL AND version >= 2 AND reason IN ('sign_out','refresh_reuse','password_change','password_reset'))))
 OR (account_id IS NOT NULL AND file_id IS NOT NULL AND principal_id IS NULL AND session_id IS NULL AND reason='' AND ((action='files.upload_requested' AND version=1) OR (action='files.upload_completed' AND version=2) OR (action='files.deleted' AND version IN (2,3))))
);
