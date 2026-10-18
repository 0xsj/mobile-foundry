use super::*;
use crate::{
    app::push::PushLease,
    domain::installation::{InstallationSpec, Secret},
};
use events::dispatcher::Control;
use kernel::id::{Generator, RandomIds};
use std::{
    sync::atomic::{AtomicU64, Ordering},
    time::UNIX_EPOCH,
};
mod configuration;
mod protocol;
mod tls;
const REGISTERED: u64 = 1_000_000_000;
const NOW: u64 = 1_060_000_000;
struct TestClock(AtomicU64);
impl Clock for TestClock {
    fn now(&self) -> std::time::SystemTime {
        UNIX_EPOCH + Duration::from_micros(self.0.load(Ordering::SeqCst))
    }
}
fn id() -> Id {
    RandomIds.new_id().unwrap()
}
fn address(provider: &str) -> PushAddress {
    PushAddress {
        lease: PushLease {
            installation_id: id(),
            principal_id: id(),
            session_id: id(),
            version: 1,
        },
        spec: InstallationSpec {
            route: PushRoute {
                provider: provider.into(),
                application: "dev.example.app".into(),
                environment: if provider == "apns" {
                    "sandbox"
                } else {
                    "production"
                }
                .into(),
            },
            token: Secret::new(
                if provider == "apns" {
                    "000102aaff"
                } else {
                    "opaque:registration-address"
                }
                .into(),
            ),
        },
        registered_at: REGISTERED,
    }
}
#[derive(Deserialize)]
struct Corpus {
    registered_at_us: u64,
    now_us: u64,
    project: String,
    cases: Vec<Row>,
}
#[derive(Deserialize, Clone)]
struct Row {
    name: String,
    provider: String,
    status: u16,
    body: String,
    expected: String,
}
fn corpus() -> Corpus {
    serde_json::from_str(include_str!(
        "../../../../../../../contracts/fixtures/backend-push-providers/responses.json"
    ))
    .unwrap()
}
fn outcome(result: Result<PushDisposition, Failure>) -> String {
    match result {
        Ok(PushDisposition::Accepted) => "accepted".into(),
        Ok(PushDisposition::InvalidTarget) => "invalid_target".into(),
        Err(e) => {
            assert!(!format!("{e:?} {e}").contains("private-provider-details"));
            e.public().code.unwrap()
        }
    }
}
#[test]
fn shared_provider_response_admission() {
    let fixture = corpus();
    for row in fixture.cases {
        let result = match row.provider.as_str() {
            "apns" => apns::admit(
                row.status,
                row.body.as_bytes(),
                fixture.registered_at_us,
                fixture.now_us,
            ),
            "fcm" => fcm::admit(row.status, row.body.as_bytes(), &fixture.project),
            _ => panic!("unknown corpus provider"),
        };
        assert_eq!(outcome(result), row.expected, "{}", row.name);
    }
}
