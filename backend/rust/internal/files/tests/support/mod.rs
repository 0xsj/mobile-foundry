use files::{
    app::{
        self, Dependencies, Target,
        command::{CompleteUpload, DeleteFile, RequestUpload, UploadContent},
        ports::{AccountAccess, Objects, PortFuture, Scope, Store},
        query::{DownloadFile, GetFile, GetReceipt},
    },
    domain::{Snapshot, Spec, Status},
};
use kernel::id::{Generator, RandomIds};
use std::{
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
    time::{Duration, SystemTime, UNIX_EPOCH},
};
pub struct Fixed;
impl kernel::clock::Clock for Fixed {
    fn now(&self) -> SystemTime {
        UNIX_EPOCH + Duration::from_secs(1_800_000_000)
    }
}
pub struct Access(pub AtomicBool);
impl AccountAccess for Access {
    fn allowed<'a>(
        &'a self,
        _: &'a dyn Scope,
        _: &'a kernel::id::Id,
        _: &'a kernel::id::Id,
    ) -> PortFuture<'a, bool> {
        Box::pin(async move { Ok(self.0.load(Ordering::Acquire)) })
    }
}
struct Cleanup {
    inner: Arc<dyn Objects>,
    fail: AtomicBool,
}
impl Objects for Cleanup {
    fn put<'a>(&'a self, s: &'a dyn Scope, f: &'a Snapshot, b: Vec<u8>) -> PortFuture<'a, ()> {
        self.inner.put(s, f, b)
    }
    fn get<'a>(&'a self, s: &'a dyn Scope, f: &'a Snapshot) -> PortFuture<'a, Vec<u8>> {
        self.inner.get(s, f)
    }
    fn delete<'a>(&'a self, s: &'a dyn Scope, f: &'a Snapshot) -> PortFuture<'a, ()> {
        Box::pin(async move {
            if self.fail.swap(false, Ordering::AcqRel) {
                return Err(app::storage());
            }
            self.inner.delete(s, f).await
        })
    }
}
pub fn spec() -> Spec {
    Spec {
        name: "SECRET Café <>& 🧩".into(),
        content_type: "image/png".into(),
        byte_size: 3,
        sha256: "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".into(),
    }
}
pub async fn conformance<T: Store + events::dispatcher::Outbox + 'static>(
    store: Arc<T>,
    objects: Arc<dyn Objects>,
) {
    let access = Arc::new(Access(AtomicBool::new(true)));
    let objects = Arc::new(Cleanup {
        inner: objects,
        fail: AtomicBool::new(false),
    });
    let d = Dependencies {
        store: store.clone(),
        objects: objects.clone(),
        access: access.clone(),
        ids: Arc::new(RandomIds),
        clock: Arc::new(Fixed),
    };
    let t = Target {
        actor: RandomIds.new_id().unwrap(),
        account: RandomIds.new_id().unwrap(),
    };
    let s = events::dispatcher::Control::default();
    let request = RequestUpload(d.clone());
    let complete = CompleteUpload(d.clone());
    let upload = UploadContent(d.clone());
    let delete = DeleteFile(d.clone());
    let mut tasks = vec![];
    for _ in 0..12 {
        let request = request.clone();
        let t = t.clone();
        tasks.push(tokio::spawn(async move {
            request
                .handle(
                    &events::dispatcher::Control::default(),
                    &t,
                    spec(),
                    "request".into(),
                )
                .await
                .unwrap()
        }));
    }
    let r = tasks.remove(0).await.unwrap();
    for task in tasks {
        assert_eq!(task.await.unwrap(), r);
    }
    let id = &r.file.id;
    assert_eq!(r.file.status, Status::Pending);
    assert_eq!(
        complete
            .handle(&s, &t, id.clone(), 1, "complete".into())
            .await
            .unwrap_err()
            .public()
            .code
            .as_deref(),
        Some("files.object_missing")
    );
    assert!(
        upload
            .handle(&s, &t, id, "image/png", b"bad".to_vec())
            .await
            .is_err()
    );
    upload
        .handle(&s, &t, id, "image/png", b"abc".to_vec())
        .await
        .unwrap();
    upload
        .handle(&s, &t, id, "image/png", b"abc".to_vec())
        .await
        .unwrap();
    let ready = complete
        .handle(&s, &t, id.clone(), 1, "complete".into())
        .await
        .unwrap();
    assert_eq!(ready.file.version, 2);
    assert_eq!(
        DownloadFile(d.clone()).handle(&s, &t, id).await.unwrap().1,
        b"abc"
    );
    assert_eq!(
        complete
            .handle(&s, &t, id.clone(), 1, "stale".into())
            .await
            .unwrap_err()
            .public()
            .code
            .as_deref(),
        Some("files.version_conflict")
    );
    assert!(store.receipt(&s, &t, "stale").await.unwrap().is_none());
    objects.fail.store(true, Ordering::Release);
    assert!(
        delete
            .handle(&s, &t, id.clone(), 2, "delete".into())
            .await
            .is_err()
    );
    let removed = GetReceipt(d.clone())
        .handle(&s, &t, "delete")
        .await
        .unwrap();
    assert_eq!(removed.file.status, Status::Deleted);
    assert!(GetFile(d.clone()).handle(&s, &t, id).await.is_err());
    assert!(DownloadFile(d.clone()).handle(&s, &t, id).await.is_err());
    assert_eq!(
        delete
            .handle(&s, &t, id.clone(), 2, "delete".into())
            .await
            .unwrap(),
        removed
    );
    assert!(objects.inner.get(&s, &r.file).await.is_err());
    assert_eq!(
        request
            .handle(&s, &t, spec(), "request".into())
            .await
            .unwrap(),
        r
    );
    assert_eq!(
        complete
            .handle(&s, &t, id.clone(), 1, "complete".into())
            .await
            .unwrap(),
        ready
    );
    let mut changed = spec();
    changed.name = "changed".into();
    assert!(
        request
            .handle(&s, &t, changed, "request".into())
            .await
            .is_err()
    );
    let other = Target {
        actor: t.actor.clone(),
        account: RandomIds.new_id().unwrap(),
    };
    assert!(store.get(&s, &other.account, id).await.unwrap().is_none());
    assert!(
        store
            .receipt(&s, &other, "request")
            .await
            .unwrap()
            .is_none()
    );
    access.0.store(false, Ordering::Release);
    assert!(
        request
            .handle(&s, &t, spec(), "request".into())
            .await
            .is_err()
    );
    assert!(GetReceipt(d).handle(&s, &t, "request").await.is_err());
    for version in 1..=3 {
        let pending = store.pending(&s, 32).await.unwrap();
        let event = pending
            .iter()
            .find(|e| e.account_id.as_ref() == Some(&t.account))
            .unwrap();
        assert_eq!(event.version, version);
        store.delivered(&s, &event.id).await.unwrap();
    }
    assert!(
        store
            .pending(&s, 32)
            .await
            .unwrap()
            .iter()
            .all(|e| e.account_id.as_ref() != Some(&t.account))
    );
}
