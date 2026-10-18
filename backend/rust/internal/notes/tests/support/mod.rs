use kernel::{
    Failure, Kind,
    context::Metadata,
    id::{Generator, Id, RandomIds},
};
use notes::{
    app::ports::{Scope, Store},
    domain::{Action, Write},
};
use std::{
    collections::BTreeSet,
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
};
#[derive(Default)]
pub struct Control(pub AtomicBool);
impl Scope for Control {
    fn check(&self) -> Result<(), Failure> {
        if self.0.load(Ordering::Acquire) {
            Err(Failure::new(Kind::Canceled, "", "canceled"))
        } else {
            Ok(())
        }
    }
    fn metadata(&self) -> Metadata {
        Metadata::default()
    }
}
pub fn id() -> Id {
    RandomIds.new_id().unwrap()
}
pub fn create(title: &str) -> Write {
    Write {
        command_id: id(),
        action: Action::Create,
        note_id: None,
        title: title.into(),
        expected: 0,
    }
}
pub async fn conformance(store: Arc<dyn Store>) {
    let actor = id();
    let account = id();
    let scope = Control::default();
    let input = create("SECRET Café <>&\u{2028}\u{2029}\0 🧩");
    let mut tasks = Vec::new();
    for _ in 0..12 {
        let store = store.clone();
        let (a, p, w) = (account.clone(), actor.clone(), input.clone());
        tasks.push(tokio::spawn(async move {
            store
                .apply(&Control::default(), &a, &p, w, Some(id()))
                .await
        }));
    }
    let mut results = Vec::new();
    for task in tasks {
        results.push(task.await.unwrap().unwrap());
    }
    assert!(results.iter().all(|r| r == &results[0]));
    let first = results.remove(0);
    let note = first.snapshot().note_id.clone();
    assert_eq!(
        store.list(&scope, &account, None, 100).await.unwrap().len(),
        1
    );
    let mut different = input.clone();
    different.title = "different".into();
    assert_eq!(
        store
            .apply(&scope, &account, &actor, different, None)
            .await
            .unwrap_err()
            .kind(),
        Kind::Conflict
    );
    let update = Write {
        command_id: id(),
        action: Action::Update,
        note_id: Some(note.clone()),
        title: "Same values still increment".into(),
        expected: 1,
    };
    let updated = store
        .apply(&scope, &account, &actor, update.clone(), None)
        .await
        .unwrap();
    assert_eq!(updated.snapshot().version, 2);
    assert_eq!(
        store
            .apply(&scope, &account, &actor, input.clone(), None)
            .await
            .unwrap(),
        first
    );
    let mut contenders = Vec::new();
    for _ in 0..2 {
        let store = store.clone();
        let (a, p) = (account.clone(), actor.clone());
        let w = Write {
            command_id: id(),
            expected: 2,
            ..update.clone()
        };
        let command = w.command_id.clone();
        contenders.push((
            command,
            tokio::spawn(async move { store.apply(&Control::default(), &a, &p, w, None).await }),
        ));
    }
    let mut won = 0;
    for (command, task) in contenders {
        match task.await.unwrap() {
            Ok(r) => {
                won += 1;
                assert_eq!(r.snapshot().version, 3);
            }
            Err(e) => {
                assert_eq!(e.kind(), Kind::Conflict);
                assert!(
                    store
                        .receipt(&scope, &account, &actor, &command)
                        .await
                        .unwrap()
                        .is_none()
                );
            }
        }
    }
    assert_eq!(won, 1);
    let delete = Write {
        command_id: id(),
        action: Action::Delete,
        note_id: Some(note.clone()),
        title: String::new(),
        expected: 3,
    };
    let removed = store
        .apply(&scope, &account, &actor, delete.clone(), None)
        .await
        .unwrap();
    assert_eq!(removed.snapshot().version, 4);
    for (w, r) in [
        (input.clone(), first.clone()),
        (update, updated),
        (delete, removed),
    ] {
        assert_eq!(
            store
                .apply(&scope, &account, &actor, w, None)
                .await
                .unwrap(),
            r
        );
    }
    assert!(store.get(&scope, &account, &note).await.unwrap().is_none());
    assert!(
        store
            .receipt(&scope, &id(), &actor, &input.command_id)
            .await
            .unwrap()
            .is_none()
    );
    assert!(
        store
            .receipt(&scope, &account, &id(), &input.command_id)
            .await
            .unwrap()
            .is_none()
    );
    let separate = id();
    let other = store
        .apply(&scope, &separate, &actor, input.clone(), Some(id()))
        .await
        .unwrap();
    assert_ne!(other.snapshot().note_id, note);
    let mut expected = BTreeSet::new();
    for _ in 0..5 {
        let r = store
            .apply(&scope, &account, &actor, create("Page"), Some(id()))
            .await
            .unwrap();
        expected.insert(r.snapshot().note_id.clone());
    }
    let mut after = None;
    let mut observed = Vec::new();
    loop {
        let page = store
            .list(&scope, &account, after.as_ref(), 2)
            .await
            .unwrap();
        if page.is_empty() {
            break;
        }
        after = page.last().map(|n| n.id().clone());
        observed.extend(page.into_iter().map(|n| n.id().clone()));
    }
    assert_eq!(observed, expected.into_iter().collect::<Vec<_>>());
    let cancelled = Control(AtomicBool::new(true));
    let input = create("Canceled");
    assert!(
        store
            .apply(&cancelled, &account, &actor, input.clone(), Some(id()))
            .await
            .is_err()
    );
    assert!(
        store
            .receipt(&scope, &account, &actor, &input.command_id)
            .await
            .unwrap()
            .is_none()
    );
}
