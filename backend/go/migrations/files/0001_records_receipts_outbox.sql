CREATE TABLE files.records (
 id uuid PRIMARY KEY, account_id uuid NOT NULL,
 status text NOT NULL CHECK(status IN ('pending_upload','available','deleted')),
 version bigint NOT NULL CHECK(version BETWEEN 1 AND 3),
 snapshot jsonb NOT NULL CHECK(jsonb_typeof(snapshot)='object'),
 CHECK((status='pending_upload' AND version=1) OR (status='available' AND version=2) OR (status='deleted' AND version IN (2,3)))
);
CREATE INDEX files_account_list ON files.records(account_id,id) WHERE status<>'deleted';
CREATE TABLE files.receipts (
 account_id uuid NOT NULL, principal_id uuid NOT NULL, key text NOT NULL CHECK(key ~ '^[A-Za-z0-9._:-]{1,128}$'),
 fingerprint bytea NOT NULL CHECK(octet_length(fingerprint)=32), result jsonb CHECK(result IS NULL OR jsonb_typeof(result)='object'),
 PRIMARY KEY(account_id,principal_id,key)
);
CREATE FUNCTION files.require_final_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM files.receipts WHERE account_id=NEW.account_id AND principal_id=NEW.principal_id AND key=NEW.key AND result IS NULL) THEN
 RAISE EXCEPTION 'incomplete files receipt' USING ERRCODE='23514';
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER complete_receipt AFTER INSERT OR UPDATE ON files.receipts DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION files.require_final_receipt();
CREATE TABLE files.outbox (
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
CREATE INDEX outbox_pending ON files.outbox(sequence) WHERE delivered_at IS NULL AND NOT dead;
CREATE INDEX outbox_stream_pending ON files.outbox(stream_id, sequence) WHERE delivered_at IS NULL;
