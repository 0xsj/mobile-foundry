#[path = "support/recovery.rs"]
mod support;
use events::dispatcher::Control;
use identity::{
    app::{
        ports::{Purpose as TokenPurpose, Store, Tokens},
        recovery::RecoveryStore,
    },
    domain::{
        DisplayName, Login, Principal, Secret, Timestamp,
        recovery::{self, Purpose},
    },
    infra::{memory::MemoryStore, security::TokenAdapter},
};
use kernel::{
    Failure,
    id::{Generator, Id, RandomIds},
};
use std::sync::{
    Arc,
    atomic::{AtomicBool, Ordering},
};
#[test]
fn email_policy_and_separate_token_purposes() {
    let cases: Vec<serde_json::Value> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-recovery/emails.json"
    ))
    .unwrap();
    for c in cases {
        let result = recovery::email(c["input"].as_str().unwrap());
        assert_eq!(result.is_ok(), c["valid"].as_bool().unwrap());
        if let Ok(value) = result {
            assert_eq!(value, c["canonical"].as_str().unwrap());
        }
    }
    for (p, prefix) in [
        (TokenPurpose::VerifyEmail, "e1."),
        (TokenPurpose::ResetPassword, "p1."),
    ] {
        let issued = TokenAdapter.issue(&Control::default(), p).unwrap();
        assert!(issued.raw.expose().starts_with(prefix));
        assert!(
            TokenAdapter
                .digest(&Control::default(), &issued.raw, TokenPurpose::Access)
                .is_err()
        );
    }
}
#[tokio::test]
async fn memory_recovery_lifecycle() {
    support::conformance(Arc::new(MemoryStore::new(Arc::new(RandomIds)))).await;
}
struct IDs(AtomicBool);
impl Generator for IDs {
    fn new_id(&self) -> Result<Id, Failure> {
        if self.0.load(Ordering::Acquire) {
            Err(identity::domain::invalid_state())
        } else {
            RandomIds.new_id()
        }
    }
}
#[tokio::test]
async fn required_fact_failure_leaves_challenge_contact_password_and_sessions_unchanged() {
    let ids = Arc::new(IDs(AtomicBool::new(false)));
    let store = MemoryStore::new(ids.clone());
    let s = Control::default();
    let actor = support::id();
    let now = Timestamp::from_micros(1_800_000_000_000_000).unwrap();
    store
        .register(
            &s,
            Principal::new(
                actor.clone(),
                Login::new("fact.failure").unwrap(),
                DisplayName::new("SECRET").unwrap(),
                Secret::new("checkpoint".into()),
                now,
            )
            .unwrap(),
        )
        .await
        .unwrap();
    let c = support::challenge(&actor, Purpose::VerifyEmail, "checkpoint", now);
    store.begin_challenge(&s, c.clone()).await.unwrap();
    ids.0.store(true, Ordering::Release);
    assert!(store.verify_email(&s, c.digest.clone(), now).await.is_err());
    assert!(store.contact(&s, &actor).await.unwrap().is_none());
    ids.0.store(false, Ordering::Release);
    store.verify_email(&s, c.digest, now).await.unwrap();
    let session = support::session(&actor, now);
    store
        .issue_checked(&s, session.clone(), &Secret::new("checkpoint".into()))
        .await
        .unwrap();
    let c = support::challenge(&actor, Purpose::ResetPassword, "checkpoint", now);
    store.begin_challenge(&s, c.clone()).await.unwrap();
    ids.0.store(true, Ordering::Release);
    assert!(
        store
            .reset_password(&s, c.digest.clone(), Secret::new("new".into()), now)
            .await
            .is_err()
    );
    assert_eq!(
        store
            .by_id(&s, &actor)
            .await
            .unwrap()
            .unwrap()
            .password_hash()
            .expose(),
        "checkpoint"
    );
    assert!(
        store
            .access_session(&s, session.state().access.clone(), now)
            .await
            .is_ok()
    );
    ids.0.store(false, Ordering::Release);
    store
        .reset_password(&s, c.digest, Secret::new("new".into()), now)
        .await
        .unwrap();
}
