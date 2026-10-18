mod support;
use notifications::{
    app::{
        Dependencies,
        command::{MarkRead, SetMediaPreference},
        ports::{AccountAccess, PortFuture, Scope},
        query::{GetPreferences, ListInbox},
    },
    domain::{Item, MAX_SEQUENCE, MAX_TIME, MEDIA, Snapshot, UPLOAD_READY},
};
use std::sync::Arc;
#[tokio::test]
async fn memory_decisions_replay_paging_read_and_scope() {
    support::conformance(Arc::new(
        notifications::infra::memory::MemoryStore::default(),
    ))
    .await;
}
#[test]
fn item_restoration_and_first_read() {
    let t = support::target();
    let d = support::delivery(&t);
    let original = d.item(1, 10).unwrap();
    let read = original.mark_read(20).unwrap();
    assert_eq!(read.mark_read(30).unwrap(), read);
    let base = Snapshot {
        id: d.event_id,
        account_id: d.account_id,
        principal_id: d.principal_id,
        file_id: d.file_id,
        sequence: 1,
        kind: UPLOAD_READY.into(),
        category: MEDIA.into(),
        occurred_at: 0,
        created_at: 0,
        read_at: None,
    };
    assert!(Item::restore(base.clone()).is_ok());
    let mut bad = base.clone();
    bad.sequence = MAX_SEQUENCE + 1;
    assert!(Item::restore(bad).is_err());
    let mut bad = base.clone();
    bad.sequence = 0;
    assert!(Item::restore(bad).is_err());
    let mut bad = base.clone();
    bad.kind = "other".into();
    assert!(Item::restore(bad).is_err());
    let mut bad = base;
    bad.read_at = Some(MAX_TIME + 1);
    assert!(Item::restore(bad).is_err());
}
struct Clock;
impl kernel::clock::Clock for Clock {
    fn now(&self) -> std::time::SystemTime {
        std::time::UNIX_EPOCH
    }
}
struct Access(bool);
impl AccountAccess for Access {
    fn allowed<'a>(
        &'a self,
        _: &'a dyn Scope,
        _: &'a kernel::id::Id,
        _: &'a kernel::id::Id,
    ) -> PortFuture<'a, bool> {
        Box::pin(async move {
            if self.0 {
                Ok(false)
            } else {
                Err(kernel::Failure::new(
                    kernel::Kind::Unavailable,
                    "test.access",
                    "access unavailable",
                ))
            }
        })
    }
}
#[tokio::test]
async fn public_use_cases_refuse_denied_or_unavailable_access() {
    let t = support::target();
    let scope = events::dispatcher::Control::default();
    for negative in [true, false] {
        let d = Dependencies {
            store: Arc::new(notifications::infra::memory::MemoryStore::default()),
            access: Arc::new(Access(negative)),
            clock: Arc::new(Clock),
        };
        let expected = if negative {
            kernel::Kind::Forbidden
        } else {
            kernel::Kind::Unavailable
        };
        assert_eq!(
            ListInbox(d.clone())
                .handle(&scope, support::input(&t))
                .await
                .err()
                .unwrap()
                .kind(),
            expected
        );
        assert_eq!(
            GetPreferences(d.clone())
                .handle(&scope, &t)
                .await
                .unwrap_err()
                .kind(),
            expected
        );
        assert_eq!(
            SetMediaPreference(d.clone())
                .handle(&scope, &t, false)
                .await
                .unwrap_err()
                .kind(),
            expected
        );
        assert_eq!(
            MarkRead(d)
                .handle(&scope, &t, &support::id())
                .await
                .unwrap_err()
                .kind(),
            expected
        );
    }
}
