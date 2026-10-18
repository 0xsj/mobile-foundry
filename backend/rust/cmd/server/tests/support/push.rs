//! Only composition acceptance imports both module implementations.
#[path = "../../../../internal/identity/tests/support/eligibility.rs"]
mod sessions;
#[path = "../../../../internal/notifications/tests/support/push.rs"]
mod targets;
use events::dispatcher::Control;
use identity::app::{
    ports::Store,
    query::session_eligibility::{SessionEligibility, SessionStatus},
};
use kernel::{
    Kind,
    context::{PortFuture, Scope},
    id::Id,
};
use notifications::app::{
    installations::Installations,
    push::{PushAddress, PushAttempt, PushDisposition, PushGateway, PushTargets},
};
use std::sync::{
    Arc,
    atomic::{AtomicU64, AtomicUsize, Ordering},
};
struct Gateway(AtomicUsize);
impl PushGateway for Gateway {
    fn send_preview<'a>(
        &'a self,
        s: &'a dyn Scope,
        _: PushAddress,
        _: Id,
    ) -> PortFuture<'a, PushDisposition> {
        Box::pin(async move {
            s.check()?;
            self.0.fetch_add(1, Ordering::Relaxed);
            Ok(PushDisposition::Accepted)
        })
    }
}
pub async fn conformance<
    T: Installations + PushTargets + 'static,
    I: Store + SessionStatus + 'static,
>(
    store: Arc<T>,
    identity: Arc<I>,
) {
    targets::conformance(store.clone()).await;
    sessions::conformance(identity.clone()).await;
    let s = Control::default();
    let issued = sessions::issued(identity.as_ref()).await;
    let v = issued.state();
    let clock = Arc::new(targets::TestClock(AtomicU64::new(targets::AT)));
    let mut builder = capability::Builder::default();
    builder
        .register(
            identity::transport::capability::v1::session_eligibility::NAME,
            &["notifications"],
            Arc::new(
                identity::transport::capability::v1::session_eligibility::Provider(
                    SessionEligibility {
                        store: identity.clone(),
                        clock: clock.clone(),
                    },
                ),
            ),
        )
        .unwrap();
    let registry = builder.freeze();
    let mut m = targets::mutation();
    m.actor.principal = v.principal_id.clone();
    m.actor.session = v.id.clone();
    m.actor.expires_at = v.expires_at.micros();
    store.register(&s, m.clone(), targets::AT).await.unwrap();
    let lease = targets::lease(&m);
    let gateway = Arc::new(Gateway(AtomicUsize::new(0)));
    let h =
        notifications::new_push_preview(store.clone(), clock.clone(), &registry, gateway.clone())
            .unwrap();
    clock.0.store(targets::AT + 960_000_000, Ordering::Relaxed);
    assert!(matches!(
        h.handle(&s, lease.clone(), targets::id()).await.unwrap(),
        PushAttempt::Accepted
    ));
    identity
        .revoke(
            &s,
            v.refresh.clone(),
            identity::domain::Timestamp::from_micros(targets::AT + 960_000_000).unwrap(),
        )
        .await
        .unwrap();
    // Deliberately omit delivery: Identity already refuses the still-enabled target.
    store
        .target(&s, &lease, targets::AT + 960_000_000)
        .await
        .unwrap();
    assert_eq!(
        h.handle(&s, lease, targets::id()).await.unwrap_err().kind(),
        Kind::Forbidden
    );
    assert_eq!(gateway.0.load(Ordering::Relaxed), 1);
    assert!(
        notifications::new_push_preview(
            store,
            clock,
            &capability::Builder::default().freeze(),
            gateway
        )
        .is_err()
    );
}
