ALTER TABLE notifications.push_jobs DROP CONSTRAINT push_jobs_status_check;
ALTER TABLE notifications.push_jobs ADD CONSTRAINT push_jobs_status_check
    CHECK(status IN ('queued','leased','sending','accepted','invalid_target','blocked','failed','uncertain','expired','canceled'));
ALTER TABLE notifications.push_jobs DROP CONSTRAINT push_jobs_code_check;
ALTER TABLE notifications.push_jobs ADD CONSTRAINT push_jobs_code_check
    CHECK(code IN ('','preflight_unavailable','attempts_exhausted','target_ineligible','provider_unconfirmed','worker_lost','cleanup_unconfirmed','expired','user_canceled'));
ALTER TABLE notifications.push_jobs ADD COLUMN retry_of uuid REFERENCES notifications.push_jobs(id);
ALTER TABLE notifications.push_jobs ADD COLUMN duplicate_risk_accepted boolean NOT NULL DEFAULT false;
ALTER TABLE notifications.push_jobs ADD CONSTRAINT push_jobs_replacement_shape
    CHECK((retry_of IS NULL AND NOT duplicate_risk_accepted) OR (retry_of IS NOT NULL AND retry_of<>id));
CREATE UNIQUE INDEX push_jobs_one_replacement ON notifications.push_jobs(retry_of) WHERE retry_of IS NOT NULL;
