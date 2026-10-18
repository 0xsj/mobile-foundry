use events::dispatcher::Control;
use kernel::{
    Failure,
    id::{Generator, Id, RandomIds},
};
use notifications::{
    app::installations::{Actor, Installations, Mutation, Revocation},
    domain::installation::{InstallationSpec, PushRoute, Secret},
};
use std::sync::Arc;
pub const AT: u64 = 1_800_000_000_000_000;
pub fn id() -> Id {
    RandomIds.new_id().unwrap()
}
pub fn actor() -> Actor {
    Actor {
        principal: id(),
        session: id(),
        expires_at: AT + 1000,
    }
}
pub fn mutation(a: &Actor) -> Mutation {
    Mutation {
        actor: a.clone(),
        id: id(),
        registration_id: id(),
        proof_digest: Secret::new("a".repeat(64)),
        spec: InstallationSpec {
            route: PushRoute {
                provider: "apns".into(),
                application: "dev.example.product".into(),
                environment: "sandbox".into(),
            },
            token: Secret::new(format!("PRIVATE-{}", id())),
        },
        expected: 0,
    }
}
pub fn revocation(a: &Actor) -> Revocation {
    Revocation {
        event_id: id(),
        principal_id: a.principal.clone(),
        session_id: a.session.clone(),
        version: 2,
        reason: "sign_out".into(),
        occurred_at: AT + 50,
    }
}
fn code(e: Failure, want: &str) {
    assert_eq!(e.public().code.as_deref(), Some(want));
}
pub async fn conformance<T: Installations + 'static>(store: Arc<T>) {
    let s = Control::default();
    let a = actor();
    let m = mutation(&a);
    // Concurrent retries are checkpoint conflicts, never historical receipt replay.
    let mut tasks = vec![];
    for _ in 0..12 {
        let store = store.clone();
        let m = m.clone();
        tasks.push(tokio::spawn(async move {
            store.register(&Control::default(), m, AT).await
        }));
    }
    let mut winners = 0;
    for t in tasks {
        match t.await.unwrap() {
            Ok(i) => {
                assert_eq!(i.snapshot().version, 1);
                winners += 1
            }
            Err(e) => code(e, "notifications.installation_conflict"),
        }
    }
    assert_eq!(winners, 1);
    let original = store.get(&s, &a, &m.id, &m.proof_digest, AT).await.unwrap();
    let wrong = Secret::new("b".repeat(64));
    for k in [&m.id, &id()] {
        code(
            store.get(&s, &a, k, &wrong, AT).await.unwrap_err(),
            "notifications.installation_not_found",
        );
    }
    let mut update = m.clone();
    update.expected = 1;
    code(
        store
            .register(&s, update.clone(), AT + 1)
            .await
            .unwrap_err(),
        "notifications.installation_conflict",
    );
    update.registration_id = id();
    update.spec.token = Secret::new(format!("PRIVATE-rotation-{}", id()));
    let rotated = store.register(&s, update.clone(), AT + 1).await.unwrap();
    assert_eq!(rotated.snapshot().version, 2);
    code(
        store
            .unregister(&s, &a, &m.id, &m.proof_digest, 1, AT + 2)
            .await
            .unwrap_err(),
        "notifications.installation_conflict",
    );
    // Proof plus fresh checkpoint permits explicit principal/session rebinding.
    let b = actor();
    let mut rebound = update.clone();
    rebound.actor = b.clone();
    rebound.expected = 2;
    rebound.registration_id = id();
    let bound = store.register(&s, rebound.clone(), AT + 2).await.unwrap();
    assert!(b.owns(bound.snapshot()));
    assert_eq!(bound.snapshot().created_at, original.snapshot().created_at);
    assert_eq!(
        store
            .get(&s, &a, &m.id, &m.proof_digest, AT + 3)
            .await
            .unwrap(),
        bound
    );
    code(
        store
            .unregister(&s, &a, &m.id, &m.proof_digest, 3, AT + 3)
            .await
            .unwrap_err(),
        "notifications.installation_binding",
    );
    let old_revocation = revocation(&a);
    store
        .retire_session(&s, old_revocation.clone())
        .await
        .unwrap();
    assert_eq!(
        store
            .get(&s, &b, &m.id, &m.proof_digest, AT + 51)
            .await
            .unwrap(),
        bound
    );
    code(
        store.register(&s, mutation(&a), AT + 51).await.unwrap_err(),
        "notifications.identity_session_required",
    );
    store
        .retire_session(&s, old_revocation.clone())
        .await
        .unwrap();
    let mut changed = old_revocation;
    changed.reason = "refresh_reuse".into();
    code(
        store.retire_session(&s, changed).await.unwrap_err(),
        "notifications.event_conflict",
    );
    // Live targets are unique; failed claims leave both bindings intact.
    let mut collision = mutation(&b);
    collision.spec = rebound.spec.clone();
    code(
        store
            .register(&s, collision.clone(), AT + 52)
            .await
            .unwrap_err(),
        "notifications.installation_target",
    );
    code(
        store
            .get(&s, &b, &collision.id, &collision.proof_digest, AT + 52)
            .await
            .unwrap_err(),
        "notifications.installation_not_found",
    );
    let disabled = store
        .unregister(&s, &b, &m.id, &m.proof_digest, 3, AT + 53)
        .await
        .unwrap();
    assert!(!disabled.snapshot().enabled);
    assert_eq!(disabled.snapshot().spec.token.expose(), "");
    assert_eq!(disabled.snapshot().version, 4);
    assert_eq!(
        store
            .unregister(&s, &b, &m.id, &m.proof_digest, 1, AT + 54)
            .await
            .unwrap(),
        disabled
    );
    store
        .register(&s, collision.clone(), AT + 54)
        .await
        .unwrap();
    // Retirement erases every matching target, including v2 reasons, atomically.
    let other = mutation(&b);
    store.register(&s, other.clone(), AT + 55).await.unwrap();
    let mut r = revocation(&b);
    r.reason = "password_change".into();
    r.occurred_at = AT + 56;
    store.retire_session(&s, r.clone()).await.unwrap();
    store.retire_session(&s, r.clone()).await.unwrap();
    let observer = actor();
    for i in [&collision, &other] {
        let v = store
            .get(&s, &observer, &i.id, &i.proof_digest, AT + 57)
            .await
            .unwrap();
        assert!(!v.snapshot().enabled);
        assert!(v.snapshot().spec.token.expose().is_empty());
    }
    code(
        store.register(&s, mutation(&b), AT + 58).await.unwrap_err(),
        "notifications.identity_session_required",
    );
    let absent = actor();
    let mut r = revocation(&absent);
    r.reason = "password_reset".into();
    store.retire_session(&s, r).await.unwrap();
    code(
        store
            .register(&s, mutation(&absent), AT + 59)
            .await
            .unwrap_err(),
        "notifications.identity_session_required",
    );
    // Expiry is checked at claim time; successful reclamation erases the old token.
    let mut short = actor();
    short.expires_at = AT + 10;
    let old = mutation(&short);
    store.register(&s, old.clone(), AT).await.unwrap();
    let mut claim = mutation(&observer);
    claim.spec = old.spec.clone();
    code(
        store.register(&s, claim.clone(), AT + 9).await.unwrap_err(),
        "notifications.installation_target",
    );
    store.register(&s, claim, AT + 10).await.unwrap();
    let prior = store
        .get(&s, &observer, &old.id, &old.proof_digest, AT + 10)
        .await
        .unwrap();
    assert!(!prior.snapshot().enabled);
    assert!(prior.snapshot().spec.token.expose().is_empty());
    // Two distinct installations racing for one target produce one winner.
    let first = mutation(&observer);
    let mut second = mutation(&observer);
    second.spec = first.spec.clone();
    let (x, y) = tokio::join!(
        store.register(&s, first, AT + 60),
        store.register(&s, second, AT + 60)
    );
    assert_ne!(x.is_ok(), y.is_ok());
    code(
        x.err().or(y.err()).unwrap(),
        "notifications.installation_target",
    );
    // Register versus retirement cannot leave an enabled retired-session target.
    let raced = actor();
    let m = mutation(&raced);
    let r = revocation(&raced);
    let (registered, retired) = tokio::join!(
        store.register(&s, m.clone(), AT),
        store.retire_session(&s, r)
    );
    retired.unwrap();
    match registered {
        Ok(_) => {
            let i = store
                .get(&s, &observer, &m.id, &m.proof_digest, AT + 70)
                .await
                .unwrap();
            assert!(!i.snapshot().enabled);
            assert!(i.snapshot().spec.token.expose().is_empty());
        }
        Err(e) => code(e, "notifications.identity_session_required"),
    }
    let canceled = Control::default();
    canceled.cancel();
    let pending = mutation(&observer);
    assert!(
        store
            .register(&canceled, pending.clone(), AT + 70)
            .await
            .is_err()
    );
    code(
        store
            .get(&s, &observer, &pending.id, &pending.proof_digest, AT + 70)
            .await
            .unwrap_err(),
        "notifications.installation_not_found",
    );
    assert!(
        store
            .retire_session(&canceled, revocation(&observer))
            .await
            .is_err()
    );
    store.register(&s, pending.clone(), AT + 70).await.unwrap();
    assert!(
        store
            .unregister(
                &canceled,
                &observer,
                &pending.id,
                &pending.proof_digest,
                1,
                AT + 71
            )
            .await
            .is_err()
    );
    assert!(
        store
            .get(&s, &observer, &pending.id, &pending.proof_digest, AT + 71)
            .await
            .unwrap()
            .snapshot()
            .enabled
    );
    code(
        store
            .get(
                &s,
                &observer,
                &pending.id,
                &pending.proof_digest,
                observer.expires_at,
            )
            .await
            .unwrap_err(),
        "notifications.identity_session_required",
    );
}
