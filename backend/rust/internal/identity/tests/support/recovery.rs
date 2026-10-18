use events::dispatcher::Control;
use identity::{
    app::{
        ports::{Purpose as TokenPurpose, Store, Tokens},
        recovery::RecoveryStore,
    },
    domain::{
        DisplayName, Login, Principal, Secret, Session, Timestamp,
        recovery::{Challenge, Purpose},
    },
    infra::security::TokenAdapter,
};
use kernel::id::{Generator, Id, RandomIds};
use std::sync::Arc;
pub fn id() -> Id {
    RandomIds.new_id().unwrap()
}
pub fn challenge(actor: &Id, purpose: Purpose, hash: &str, now: Timestamp) -> Challenge {
    Challenge {
        principal_id: actor.clone(),
        purpose,
        email: format!("{}@example.test", actor),
        digest: TokenAdapter
            .issue(&Control::default(), TokenPurpose::VerifyEmail)
            .unwrap()
            .digest,
        credential_hash: Secret::new(hash.into()),
        created_at: now,
        expires_at: Timestamp::from_micros(now.micros() + 20 * 60 * 1_000_000).unwrap(),
    }
}
pub fn session(actor: &Id, now: Timestamp) -> Session {
    Session::new(
        id(),
        actor.clone(),
        TokenAdapter
            .issue(&Control::default(), TokenPurpose::Access)
            .unwrap()
            .digest,
        TokenAdapter
            .issue(&Control::default(), TokenPurpose::Refresh)
            .unwrap()
            .digest,
        now,
    )
    .unwrap()
}
pub async fn conformance<T: Store + RecoveryStore + 'static>(store: Arc<T>) {
    let s = Control::default();
    let now = Timestamp::from_micros(1_800_000_000_000_000).unwrap();
    let actor = id();
    let p = Principal::new(
        actor.clone(),
        Login::new(actor.as_str()).unwrap(),
        DisplayName::new("SECRET").unwrap(),
        Secret::new("old-checkpoint".into()),
        now,
    )
    .unwrap();
    store.register(&s, p).await.unwrap();
    let first = challenge(&actor, Purpose::VerifyEmail, "old-checkpoint", now);
    store.begin_challenge(&s, first.clone()).await.unwrap();
    let second = challenge(&actor, Purpose::VerifyEmail, "old-checkpoint", now);
    store.begin_challenge(&s, second.clone()).await.unwrap();
    assert!(
        store
            .verify_email(&s, first.digest.clone(), now)
            .await
            .is_err()
    );
    store
        .verify_email(&s, second.digest.clone(), now)
        .await
        .unwrap();
    assert!(store.verify_email(&s, second.digest, now).await.is_err());
    let contact = store.contact(&s, &actor).await.unwrap().unwrap();
    assert_eq!(contact.email, first.email);
    assert_eq!(
        store
            .by_email(&s, &contact.email)
            .await
            .unwrap()
            .unwrap()
            .id(),
        &actor
    );
    let old = session(&actor, now);
    store
        .issue_checked(&s, old.clone(), &Secret::new("old-checkpoint".into()))
        .await
        .unwrap();
    let reset = challenge(&actor, Purpose::ResetPassword, "old-checkpoint", now);
    store.begin_challenge(&s, reset.clone()).await.unwrap();
    store
        .change_password(
            &s,
            &actor,
            Secret::new("old-checkpoint".into()),
            Secret::new("new-checkpoint".into()),
            now,
        )
        .await
        .unwrap();
    assert!(
        store
            .reset_password(&s, reset.digest, Secret::new("unused".into()), now)
            .await
            .is_err()
    );
    assert!(
        store
            .access_session(&s, old.state().access.clone(), now)
            .await
            .is_err()
    );
    assert!(
        store
            .issue_checked(
                &s,
                session(&actor, now),
                &Secret::new("old-checkpoint".into())
            )
            .await
            .is_err()
    );
    let active = session(&actor, now);
    store
        .issue_checked(&s, active.clone(), &Secret::new("new-checkpoint".into()))
        .await
        .unwrap();
    let reset = challenge(&actor, Purpose::ResetPassword, "new-checkpoint", now);
    store.begin_challenge(&s, reset.clone()).await.unwrap();
    let (a, b) = tokio::join!(
        store.reset_password(
            &s,
            reset.digest.clone(),
            Secret::new("winner-a".into()),
            now
        ),
        store.reset_password(&s, reset.digest, Secret::new("winner-b".into()), now)
    );
    assert_ne!(a.is_ok(), b.is_ok());
    assert!(
        store
            .access_session(&s, active.state().access.clone(), now)
            .await
            .is_err()
    );
    let current = store.by_id(&s, &actor).await.unwrap().unwrap();
    let expected = current.password_hash().clone();
    let raced = session(&actor, now);
    let (issue, change) = tokio::join!(
        store.issue_checked(&s, raced.clone(), &expected),
        store.change_password(
            &s,
            &actor,
            expected.clone(),
            Secret::new("after-race".into()),
            now
        )
    );
    change.unwrap();
    if let Err(error) = issue {
        assert_eq!(error.kind(), kernel::Kind::Unauthenticated);
    }
    assert!(
        store
            .access_session(&s, raced.state().access.clone(), now)
            .await
            .is_err()
    );
    let stale = challenge(&actor, Purpose::VerifyEmail, "old-checkpoint", now);
    assert!(store.begin_challenge(&s, stale).await.is_err());
    let expired = challenge(&actor, Purpose::ResetPassword, "after-race", now);
    store.begin_challenge(&s, expired.clone()).await.unwrap();
    assert!(
        store
            .reset_password(
                &s,
                expired.digest,
                Secret::new("expired".into()),
                expired.expires_at
            )
            .await
            .is_err()
    );
    assert!(
        store
            .prune_challenges(&s, expired.expires_at, 1000)
            .await
            .unwrap()
            >= 1
    );
    let mut changed_contact = challenge(&actor, Purpose::VerifyEmail, "after-race", now);
    changed_contact.email = "different@example.test".into();
    assert!(store.begin_challenge(&s, changed_contact).await.is_err());
    println!(
        "Recovery store: replacement/single use, contact/checkpoint, atomic session revocation, issuance race and expiry/pruning passed"
    );
}
