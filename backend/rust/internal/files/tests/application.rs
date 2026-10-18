mod support;
use files::{
    app::{self, Action, Command, Target, ports::Store},
    infra::{
        memory::MemoryStore,
        objects::{Directory, Memory},
    },
};
use kernel::{
    Failure,
    id::{Generator, Id, RandomIds},
};
use std::sync::{
    Arc,
    atomic::{AtomicBool, Ordering},
};
struct Failing(AtomicBool);
impl Generator for Failing {
    fn new_id(&self) -> Result<Id, Failure> {
        if self.0.load(Ordering::Acquire) {
            Err(Failure::new(
                kernel::Kind::Unavailable,
                "test.allocation",
                "allocation refused",
            ))
        } else {
            RandomIds.new_id()
        }
    }
}
#[tokio::test]
async fn memory_lifecycle_receipts_cleanup_and_ordered_facts() {
    support::conformance(
        Arc::new(MemoryStore::new(Arc::new(RandomIds))),
        Arc::new(Memory::default()),
    )
    .await;
}
#[tokio::test]
async fn required_event_failure_does_not_commit_metadata_or_receipt() {
    let ids = Arc::new(Failing(AtomicBool::new(true)));
    let store = MemoryStore::new(ids.clone());
    let s = events::dispatcher::Control::default();
    let t = Target {
        actor: RandomIds.new_id().unwrap(),
        account: RandomIds.new_id().unwrap(),
    };
    let c = Command {
        action: Action::Request,
        key: "request".into(),
        file_id: None,
        spec: Some(support::spec()),
        expected: 0,
    };
    assert!(
        store
            .apply(&s, &t, c.clone(), Some(RandomIds.new_id().unwrap()), 1)
            .await
            .is_err()
    );
    assert!(
        store
            .list(&s, &t.account, None, 100)
            .await
            .unwrap()
            .is_empty()
    );
    assert!(store.receipt(&s, &t, "request").await.unwrap().is_none());
    ids.0.store(false, Ordering::Release);
    assert!(
        store
            .apply(&s, &t, c, Some(RandomIds.new_id().unwrap()), 1)
            .await
            .is_ok()
    );
}
#[tokio::test]
async fn directory_objects_survive_reopen_and_detect_corruption() {
    use app::ports::Objects;
    let root =
        std::env::temp_dir().join(format!("mobile-rust-files-{}", RandomIds.new_id().unwrap()));
    struct Owned(std::path::PathBuf);
    impl Drop for Owned {
        fn drop(&mut self) {
            let _ = std::fs::remove_dir_all(&self.0);
        }
    }
    let _owned = Owned(root.clone());
    let directory = Arc::new(Directory::new(root.to_str().unwrap()).unwrap());
    support::conformance(
        Arc::new(MemoryStore::new(Arc::new(RandomIds))),
        directory.clone(),
    )
    .await;
    let s = events::dispatcher::Control::default();
    let f = files::domain::File::new(
        RandomIds.new_id().unwrap(),
        RandomIds.new_id().unwrap(),
        RandomIds.new_id().unwrap(),
        support::spec(),
        1,
    )
    .unwrap();
    directory
        .put(&s, f.snapshot(), b"abc".to_vec())
        .await
        .unwrap();
    directory.close().await.unwrap();
    let reopened = Directory::new(root.to_str().unwrap()).unwrap();
    assert_eq!(reopened.get(&s, f.snapshot()).await.unwrap(), b"abc");
    let path = root.join(format!("{}-{}", f.snapshot().account_id, f.snapshot().id));
    std::fs::write(path, b"bad").unwrap();
    assert_eq!(
        reopened.get(&s, f.snapshot()).await.unwrap_err().kind(),
        kernel::Kind::Internal
    );
    reopened.close().await.unwrap();
    assert!(reopened.get(&s, f.snapshot()).await.is_err());
}
