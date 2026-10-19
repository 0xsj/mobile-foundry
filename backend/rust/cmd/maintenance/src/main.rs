//! Explicit owner-specific challenge pruning; no implicit retention schedule.
use kernel::{
    Failure, Kind,
    context::{Metadata, Scope},
};
use runtime_config::{Authentication, Runtime, Storage};
use std::{
    process::ExitCode,
    time::{Duration, SystemTime},
};
fn arguments(args: &[String]) -> Result<usize, Failure> {
    let limit = match args {
        [] => 128,
        [flag, value] if flag == "--limit" => value.parse().unwrap_or(0),
        _ => 0,
    };
    if !(1..=1000).contains(&limit) {
        Err(Failure::new(
            Kind::Invalid,
            "maintenance.limit",
            "invalid maintenance arguments",
        ))
    } else {
        Ok(limit)
    }
}
#[tokio::main]
async fn main() -> ExitCode {
    std::panic::set_hook(Box::new(|_| eprintln!("runtime panic")));
    match run().await {
        Ok(n) => {
            println!("Removed {n} expired identity challenges");
            ExitCode::SUCCESS
        }
        Err(e) => {
            let p = e.public();
            eprintln!(
                "{} {} {}",
                p.kind.as_str(),
                p.code.as_deref().unwrap_or_default(),
                p.message
            );
            ExitCode::FAILURE
        }
    }
}
async fn run() -> Result<usize, Failure> {
    let limit = arguments(&std::env::args().skip(1).collect::<Vec<_>>())?;
    let config = Runtime::load(|k| match std::env::var(k) {
        Ok(v) => Some(v),
        Err(std::env::VarError::NotPresent) => None,
        _ => Some("\0".into()),
    })?;
    let Storage::Postgres(db) = config.storage else {
        return Err(postgres_storage::configuration());
    };
    if config.authentication != Authentication::Identity {
        return Err(postgres_storage::configuration());
    }
    let pool = postgres_storage::open(
        db.identity_url
            .as_deref()
            .ok_or_else(postgres_storage::configuration)?,
        db.maximum,
    )
    .await?;
    let scope = Control {
        deadline: tokio::time::Instant::now() + Duration::from_secs(15),
    };
    let result = tokio::time::timeout_at(scope.deadline, async {
        let schema = migration_catalog::selected(true)
            .into_iter()
            .find(|s| s.name == "identity")
            .ok_or_else(postgres_storage::configuration)?;
        schema.check(&scope, &pool).await?;
        identity::prune_expired(&scope, pool.clone(), SystemTime::now(), limit).await
    })
    .await
    .unwrap_or_else(|_| {
        Err(Failure::new(
            Kind::Timeout,
            "",
            "maintenance deadline elapsed",
        ))
    });
    pool.close().await;
    result
}
struct Control {
    deadline: tokio::time::Instant,
}
impl Scope for Control {
    fn check(&self) -> Result<(), Failure> {
        if tokio::time::Instant::now() >= self.deadline {
            Err(Failure::new(
                Kind::Timeout,
                "",
                "maintenance deadline elapsed",
            ))
        } else {
            Ok(())
        }
    }
    fn metadata(&self) -> Metadata {
        Metadata::default()
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn bounded_explicit_arguments() {
        assert_eq!(arguments(&[]).unwrap(), 128);
        for value in ["1", "128", "1000"] {
            assert!(arguments(&["--limit".into(), value.into()]).is_ok());
        }
        for value in ["0", "1001", "-1", "PRIVATE"] {
            assert!(arguments(&["--limit".into(), value.into()]).is_err());
        }
        assert!(arguments(&["--unknown".into()]).is_err());
    }
}
