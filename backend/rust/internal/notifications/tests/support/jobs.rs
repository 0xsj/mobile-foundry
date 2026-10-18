use events::dispatcher::Control;
use kernel::{
    Kind,
    id::{Generator, Id, RandomIds},
};
use notifications::{
    app::push_jobs::{self, JobResult, Owner, PushJobs},
    domain::push_job::{Code, Intent, LEASE, LIFETIME, PushJob, Status},
};
use std::sync::Arc;
pub const AT: u64 = 1_800_000_000_000_000;
pub fn id() -> Id {
    RandomIds.new_id().unwrap()
}
pub fn job() -> PushJob {
    PushJob::new(
        Intent {
            id: id(),
            installation_id: id(),
            principal_id: id(),
            session_id: id(),
            installation_version: 1,
            retry_of: None,
            duplicate_risk_accepted: false,
        },
        AT,
    )
    .unwrap()
}
pub fn owner(j: &PushJob) -> Owner {
    let i = &j.snapshot().intent;
    Owner {
        id: i.id.clone(),
        installation_id: i.installation_id.clone(),
        principal_id: i.principal_id.clone(),
        session_id: i.session_id.clone(),
    }
}
pub fn result(started: bool, status: Status, code: Code) -> JobResult {
    JobResult {
        started,
        status,
        code,
        retired: false,
    }
}
pub async fn conformance(store: Arc<dyn PushJobs>) {
    let s = Control::default();
    let original = job();
    let key = original.snapshot().intent.id.clone();
    let mut tasks = tokio::task::JoinSet::new();
    for _ in 0..12 {
        let store = store.clone();
        let j = original.clone();
        tasks.spawn(async move { store.queue(&Control::default(), j).await.unwrap() });
    }
    while let Some(done) = tasks.join_next().await {
        assert_eq!(done.unwrap(), original);
    }
    let mut changed = original.snapshot().intent.clone();
    changed.installation_version = 2;
    assert_eq!(
        store
            .queue(&s, PushJob::new(changed, AT).unwrap())
            .await
            .unwrap_err()
            .kind(),
        Kind::Conflict
    );
    assert!(store.find(&s, &id()).await.unwrap().is_none());
    let canceled = Control::default();
    canceled.cancel();
    assert!(store.find(&canceled, &key).await.is_err());
    let mut tasks = tokio::task::JoinSet::new();
    for _ in 0..12 {
        let store = store.clone();
        tasks.spawn(async move {
            let token = id();
            (
                token.clone(),
                store.claim(&Control::default(), token, AT).await.unwrap(),
            )
        });
    }
    let mut winner = None;
    while let Some(done) = tasks.join_next().await {
        let (token, claimed) = done.unwrap();
        if let Some((j, true)) = claimed {
            assert!(winner.replace(token).is_none());
            assert_eq!(j.snapshot().attempts, 1);
        }
    }
    let token = winner.unwrap();
    assert_eq!(
        store.begin(&s, &key, &id(), AT).await.unwrap_err().kind(),
        Kind::Conflict
    );
    store.begin(&s, &key, &token, AT).await.unwrap();
    assert!(store.cancel(&s, &owner(&original), AT).await.is_err());
    assert!(
        store
            .finish(
                &s,
                &key,
                &token,
                result(false, Status::Queued, Code::PreflightUnavailable),
                AT
            )
            .await
            .is_err()
    );
    store
        .finish(
            &s,
            &key,
            &token,
            result(true, Status::Accepted, Code::None),
            AT,
        )
        .await
        .unwrap();
    assert!(
        store
            .finish(
                &s,
                &key,
                &token,
                result(true, Status::Accepted, Code::None),
                AT
            )
            .await
            .is_err()
    );
    assert!(store.claim(&s, id(), AT + LEASE).await.unwrap().is_none());
    let stale = job();
    store.queue(&s, stale.clone()).await.unwrap();
    let first = id();
    store.claim(&s, first.clone(), AT).await.unwrap();
    let second = id();
    let (reclaimed, true) = store
        .claim(&s, second.clone(), AT + LEASE)
        .await
        .unwrap()
        .unwrap()
    else {
        panic!("lease not reclaimed")
    };
    assert_eq!(reclaimed.snapshot().attempts, 2);
    assert!(
        store
            .begin(&s, &stale.snapshot().intent.id, &first, AT + LEASE)
            .await
            .is_err()
    );
    let stable = store.cancel(&s, &owner(&stale), AT + LEASE).await.unwrap();
    assert_eq!(
        store
            .cancel(&s, &owner(&stale), AT + LEASE + 1)
            .await
            .unwrap(),
        stable
    );
    let expired = job();
    store.queue(&s, expired.clone()).await.unwrap();
    let (expired, false) = store.claim(&s, id(), AT + LIFETIME).await.unwrap().unwrap() else {
        panic!("expired job leased")
    };
    assert_eq!(expired.snapshot().status, Status::Expired);
    // Only one direct child may be inserted, including under concurrent requests.
    let source = store
        .cancel(
            &s,
            &owner(&{
                let j = job();
                store.queue(&s, j.clone()).await.unwrap();
                j
            }),
            AT,
        )
        .await
        .unwrap();
    let source_before = source.clone();
    let mut candidates = Vec::new();
    for _ in 0..12 {
        let mut i = source.snapshot().intent.clone();
        i.id = id();
        i.retry_of = Some(source.snapshot().intent.id.clone());
        i.installation_version = 2;
        candidates.push(PushJob::new(i, AT).unwrap());
    }
    let mut tasks = tokio::task::JoinSet::new();
    for candidate in candidates {
        let store = store.clone();
        tasks.spawn(async move { store.replace(&Control::default(), candidate).await });
    }
    let mut child = None;
    while let Some(done) = tasks.join_next().await {
        match done.unwrap() {
            Ok(j) => assert!(child.replace(j).is_none()),
            Err(e) => assert_eq!(
                e.public().code.as_deref(),
                Some("notifications.push_job_replacement_exists")
            ),
        }
    }
    let child = child.unwrap();
    assert_eq!(
        store.replacement(&s, &owner(&source)).await.unwrap(),
        Some(child.clone())
    );
    assert_eq!(
        store.find(&s, &source.snapshot().intent.id).await.unwrap(),
        Some(source_before)
    );
    assert_eq!(store.replace(&s, child.clone()).await.unwrap(), child);
    let mut root = child.snapshot().intent.clone();
    root.retry_of = None;
    assert_eq!(
        store
            .queue(&s, PushJob::new(root, AT).unwrap())
            .await
            .unwrap_err()
            .public()
            .code
            .as_deref(),
        Some("notifications.push_job_conflict")
    );
    let mut foreign = owner(&source);
    foreign.session_id = id();
    assert!(store.replacement(&s, &foreign).await.unwrap().is_none());
    assert_eq!(
        store.cancel(&s, &foreign, AT).await.unwrap_err().kind(),
        Kind::NotFound
    );
    store.cancel(&s, &owner(&child), AT).await.unwrap();
    let uncertain = job();
    store.queue(&s, uncertain.clone()).await.unwrap();
    let token = id();
    store.claim(&s, token.clone(), AT).await.unwrap();
    store
        .begin(&s, &uncertain.snapshot().intent.id, &token, AT)
        .await
        .unwrap();
    store
        .finish(
            &s,
            &uncertain.snapshot().intent.id,
            &token,
            result(true, Status::Uncertain, Code::ProviderUnconfirmed),
            AT,
        )
        .await
        .unwrap();
    let mut i = uncertain.snapshot().intent.clone();
    i.id = id();
    i.retry_of = Some(uncertain.snapshot().intent.id.clone());
    assert!(
        store
            .replace(&s, PushJob::new(i.clone(), AT).unwrap())
            .await
            .is_err()
    );
    i.duplicate_risk_accepted = true;
    let child = store
        .replace(&s, PushJob::new(i, AT).unwrap())
        .await
        .unwrap();
    store.cancel(&s, &owner(&child), AT).await.unwrap();
    let preflight = job();
    store.queue(&s, preflight.clone()).await.unwrap();
    let mut now = AT;
    for n in 1..=5 {
        let token = id();
        let (claimed, true) = store.claim(&s, token.clone(), now).await.unwrap().unwrap() else {
            panic!("backoff did not become due")
        };
        assert_eq!(claimed.snapshot().attempts, n);
        store
            .finish(
                &s,
                &preflight.snapshot().intent.id,
                &token,
                result(false, Status::Queued, Code::PreflightUnavailable),
                now,
            )
            .await
            .unwrap();
        let next = store
            .find(&s, &preflight.snapshot().intent.id)
            .await
            .unwrap()
            .unwrap();
        if n < 5 {
            assert!(store.claim(&s, id(), now).await.unwrap().is_none());
            now = next.snapshot().next_at;
        } else {
            assert_eq!(next.snapshot().status, Status::Failed);
        }
    }
    assert!(
        !format!(
            "{original:?} {:?} {:?}",
            original.snapshot(),
            original.snapshot().intent
        )
        .contains(key.as_str())
    );
    let _ = push_jobs::lease(&original);
    for _ in 0..12 {
        let j = job();
        let key = j.snapshot().intent.id.clone();
        store.queue(&s, j.clone()).await.unwrap();
        let token = id();
        store.claim(&s, token.clone(), AT).await.unwrap();
        let gate = Arc::new(tokio::sync::Barrier::new(2));
        let (canceled, begun) = tokio::join!(
            async {
                gate.wait().await;
                store.cancel(&s, &owner(&j), AT).await
            },
            async {
                gate.wait().await;
                store.begin(&s, &key, &token, AT).await
            }
        );
        assert_ne!(
            canceled.is_ok(),
            begun.is_ok(),
            "cancel and Begin must serialize to one winner"
        );
        if begun.is_ok() {
            assert_eq!(
                store
                    .find(&s, &key)
                    .await
                    .unwrap()
                    .unwrap()
                    .snapshot()
                    .status,
                Status::Sending
            );
            store
                .finish(
                    &s,
                    &key,
                    &token,
                    result(true, Status::Accepted, Code::None),
                    AT,
                )
                .await
                .unwrap();
        } else {
            assert_eq!(
                store
                    .find(&s, &key)
                    .await
                    .unwrap()
                    .unwrap()
                    .snapshot()
                    .status,
                Status::Canceled
            );
        }
    }
}
