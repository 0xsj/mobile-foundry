CREATE TABLE notifications.push_job_guard(singleton boolean PRIMARY KEY CHECK(singleton));
INSERT INTO notifications.push_job_guard(singleton) VALUES(true);
CREATE TABLE notifications.push_jobs (
    id uuid PRIMARY KEY,
    installation_id uuid NOT NULL,
    principal_id uuid NOT NULL,
    session_id uuid NOT NULL,
    installation_version bigint NOT NULL CHECK(installation_version BETWEEN 1 AND 9007199254740991),
    status text NOT NULL CHECK(status IN ('queued','leased','sending','accepted','invalid_target','blocked','failed','uncertain','expired')),
    attempts integer NOT NULL CHECK(attempts BETWEEN 0 AND 5),
    claim uuid,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK(updated_at>=created_at),
    expires_at timestamptz NOT NULL CHECK(expires_at=created_at+interval '15 minutes'),
    next_at timestamptz NOT NULL CHECK(next_at>=created_at),
    lease_until timestamptz,
    code text NOT NULL CHECK(code IN ('','preflight_unavailable','attempts_exhausted','target_ineligible','provider_unconfirmed','worker_lost','cleanup_unconfirmed','expired')),
    retired boolean NOT NULL CHECK(NOT retired OR status='invalid_target'),
    CHECK((status IN ('leased','sending') AND claim IS NOT NULL AND lease_until IS NOT NULL AND attempts>0) OR (status NOT IN ('leased','sending') AND claim IS NULL AND lease_until IS NULL))
);
CREATE INDEX push_jobs_due ON notifications.push_jobs(status,next_at,lease_until);
