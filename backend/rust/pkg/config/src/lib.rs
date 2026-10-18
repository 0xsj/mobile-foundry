//! One validated startup snapshot. Reusable modules never read process environment.
use kernel::{Failure, Kind};
use std::{fmt, net::SocketAddr, time::Duration};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum LogFormat {
    Console,
    Json,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub enum LogLevel {
    Debug,
    Info,
    Warn,
    Error,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Authentication {
    Deny,
    Identity,
}
#[derive(Clone)]
pub enum Storage {
    Memory,
    Postgres(Database),
}
#[derive(Clone)]
pub struct Database {
    pub account_url: String,
    pub audit_url: String,
    pub notes_url: String,
    pub files_url: String,
    pub notifications_url: String,
    pub files_directory: String,
    pub identity_url: Option<String>,
    pub maximum: u32,
}
impl fmt::Debug for Storage {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Storage { redacted }")
    }
}
impl fmt::Debug for Database {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Database { redacted }")
    }
}

#[derive(Clone)]
pub struct Runtime {
    pub environment: String,
    pub address: SocketAddr,
    pub log_format: LogFormat,
    pub log_level: LogLevel,
    pub shutdown_timeout: Duration,
    pub authentication: Authentication,
    pub storage: Storage,
    pub mail_config: Option<String>,
    pub push_config: Option<String>,
    pub push_jobs: bool,
}
impl fmt::Debug for Runtime {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Runtime { redacted }")
    }
}

fn invalid() -> Failure {
    Failure::new(
        Kind::Invalid,
        "config.invalid",
        "invalid or unsupported runtime configuration",
    )
}

impl Runtime {
    /// Absent values default; present empty values are validated, never replaced.
    pub fn load(source: impl Fn(&str) -> Option<String>) -> Result<Self, Failure> {
        let value = |key, fallback: &str| source(key).unwrap_or_else(|| fallback.into());
        let environment = value("APP_ENV", "development");
        if !["development", "test", "production"].contains(&environment.as_str()) {
            return Err(invalid());
        }
        // Literal socket addresses deliberately avoid DNS at startup in this first profile.
        let address: SocketAddr = value("HTTP_ADDR", "127.0.0.1:8080")
            .parse()
            .map_err(|_| invalid())?;
        if address.port() == 0 {
            return Err(invalid());
        }
        let log_format = match value("LOG_FORMAT", "console").as_str() {
            "console" => LogFormat::Console,
            "json" => LogFormat::Json,
            _ => return Err(invalid()),
        };
        let log_level = match value("LOG_LEVEL", "info").as_str() {
            "debug" => LogLevel::Debug,
            "info" => LogLevel::Info,
            "warn" => LogLevel::Warn,
            "error" => LogLevel::Error,
            _ => return Err(invalid()),
        };
        let selection = value("STORAGE", "memory");
        let maximum: u32 = value("POSTGRES_POOL_MAX_CONNS", "8")
            .parse()
            .map_err(|_| invalid())?;
        if !(1..=128).contains(&maximum) {
            return Err(invalid());
        }
        let authentication = match value("AUTH_MODE", "deny").as_str() {
            "deny" => Authentication::Deny,
            "identity" => Authentication::Identity,
            _ => return Err(invalid()),
        };
        let mail_config = source("IDENTITY_MAIL_CONFIG").filter(|p| !p.is_empty());
        let push_config = source("PUSH_CONFIG_FILE").filter(|p| !p.is_empty());
        if [&mail_config, &push_config]
            .iter()
            .any(|p| p.as_ref().is_some_and(|p| p.contains('\0')))
            || ((mail_config.is_some() || push_config.is_some())
                && authentication != Authentication::Identity)
        {
            return Err(invalid());
        }
        let storage = match selection.as_str() {
            "memory" => Storage::Memory,
            "postgres" => {
                let common = value("DATABASE_URL", "");
                let account_url = value("ACCOUNT_DATABASE_URL", &common);
                let audit_url = value("AUDIT_DATABASE_URL", &common);
                let notes_url = value("NOTES_DATABASE_URL", &common);
                let files_url = value("FILES_DATABASE_URL", &common);
                let notifications_url = value("NOTIFICATIONS_DATABASE_URL", &common);
                let files_directory = value("FILES_DIRECTORY", ".data/files");
                let identity = value("IDENTITY_DATABASE_URL", &common);
                if notes_url.is_empty()
                    || files_url.is_empty()
                    || notifications_url.is_empty()
                    || files_directory.is_empty()
                    || files_directory.contains('\0')
                    || account_url.is_empty()
                    || audit_url.is_empty()
                    || (authentication == Authentication::Identity && identity.is_empty())
                {
                    return Err(invalid());
                }
                Storage::Postgres(Database {
                    account_url,
                    audit_url,
                    notes_url,
                    files_url,
                    notifications_url,
                    files_directory,
                    identity_url: (!identity.is_empty()).then_some(identity),
                    maximum,
                })
            }
            _ => return Err(invalid()),
        };
        // Fail closed for selections not implemented by this Rust profile.
        for key in [
            "DEV_AUTH_TOKEN",
            "DEV_PRINCIPAL_ID",
            "METRICS_ADDR",
            "TRUSTED_PROXY_CIDRS",
        ] {
            if source(key).is_some_and(|v| !v.is_empty()) {
                return Err(invalid());
            }
        }
        let push_jobs = match value("PUSH_JOBS_ENABLED", "false").as_str() {
            "false" => false,
            "true" => true,
            _ => return Err(invalid()),
        };
        if push_jobs
            && (authentication != Authentication::Identity
                || push_config.is_none()
                || !matches!(storage, Storage::Postgres(_)))
        {
            return Err(invalid());
        }
        Ok(Self {
            environment,
            address,
            log_format,
            log_level,
            shutdown_timeout: Duration::from_secs(10),
            authentication,
            storage,
            mail_config,
            push_config,
            push_jobs,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn jobs_are_an_independent_explicit_durable_selection() {
        let selected = |k: &str| match k {
            "PUSH_JOBS_ENABLED" => Some("true".into()),
            "STORAGE" => Some("postgres".into()),
            "AUTH_MODE" => Some("identity".into()),
            "PUSH_CONFIG_FILE" => Some("/private/push.json".into()),
            "DATABASE_URL" => Some("postgres://PRIVATE/owned".into()),
            _ => None,
        };
        assert!(Runtime::load(selected).unwrap().push_jobs);
        for (key, value) in [
            ("STORAGE", "memory"),
            ("AUTH_MODE", "deny"),
            ("PUSH_CONFIG_FILE", ""),
            ("PUSH_JOBS_ENABLED", ""),
            ("PUSH_JOBS_ENABLED", "TRUE"),
        ] {
            assert!(
                Runtime::load(|k| if k == key {
                    Some(value.into())
                } else {
                    selected(k)
                })
                .is_err()
            );
        }
        assert!(!Runtime::load(|_| None).unwrap().push_jobs);
    }
    #[test]
    fn push_selection_requires_identity_and_remains_private() {
        let config = Runtime::load(|k| match k {
            "AUTH_MODE" => Some("identity".into()),
            "PUSH_CONFIG_FILE" => Some("/private/PUSH-SECRET.json".into()),
            _ => None,
        })
        .unwrap();
        assert_eq!(
            config.push_config.as_deref(),
            Some("/private/PUSH-SECRET.json")
        );
        assert!(!format!("{config:?}").contains("PUSH-SECRET"));
        assert!(
            Runtime::load(|k| match k {
                "AUTH_MODE" => Some("identity".into()),
                "PUSH_CONFIG_FILE" => Some("bad\0path".into()),
                _ => None,
            })
            .is_err()
        );
        assert!(
            Runtime::load(|k| (k == "PUSH_CONFIG_FILE").then(String::new))
                .unwrap()
                .push_config
                .is_none()
        );
    }
    #[test]
    fn mail_requires_owned_identity_and_keeps_configuration_private() {
        let selected = |key: &str| match key {
            "AUTH_MODE" => Some("identity".into()),
            "IDENTITY_MAIL_CONFIG" => Some("/private/SMTP-SECRET.json".into()),
            _ => None,
        };
        let config = Runtime::load(selected).unwrap();
        assert_eq!(
            config.mail_config.as_deref(),
            Some("/private/SMTP-SECRET.json")
        );
        assert!(!format!("{config:?}").contains("SMTP-SECRET"));
        assert!(
            Runtime::load(|key| {
                (key == "IDENTITY_MAIL_CONFIG").then(|| "/private/SMTP-SECRET.json".into())
            })
            .is_err()
        );
        assert!(
            Runtime::load(|key| {
                if key == "IDENTITY_MAIL_CONFIG" {
                    Some("invalid\0path".into())
                } else {
                    selected(key)
                }
            })
            .is_err()
        );
        assert!(
            Runtime::load(|key| { (key == "IDENTITY_MAIL_CONFIG").then(String::new) })
                .unwrap()
                .mail_config
                .is_none()
        );
    }
    #[test]
    fn defaults_empty_invalid_and_unsupported_selections() {
        assert_eq!(
            Runtime::load(|_| None).unwrap().address.to_string(),
            "127.0.0.1:8080"
        );
        for (key, value) in [
            ("APP_ENV", ""),
            ("HTTP_ADDR", "127.0.0.1:0"),
            ("LOG_LEVEL", "verbose"),
            ("LOG_FORMAT", "yaml"),
            ("AUTH_MODE", "development"),
            ("STORAGE", "postgres"),
            ("PUSH_CONFIG_FILE", "private-secret"),
            ("PUSH_JOBS_ENABLED", "true"),
        ] {
            let error = Runtime::load(|k| (k == key).then(|| value.into())).unwrap_err();
            assert_eq!(error.kind(), Kind::Invalid);
            assert!(!format!("{error:?}").contains("private-secret"));
        }
        let ipv6 = Runtime::load(|k| (k == "HTTP_ADDR").then(|| "[::1]:8081".into())).unwrap();
        assert!(ipv6.address.is_ipv6());
        assert_eq!(format!("{ipv6:?}"), "Runtime { redacted }");
    }
    #[test]
    fn postgres_selection_fallback_explicit_empty_and_secrets() {
        let source = |key: &str| match key {
            "STORAGE" => Some("postgres".into()),
            "DATABASE_URL" => Some("postgres://SECRET/owned".into()),
            _ => None,
        };
        let config = Runtime::load(source).unwrap();
        let Storage::Postgres(db) = config.storage else {
            panic!("database selection missing")
        };
        assert_eq!(db.maximum, 8);
        assert_eq!(db.account_url, "postgres://SECRET/owned");
        assert_eq!(db.notes_url, "postgres://SECRET/owned");
        assert!(!format!("{db:?}").contains("SECRET"));
        for module in [
            "ACCOUNT_DATABASE_URL",
            "AUDIT_DATABASE_URL",
            "NOTES_DATABASE_URL",
            "FILES_DATABASE_URL",
            "NOTIFICATIONS_DATABASE_URL",
            "FILES_DIRECTORY",
        ] {
            assert!(
                Runtime::load(|key| if key == module {
                    Some(String::new())
                } else {
                    source(key)
                })
                .is_err()
            );
        }
        for number in ["0", "129", "invalid"] {
            assert!(
                Runtime::load(|key| if key == "POSTGRES_POOL_MAX_CONNS" {
                    Some(number.into())
                } else {
                    source(key)
                })
                .is_err()
            );
        }
    }
}
