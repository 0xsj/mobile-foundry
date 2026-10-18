use account::{
    app::{
        Dependencies,
        command::{Bootstrap, Create, Rename},
        ports::{Metadata, Scope, Store},
        query::{Get, List},
    },
    domain::{self, Action},
    infra::memory::MemoryStore,
};
use kernel::{
    Failure, Kind,
    clock::Clock,
    id::{Generator, Id, RandomIds},
};
use std::{
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
    time::{Duration, SystemTime, UNIX_EPOCH},
};
#[derive(Default)]
struct TestScope(AtomicBool);
impl Scope for TestScope {
    fn check(&self) -> Result<(), Failure> {
        if self.0.load(Ordering::Acquire) {
            Err(Failure::new(Kind::Canceled, "", "canceled"))
        } else {
            Ok(())
        }
    }
    fn metadata(&self) -> Metadata {
        Metadata {
            request_id: "request-test".into(),
            correlation_id: Some("flow-test".into()),
        }
    }
}
struct Fixed;
impl Clock for Fixed {
    fn now(&self) -> SystemTime {
        UNIX_EPOCH + Duration::from_secs(1_700_000_000)
    }
}
#[derive(Default)]
struct FactIds(AtomicBool);
impl Generator for FactIds {
    fn new_id(&self) -> Result<Id, Failure> {
        if self.0.load(Ordering::Acquire) {
            Err(domain::invalid_state())
        } else {
            RandomIds.new_id()
        }
    }
}
fn setup() -> (Dependencies, Arc<MemoryStore>, Arc<FactIds>) {
    let ids = Arc::new(FactIds::default());
    let store = Arc::new(MemoryStore::new(ids.clone()));
    (
        Dependencies {
            store: store.clone(),
            directory: store.clone(),
            clock: Arc::new(Fixed),
            ids: Arc::new(RandomIds),
        },
        store,
        ids,
    )
}
#[tokio::test]
async fn invalid_integration_metadata_leaves_mapping_aggregate_and_outbox_unchanged() {
    use events::dispatcher::Outbox;
    struct InvalidMetadata;
    impl Scope for InvalidMetadata {
        fn check(&self) -> Result<(), Failure> {
            Ok(())
        }
        fn metadata(&self) -> Metadata {
            Metadata {
                request_id: "invalid token".into(),
                correlation_id: None,
            }
        }
    }
    let (deps, store, _) = setup();
    let actor = RandomIds.new_id().unwrap();
    let scope = TestScope::default();
    assert!(
        Bootstrap(deps.clone())
            .handle(&InvalidMetadata, &actor, "Personal")
            .await
            .is_err()
    );
    assert!(store.pending(&scope, 32).await.unwrap().is_empty());
    assert!(
        List(deps.clone())
            .handle(&scope, &actor, None, 20)
            .await
            .unwrap()
            .accounts
            .is_empty()
    );
    let result = Bootstrap(deps)
        .handle(&scope, &actor, "First successful name")
        .await
        .unwrap();
    assert!(result.created);
    let messages = store.pending(&scope, 32).await.unwrap();
    assert_eq!(messages.len(), 1);
    assert_eq!(messages[0].id, store.facts().unwrap()[0].id);
}
#[tokio::test]
async fn bootstrap_keeps_current_personal_account_and_scopes_lists() {
    let (deps, store, _) = setup();
    let scope = TestScope::default();
    let actor = RandomIds.new_id().unwrap();
    let other = RandomIds.new_id().unwrap();
    let ordinary = Create(deps.clone())
        .handle(&scope, &actor, "Ordinary")
        .await
        .unwrap();
    let first = Bootstrap(deps.clone())
        .handle(&scope, &actor, "Personal")
        .await
        .unwrap();
    assert!(first.created);
    assert_ne!(first.account.snapshot().id, ordinary.snapshot().id);
    let renamed = Rename(deps.clone())
        .handle(&scope, &actor, &first.account.snapshot().id, "Renamed", 1)
        .await
        .unwrap();
    let repeat = Bootstrap(deps.clone())
        .handle(&scope, &actor, "Ignored")
        .await
        .unwrap();
    assert!(!repeat.created);
    assert_eq!(repeat.account.snapshot().name, "Renamed");
    assert_eq!(repeat.account.snapshot().version, 2);
    assert!(
        Bootstrap(deps.clone())
            .handle(&scope, &actor, "")
            .await
            .is_err()
    );
    Create(deps.clone())
        .handle(&scope, &other, "Other")
        .await
        .unwrap();
    assert_eq!(
        Get(deps.clone())
            .handle(&scope, &other, &renamed.snapshot().id)
            .await
            .unwrap_err()
            .kind(),
        Kind::Forbidden
    );
    let page = List(deps.clone())
        .handle(&scope, &actor, None, 1)
        .await
        .unwrap();
    assert_eq!(page.accounts.len(), 1);
    assert!(page.next.is_some());
    let last = List(deps.clone())
        .handle(&scope, &actor, page.next.as_ref(), 1)
        .await
        .unwrap();
    assert_eq!(last.accounts.len(), 1);
    assert!(last.next.is_none());
    assert!(page.accounts[0].snapshot().id < last.accounts[0].snapshot().id);
    assert_eq!(store.facts().unwrap().len(), 4);
    assert_eq!(store.facts().unwrap()[2].fact.action, Action::Renamed);
    assert_eq!(
        store.facts().unwrap()[0].metadata.request_id,
        "request-test"
    );
}
#[tokio::test]
async fn fact_failures_and_cancellation_do_not_partially_commit() {
    let (deps, store, ids) = setup();
    let scope = TestScope::default();
    let actor = RandomIds.new_id().unwrap();
    ids.0.store(true, Ordering::Release);
    assert!(
        Bootstrap(deps.clone())
            .handle(&scope, &actor, "Personal")
            .await
            .is_err()
    );
    assert!(
        List(deps.clone())
            .handle(&scope, &actor, None, 20)
            .await
            .unwrap()
            .accounts
            .is_empty()
    );
    assert!(store.facts().unwrap().is_empty());
    ids.0.store(false, Ordering::Release);
    let first = Bootstrap(deps.clone())
        .handle(&scope, &actor, "Personal")
        .await
        .unwrap();
    assert!(first.created);
    ids.0.store(true, Ordering::Release);
    assert!(
        Rename(deps.clone())
            .handle(&scope, &actor, &first.account.snapshot().id, "Changed", 1)
            .await
            .is_err()
    );
    assert_eq!(
        store
            .get(&scope, &first.account.snapshot().id)
            .await
            .unwrap()
            .unwrap()
            .snapshot()
            .version,
        1
    );
    assert_eq!(store.facts().unwrap().len(), 1);
    scope.0.store(true, Ordering::Release);
    assert_eq!(
        Create(deps)
            .handle(&scope, &actor, "Canceled")
            .await
            .unwrap_err()
            .kind(),
        Kind::Canceled
    );
    assert_eq!(store.facts().unwrap().len(), 1);
}
#[tokio::test(flavor = "multi_thread", worker_threads = 4)]
async fn concurrent_bootstrap_and_store_cas_have_one_winner() {
    let (deps, store, _) = setup();
    let actor = RandomIds.new_id().unwrap();
    let barrier = Arc::new(tokio::sync::Barrier::new(8));
    let mut tasks = Vec::new();
    for index in 0..8 {
        let (deps, actor, barrier) = (deps.clone(), actor.clone(), barrier.clone());
        tasks.push(tokio::spawn(async move {
            barrier.wait().await;
            Bootstrap(deps)
                .handle(&TestScope::default(), &actor, &format!("Personal {index}"))
                .await
                .unwrap()
        }));
    }
    let mut winners = 0;
    let mut accounts = Vec::new();
    for task in tasks {
        let result = task.await.unwrap();
        winners += usize::from(result.created);
        accounts.push(result.account);
    }
    assert_eq!(winners, 1);
    assert!(
        accounts
            .iter()
            .all(|a| a.snapshot() == accounts[0].snapshot())
    );
    assert_eq!(store.facts().unwrap().len(), 1);
    let barrier = Arc::new(tokio::sync::Barrier::new(8));
    let mut tasks = Vec::new();
    for index in 0..8 {
        let next = accounts[0]
            .rename(&format!("Rename {index}"), Fixed.now())
            .unwrap();
        let fact = next.fact(Action::Renamed, &actor).unwrap();
        let (store, barrier) = (store.clone(), barrier.clone());
        tasks.push(tokio::spawn(async move {
            barrier.wait().await;
            store.rename(&TestScope::default(), next, 1, fact).await
        }));
    }
    let mut winners = 0;
    for task in tasks {
        match task.await.unwrap() {
            Ok(()) => winners += 1,
            Err(error) => assert_eq!(error.kind(), Kind::Conflict),
        }
    }
    assert_eq!(winners, 1);
    assert_eq!(store.facts().unwrap().len(), 2);
}
