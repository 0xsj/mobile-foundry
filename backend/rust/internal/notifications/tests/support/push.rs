use events::dispatcher::Control;
use kernel::{
    Failure, Kind,
    clock::Clock,
    context::{PortFuture, Scope},
    id::{Generator, Id, RandomIds},
};
use notifications::{
    app::{
        command::send_push_preview::SendPushPreview,
        installations::{Actor, Installations, Mutation, Revocation},
        push::{
            PushAddress, PushAttempt, PushDisposition, PushGateway, PushLease, PushTargets,
            SessionEligibility,
        },
    },
    domain::installation::{InstallationSpec, PushRoute, Secret},
};
use serde_json::Value;
use std::{
    sync::{
        Arc,
        atomic::{AtomicU64, AtomicUsize, Ordering},
    },
    time::{Duration, SystemTime, UNIX_EPOCH},
};
pub const AT: u64 = 1_800_000_000_000_000;
pub fn id() -> Id {
    RandomIds.new_id().unwrap()
}
pub struct TestClock(pub AtomicU64);
impl Clock for TestClock {
    fn now(&self) -> SystemTime {
        UNIX_EPOCH + Duration::from_micros(self.0.load(Ordering::Relaxed))
    }
}
pub fn mutation() -> Mutation {
    Mutation {
        actor: Actor {
            principal: id(),
            session: id(),
            expires_at: AT + 3_600_000_000,
        },
        id: id(),
        registration_id: id(),
        proof_digest: Secret::new("a".repeat(64)),
        spec: InstallationSpec {
            route: PushRoute {
                provider: "apns".into(),
                application: "dev.example.mobile".into(),
                environment: "sandbox".into(),
            },
            token: Secret::new(format!("PRIVATE-{}", id())),
        },
        expected: 0,
    }
}
pub fn lease(m: &Mutation) -> PushLease {
    PushLease {
        installation_id: m.id.clone(),
        principal_id: m.actor.principal.clone(),
        session_id: m.actor.session.clone(),
        version: 1,
    }
}
fn dependency() -> Failure {
    Failure::new(
        Kind::Unavailable,
        "fixture.dependency",
        "PRIVATE dependency failure",
    )
}
struct Probe<T> {
    store: Arc<T>,
    input: Mutation,
    clock: Arc<TestClock>,
    scenario: String,
    calls: AtomicUsize,
    preview: Id,
}
impl<T: Installations> Probe<T> {
    async fn rotate(&self, s: &dyn Scope, rebind: bool) {
        let mut m = self.input.clone();
        m.expected = 1;
        m.registration_id = id();
        m.spec.token = Secret::new(format!("PRIVATE-rotated-{}", id()));
        if rebind {
            m.actor.principal = id();
            m.actor.session = id();
        }
        self.store.register(s, m, AT).await.unwrap();
    }
}
impl<T: Installations> SessionEligibility for Probe<T> {
    fn eligible<'a>(
        &'a self,
        s: &'a dyn Scope,
        p: &'a Id,
        session: &'a Id,
    ) -> PortFuture<'a, bool> {
        Box::pin(async move {
            assert_eq!(p, &self.input.actor.principal);
            assert_eq!(session, &self.input.actor.session);
            match self.scenario.as_str() {
                "session_denied" => return Ok(false),
                "session_outage" => return Err(dependency()),
                "rotation_during_check" => self.rotate(s, false).await,
                "expiry_during_check" => {
                    self.clock
                        .0
                        .store(self.input.actor.expires_at, Ordering::Relaxed);
                }
                "revocation_during_check" => self
                    .store
                    .retire_session(
                        s,
                        Revocation {
                            event_id: id(),
                            principal_id: p.clone(),
                            session_id: session.clone(),
                            version: 2,
                            reason: "sign_out".into(),
                            occurred_at: AT,
                        },
                    )
                    .await
                    .unwrap(),
                _ => {}
            }
            Ok(true)
        })
    }
}
impl<T: Installations> PushGateway for Probe<T> {
    fn send_preview<'a>(
        &'a self,
        s: &'a dyn Scope,
        address: PushAddress,
        preview: Id,
    ) -> PortFuture<'a, PushDisposition> {
        Box::pin(async move {
            self.calls.fetch_add(1, Ordering::Relaxed);
            s.check()?;
            assert_eq!(address.lease, lease(&self.input));
            assert_eq!(address.spec, self.input.spec);
            assert_eq!(address.registered_at, AT);
            assert_eq!(preview, self.preview);
            match self.scenario.as_str() {
                "invalid" | "cleanup_failure" => Ok(PushDisposition::InvalidTarget),
                "uncertain" => Err(dependency()),
                "unknown" => PushDisposition::parse("invented"),
                "rotation_during_send" | "rebind_during_send" => {
                    self.rotate(s, self.scenario == "rebind_during_send").await;
                    Ok(PushDisposition::InvalidTarget)
                }
                "unregister_during_send" => {
                    let m = &self.input;
                    self.store
                        .unregister(s, &m.actor, &m.id, &m.proof_digest, 1, AT)
                        .await
                        .unwrap();
                    Ok(PushDisposition::InvalidTarget)
                }
                _ => Ok(PushDisposition::Accepted),
            }
        })
    }
}
struct Targets<T> {
    inner: Arc<T>,
    fail_cleanup: bool,
}
impl<T: PushTargets> PushTargets for Targets<T> {
    fn target<'a>(
        &'a self,
        s: &'a dyn Scope,
        l: &'a PushLease,
        now: u64,
    ) -> PortFuture<'a, PushAddress> {
        self.inner.target(s, l, now)
    }
    fn retire<'a>(&'a self, s: &'a dyn Scope, l: &'a PushLease, now: u64) -> PortFuture<'a, bool> {
        Box::pin(async move {
            if self.fail_cleanup {
                return Err(dependency());
            }
            self.inner.retire(s, l, now).await
        })
    }
}
pub async fn conformance<T: Installations + PushTargets + 'static>(store: Arc<T>) {
    let cases: Vec<Value> = serde_json::from_str(include_str!(
        "../../../../../../contracts/fixtures/backend-push/attempts.json"
    ))
    .unwrap();
    for case in cases {
        let name = case["name"].as_str().unwrap();
        let s = Control::default();
        let m = mutation();
        let mut l = lease(&m);
        store.register(&s, m.clone(), AT).await.unwrap();
        let clock = Arc::new(TestClock(AtomicU64::new(AT)));
        let probe = Arc::new(Probe {
            store: store.clone(),
            input: m.clone(),
            clock: clock.clone(),
            scenario: name.into(),
            calls: AtomicUsize::new(0),
            preview: id(),
        });
        let h = SendPushPreview {
            targets: Arc::new(Targets {
                inner: store.clone(),
                fail_cleanup: name == "cleanup_failure",
            }),
            sessions: probe.clone(),
            gateway: probe.clone(),
            clock: clock.clone(),
        };
        match name {
            "missing" => l.installation_id = id(),
            "stale" => l.version = 2,
            "wrong_owner" => l.principal_id = id(),
            "wrong_session" => l.session_id = id(),
            "disabled" => {
                store
                    .unregister(&s, &m.actor, &m.id, &m.proof_digest, 1, AT)
                    .await
                    .unwrap();
            }
            "expired" => clock.0.store(m.actor.expires_at, Ordering::Relaxed),
            "before_creation" => clock.0.store(AT - 1, Ordering::Relaxed),
            _ => {}
        }
        let call_scope = Control::default();
        if name == "canceled" {
            call_scope.cancel();
        }
        let result = h.handle(&call_scope, l, probe.preview.clone()).await;
        let (disposition, retired, error) = match result {
            Ok(PushAttempt::Accepted) => (Some("accepted"), false, None),
            Ok(PushAttempt::InvalidTarget { retirement }) => match retirement {
                Ok(retired) => (Some("invalid_target"), retired, None),
                Err(e) => (Some("invalid_target"), false, Some(e.kind().as_str())),
            },
            Err(e) => (None, false, Some(e.kind().as_str())),
        };
        assert_eq!(disposition, case["disposition"].as_str(), "{name}");
        assert_eq!(error, case["error_kind"].as_str(), "{name}");
        assert_eq!(retired, case["retired"].as_bool().unwrap(), "{name}");
        assert_eq!(
            probe.calls.load(Ordering::Relaxed) as u64,
            case["gateway_calls"].as_u64().unwrap(),
            "{name}"
        );
        let observer = Actor {
            principal: id(),
            session: id(),
            expires_at: AT + 3_600_000_000,
        };
        let current = store
            .get(&s, &observer, &m.id, &m.proof_digest, AT)
            .await
            .unwrap();
        let v = current.snapshot();
        assert_eq!(v.enabled, case["enabled"].as_bool().unwrap(), "{name}");
        assert_eq!(v.version, case["version"].as_u64().unwrap(), "{name}");
        assert_eq!(v.spec.token.expose().is_empty(), !v.enabled, "{name}");
        if name == "invalid" {
            assert!(!store.retire(&s, &lease(&m), AT).await.unwrap());
            let mut other = mutation();
            other.spec = m.spec.clone();
            store.register(&s, other, AT).await.unwrap();
        }
    }
    let s = Control::default();
    let m = mutation();
    store.register(&s, m.clone(), AT).await.unwrap();
    let mut tasks = vec![];
    for _ in 0..12 {
        let store = store.clone();
        let l = lease(&m);
        tasks.push(tokio::spawn(async move {
            store.retire(&Control::default(), &l, AT).await.unwrap()
        }));
    }
    let mut wins = 0;
    for task in tasks {
        wins += usize::from(task.await.unwrap());
    }
    assert_eq!(wins, 1);
    let canceled = Control::default();
    canceled.cancel();
    assert!(store.retire(&canceled, &lease(&m), AT).await.is_err());
    let missing = PushLease {
        installation_id: id(),
        ..lease(&m)
    };
    assert!(!store.retire(&s, &missing, AT).await.unwrap());
}
