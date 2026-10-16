CREATE TABLE notes.records (
 account_id uuid NOT NULL,
 id uuid NOT NULL,
 title bytea NOT NULL,
 version bigint NOT NULL CHECK (version BETWEEN 1 AND 9007199254740991),
 PRIMARY KEY(account_id,id)
);
-- A receipt survives record deletion. No foreign key crosses module ownership.
-- The nullable result is an internal transaction claim; committed rows must have it.
CREATE TABLE notes.receipts (
 account_id uuid NOT NULL,
 principal_id uuid NOT NULL,
 command_id uuid NOT NULL,
 fingerprint bytea NOT NULL CHECK(octet_length(fingerprint)=32),
 result bytea,
 PRIMARY KEY(account_id,principal_id,command_id)
);
-- Inspect the final row at commit, after the claim has been filled in.
CREATE FUNCTION notes.require_complete_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM notes.receipts WHERE account_id=NEW.account_id
     AND principal_id=NEW.principal_id AND command_id=NEW.command_id AND result IS NULL) THEN
  RAISE EXCEPTION 'incomplete Notes receipt' USING ERRCODE='23514';
 END IF;
 RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER complete_receipt AFTER INSERT OR UPDATE ON notes.receipts
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION notes.require_complete_receipt();
