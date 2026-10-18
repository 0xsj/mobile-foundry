-- All relationships remain inside Account; no Identity schema dependency.
ALTER TABLE account.accounts ADD CONSTRAINT accounts_id_owner UNIQUE (id, owner_id);
CREATE TABLE account.personal_accounts (
    owner_id uuid PRIMARY KEY,
    account_id uuid UNIQUE NOT NULL,
    CONSTRAINT personal_account_owner FOREIGN KEY (account_id, owner_id)
        REFERENCES account.accounts(id, owner_id) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX accounts_owner_page ON account.accounts(owner_id, id);
