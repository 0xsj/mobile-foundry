use kernel::id::{Generator, Id, RandomIds};
use notifications::{
    app::{
        self, Delivery, ListInput, Target,
        ports::{Scope, Store},
    },
    domain::MAX_TIME,
};
use std::sync::Arc;
pub fn id() -> Id {
    RandomIds.new_id().unwrap()
}
pub fn target() -> Target {
    Target {
        actor: id(),
        account: id(),
    }
}
pub fn delivery(t: &Target) -> Delivery {
    Delivery {
        event_id: id(),
        account_id: t.account.clone(),
        principal_id: t.actor.clone(),
        file_id: id(),
        occurred_at: 1_800_000_000_000_000,
    }
}
pub fn input(t: &Target) -> ListInput {
    ListInput {
        target: t.clone(),
        before: 0,
        limit: 100,
        unread_only: false,
    }
}
fn same(page: &app::Page) -> Vec<notifications::domain::Snapshot> {
    page.items.iter().map(|i| i.snapshot().clone()).collect()
}
pub async fn conformance<T: Store + 'static>(store: Arc<T>) {
    let s = events::dispatcher::Control::default();
    let t = target();
    let d = delivery(&t);
    let at = d.occurred_at;
    assert!(store.media_enabled(&s, &t).await.unwrap());
    assert!(store.list(&s, input(&t)).await.unwrap().items.is_empty());
    let mut tasks = vec![];
    for _ in 0..16 {
        let store = store.clone();
        let d = d.clone();
        tasks.push(tokio::spawn(async move {
            store
                .deliver(&events::dispatcher::Control::default(), d, at)
                .await
                .unwrap()
        }));
    }
    for task in tasks {
        task.await.unwrap();
    }
    let first = store.list(&s, input(&t)).await.unwrap();
    assert_eq!(first.items.len(), 1);
    assert_eq!(first.unread_count, 1);
    assert_eq!(first.items[0].snapshot().sequence, 1);
    store.deliver(&s, d.clone(), at + 99).await.unwrap();
    assert_eq!(
        same(&first),
        same(&store.list(&s, input(&t)).await.unwrap())
    );
    let mut changed = d.clone();
    changed.file_id = id();
    assert_eq!(
        store
            .deliver(&s, changed, at)
            .await
            .unwrap_err()
            .public()
            .code
            .as_deref(),
        Some("notifications.event_conflict")
    );
    let read = store.mark_read(&s, &t, &d.event_id, at + 1).await.unwrap();
    assert_eq!(read.snapshot().read_at, Some(at + 1));
    assert_eq!(
        store.mark_read(&s, &t, &d.event_id, at + 99).await.unwrap(),
        read
    );
    let wrong_principal = Target {
        actor: id(),
        account: t.account.clone(),
    };
    let wrong_account = Target {
        actor: t.actor.clone(),
        account: id(),
    };
    for foreign in [&wrong_principal, &wrong_account] {
        assert!(
            store
                .list(&s, input(foreign))
                .await
                .unwrap()
                .items
                .is_empty()
        );
        assert!(store.mark_read(&s, foreign, &d.event_id, at).await.is_err());
    }
    store.set_media_enabled(&s, &t, false).await.unwrap();
    assert!(!store.media_enabled(&s, &t).await.unwrap());
    let suppressed = delivery(&t);
    store.deliver(&s, suppressed.clone(), at + 2).await.unwrap();
    store.set_media_enabled(&s, &t, true).await.unwrap();
    store.deliver(&s, suppressed.clone(), at + 3).await.unwrap();
    assert_eq!(store.list(&s, input(&t)).await.unwrap().items.len(), 1);
    let mut changed = suppressed;
    changed.occurred_at += 1;
    assert!(store.deliver(&s, changed, at + 4).await.is_err());
    let mut second = delivery(&t);
    second.occurred_at -= 100;
    store.deliver(&s, second.clone(), at + 4).await.unwrap();
    let mut page_input = input(&t);
    page_input.limit = 1;
    let page = store.list(&s, page_input.clone()).await.unwrap();
    assert_eq!(page.items[0].snapshot().id, second.event_id);
    assert_eq!(page.next, Some(2));
    assert_eq!(page.unread_count, 1);
    page_input.before = page.next.unwrap();
    let older = store.list(&s, page_input.clone()).await.unwrap();
    assert_eq!(older.items[0], read);
    assert_eq!(older.unread_count, 1);
    assert!(older.next.is_none());
    page_input.before = 0;
    page_input.unread_only = true;
    let unread = store.list(&s, page_input.clone()).await.unwrap();
    assert_eq!(unread.items.len(), 1);
    assert_eq!(unread.unread_count, 1);
    assert!(unread.next.is_none());
    page_input.before = 2;
    let empty = store.list(&s, page_input).await.unwrap();
    assert!(empty.items.is_empty());
    assert_eq!(empty.unread_count, 1);
    assert!(store.media_enabled(&s, &wrong_account).await.unwrap());
    let canceled = events::dispatcher::Control::default();
    canceled.cancel();
    assert!(store.deliver(&canceled, delivery(&t), at).await.is_err());
    assert!(
        store
            .mark_read(&canceled, &t, &second.event_id, at)
            .await
            .is_err()
    );
    assert!(store.set_media_enabled(&canceled, &t, false).await.is_err());
    assert!(store.media_enabled(&s, &t).await.unwrap());
    assert!(store.mark_read(&s, &t, &id(), at).await.is_err());
    assert!(store.deliver(&s, delivery(&t), MAX_TIME + 1).await.is_err());
    s.check().unwrap();
    let mut writers = vec![];
    for index in 0..16 {
        let store = store.clone();
        let t = t.clone();
        writers.push(tokio::spawn(async move {
            let s = events::dispatcher::Control::default();
            let d = delivery(&t);
            store.deliver(&s, d.clone(), at + 10).await.unwrap();
            if index % 2 == 0 {
                store.mark_read(&s, &t, &d.event_id, at + 11).await.unwrap();
            }
        }));
    }
    for _ in 0..24 {
        let page = store.list(&s, input(&t)).await.unwrap();
        assert_eq!(
            page.unread_count,
            page.items
                .iter()
                .filter(|i| i.snapshot().read_at.is_none())
                .count() as u64
        );
        tokio::task::yield_now().await;
    }
    for writer in writers {
        writer.await.unwrap();
    }
}
