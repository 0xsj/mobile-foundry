//! Immutable deployment artifacts. Only process composition imports this catalog.
use postgres_storage::migrations::{Migration, Schema};
pub static ACCOUNT: Schema = Schema {
    name: "account",
    migrations: &[
        Migration {
            name: "0001_accounts_outbox.sql",
            sql: include_str!("../account/0001_accounts_outbox.sql"),
        },
        Migration {
            name: "0002_personal_accounts.sql",
            sql: include_str!("../account/0002_personal_accounts.sql"),
        },
    ],
};
pub static AUDIT: Schema = Schema {
    name: "audit",
    migrations: &[
        Migration {
            name: "0001_entries_inbox.sql",
            sql: include_str!("../audit/0001_entries_inbox.sql"),
        },
        Migration {
            name: "0002_principal_evidence.sql",
            sql: include_str!("../audit/0002_principal_evidence.sql"),
        },
        Migration {
            name: "0003_file_evidence.sql",
            sql: include_str!("../audit/0003_file_evidence.sql"),
        },
        Migration {
            name: "0004_identity_recovery.sql",
            sql: include_str!("../audit/0004_identity_recovery.sql"),
        },
    ],
};
pub static IDENTITY: Schema = Schema {
    name: "identity",
    migrations: &[
        Migration {
            name: "0001_principals_sessions_outbox.sql",
            sql: include_str!("../identity/0001_principals_sessions_outbox.sql"),
        },
        Migration {
            name: "0002_recovery.sql",
            sql: include_str!("../identity/0002_recovery.sql"),
        },
    ],
};

pub static NOTES: Schema = Schema {
    name: "notes",
    migrations: &[Migration {
        name: "0001_notes_receipts.sql",
        sql: include_str!("../notes/0001_notes_receipts.sql"),
    }],
};

pub fn selected(identity: bool) -> Vec<&'static Schema> {
    let mut schemas = vec![&ACCOUNT, &AUDIT, &NOTES, &FILES, &NOTIFICATIONS];
    if identity {
        schemas.push(&IDENTITY);
    }
    schemas
}

pub static FILES: Schema = Schema {
    name: "files",
    migrations: &[Migration {
        name: "0001_records_receipts_outbox.sql",
        sql: include_str!("../files/0001_records_receipts_outbox.sql"),
    }],
};

pub static NOTIFICATIONS: Schema = Schema {
    name: "notifications",
    migrations: &[
        Migration {
            name: "0001_inbox_preferences.sql",
            sql: include_str!("../notifications/0001_inbox_preferences.sql"),
        },
        Migration {
            name: "0002_installations.sql",
            sql: include_str!("../notifications/0002_installations.sql"),
        },
        Migration {
            name: "0003_push_jobs.sql",
            sql: include_str!("../notifications/0003_push_jobs.sql"),
        },
        Migration {
            name: "0004_push_job_recovery.sql",
            sql: include_str!("../notifications/0004_push_job_recovery.sql"),
        },
    ],
};
