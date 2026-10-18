use identity::app::ports::Store;
use identity::{
    app::{
        command::{Dependencies, recovery::Recovery},
        ports::{Passwords, PortFuture, Purpose as TP, Scope, Tokens},
        recovery::{Mail, Mailer, RecoveryStore},
    },
    domain::{
        DisplayName, Login, Password, Principal, Secret, Timestamp,
        recovery::{Challenge, Purpose},
    },
    infra::{memory::MemoryStore, reset_queue::ResetQueue, security::TokenAdapter},
};
use kernel::id::{Generator, RandomIds};
use std::{
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
    time::{Duration, UNIX_EPOCH},
};
struct Clock;
impl kernel::clock::Clock for Clock {
    fn now(&self) -> std::time::SystemTime {
        UNIX_EPOCH + Duration::from_secs(1_800_000_000)
    }
}
struct NoPasswords;
impl Passwords for NoPasswords {
    fn hash<'a>(&'a self, _: &'a dyn Scope, _: Password) -> PortFuture<'a, Secret> {
        Box::pin(async { panic!("queue must not hash") })
    }
    fn verify<'a>(&'a self, _: &'a dyn Scope, _: Secret, _: Password) -> PortFuture<'a, bool> {
        Box::pin(async { panic!("queue must not verify passwords") })
    }
    fn dummy<'a>(&'a self, _: &'a dyn Scope, _: Password) -> PortFuture<'a, ()> {
        Box::pin(async { panic!("queue must not verify passwords") })
    }
}
struct Held {
    entered: tokio::sync::Notify,
    dropped: Arc<AtomicBool>,
}
impl Mailer for Held {
    fn send<'a>(&'a self, _: &'a dyn Scope, _: Mail) -> PortFuture<'a, ()> {
        Box::pin(async move {
            struct Guard(Arc<AtomicBool>);
            impl Drop for Guard {
                fn drop(&mut self) {
                    self.0.store(true, Ordering::Release);
                }
            }
            let _guard = Guard(self.dropped.clone());
            self.entered.notify_one();
            std::future::pending().await
        })
    }
}
#[tokio::test]
async fn reset_admission_is_independent_of_lookup_bounded_and_owned_on_close() {
    let scope = events::dispatcher::Control::default();
    let store = Arc::new(MemoryStore::new(Arc::new(RandomIds)));
    let actor = RandomIds.new_id().unwrap();
    let now = Timestamp::from_micros(1_800_000_000_000_000).unwrap();
    store
        .register(
            &scope,
            Principal::new(
                actor.clone(),
                Login::new("queue.owner").unwrap(),
                DisplayName::new("Owner").unwrap(),
                Secret::new("hash".into()),
                now,
            )
            .unwrap(),
        )
        .await
        .unwrap();
    let issued = TokenAdapter.issue(&scope, TP::VerifyEmail).unwrap();
    store
        .begin_challenge(
            &scope,
            Challenge {
                principal_id: actor,
                purpose: Purpose::VerifyEmail,
                email: "owner@example.test".into(),
                digest: issued.digest.clone(),
                credential_hash: Secret::new("hash".into()),
                created_at: now,
                expires_at: Timestamp::from_micros(now.micros() + 20 * 60 * 1_000_000).unwrap(),
            },
        )
        .await
        .unwrap();
    store
        .verify_email(&scope, issued.digest, now)
        .await
        .unwrap();
    let held = Arc::new(Held {
        entered: tokio::sync::Notify::new(),
        dropped: Arc::new(AtomicBool::new(false)),
    });
    let queue = ResetQueue::start(Recovery {
        core: Dependencies {
            store: store.clone(),
            passwords: Arc::new(NoPasswords),
            tokens: Arc::new(TokenAdapter),
            ids: Arc::new(RandomIds),
            clock: Arc::new(Clock),
        },
        store,
        mail: held.clone(),
    })
    .unwrap();
    queue.enqueue(&scope, "owner@example.test").unwrap();
    tokio::time::timeout(Duration::from_secs(1), held.entered.notified())
        .await
        .unwrap();
    for _ in 0..64 {
        queue.enqueue(&scope, "unknown@example.test").unwrap();
    }
    assert_eq!(
        queue
            .enqueue(&scope, "owner@example.test")
            .unwrap_err()
            .kind(),
        kernel::Kind::RateLimited
    );
    // Cancel close after it has fenced admission and started awaiting the worker.
    // The next owned close must still be able to join the same task.
    let mut closing = Box::pin(queue.close());
    std::future::poll_fn(|cx| {
        assert!(closing.as_mut().poll(cx).is_pending());
        std::task::Poll::Ready(())
    })
    .await;
    drop(closing);
    assert!(queue.ready().is_err());
    queue.close().await.unwrap();
    assert!(held.dropped.load(Ordering::Acquire));
    assert!(queue.enqueue(&scope, "owner@example.test").is_err());
    queue.close().await.unwrap();
}
