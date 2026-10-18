use kernel::{Failure, Kind};
use runtime_config::{Runtime, Storage};
use std::process::ExitCode;
#[tokio::main]
async fn main() -> ExitCode {
    std::panic::set_hook(Box::new(|_| eprintln!("runtime panic")));
    match run().await {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => {
            let public = error.public();
            eprintln!(
                "{} {} {}",
                public.kind.as_str(),
                public.code.as_deref().unwrap_or_default(),
                public.message
            );
            ExitCode::FAILURE
        }
    }
}
async fn run() -> Result<(), Failure> {
    let arguments: Vec<_> = std::env::args().skip(1).collect();
    if !arguments.is_empty() && arguments != ["--check"] {
        return Err(Failure::new(
            Kind::Invalid,
            "migrate.arguments",
            "unsupported migrator arguments",
        ));
    }
    let config = Runtime::load(|key| match std::env::var(key) {
        Ok(v) => Some(v),
        Err(std::env::VarError::NotPresent) => None,
        _ => Some("\0".into()),
    })?;
    let Storage::Postgres(db) = config.storage else {
        return Err(postgres_storage::configuration());
    };
    for schema in migration_catalog::selected(db.identity_url.is_some()) {
        let url = match schema.name {
            "account" => &db.account_url,
            "audit" => &db.audit_url,
            "notes" => &db.notes_url,
            "files" => &db.files_url,
            "notifications" => &db.notifications_url,
            "identity" => db
                .identity_url
                .as_ref()
                .ok_or_else(postgres_storage::configuration)?,
            _ => return Err(postgres_storage::configuration()),
        };
        let pool = postgres_storage::open(url, db.maximum).await?;
        let scope = MigrationScope;
        let result = tokio::time::timeout(std::time::Duration::from_secs(30), async {
            if arguments.is_empty() {
                schema.apply(&scope, &pool).await
            } else {
                schema.check(&scope, &pool).await
            }
        })
        .await
        .unwrap_or_else(|_| {
            Err(Failure::new(
                Kind::Timeout,
                "",
                "migration deadline expired",
            ))
        });
        pool.close().await;
        result?;
        println!("{} migrations verified", schema.name);
    }
    Ok(())
}
struct MigrationScope;
impl kernel::context::Scope for MigrationScope {
    fn check(&self) -> Result<(), Failure> {
        Ok(())
    }
    fn metadata(&self) -> kernel::context::Metadata {
        Default::default()
    }
}
