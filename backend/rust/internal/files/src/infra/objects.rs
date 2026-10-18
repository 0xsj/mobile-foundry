//! Immutable, verified bytes. Directory jobs remain owned across caller cancellation.
use crate::{
    app::{
        self,
        ports::{Objects, PortFuture, Scope},
    },
    domain::{self, File, Snapshot},
};
use kernel::Failure;
use sha2::{Digest, Sha256};
use std::{
    collections::HashMap,
    fs::{self, File as Disk, OpenOptions},
    io::{Read, Write},
    os::unix::fs::{DirBuilderExt, OpenOptionsExt},
    path::{Path, PathBuf},
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, Ordering},
    },
};
use tokio::{
    sync::{Mutex as AsyncMutex, Semaphore, oneshot},
    task::JoinHandle,
};
pub fn validate(s: &Snapshot, data: &[u8]) -> Result<(), Failure> {
    File::restore(s.clone())?;
    if data.len() as u64 != s.spec.byte_size
        || format!("{:x}", Sha256::digest(data)) != s.spec.sha256
    {
        return Err(app::content());
    }
    Ok(())
}
fn key(s: &Snapshot) -> String {
    format!("{}-{}", s.account_id, s.id)
}
#[derive(Default)]
pub struct Memory {
    state: Mutex<(HashMap<String, Vec<u8>>, usize)>,
}
impl Objects for Memory {
    fn put<'a>(&'a self, s: &'a dyn Scope, f: &'a Snapshot, bytes: Vec<u8>) -> PortFuture<'a, ()> {
        Box::pin(async move {
            s.check()?;
            validate(f, &bytes)?;
            let mut state = self.state.lock().map_err(|_| app::storage())?;
            if let Some(prior) = state.0.get(&key(f)) {
                return validate(f, prior);
            }
            if state.1 + bytes.len() > 64 * 1024 * 1024 {
                return Err(app::storage());
            }
            s.check()?;
            state.1 += bytes.len();
            state.0.insert(key(f), bytes);
            Ok(())
        })
    }
    fn get<'a>(&'a self, s: &'a dyn Scope, f: &'a Snapshot) -> PortFuture<'a, Vec<u8>> {
        Box::pin(async move {
            s.check()?;
            let state = self.state.lock().map_err(|_| app::storage())?;
            let bytes = state.0.get(&key(f)).ok_or_else(app::object_missing)?;
            validate(f, bytes).map_err(|e| domain::invalid_state().with_cause(e))?;
            Ok(bytes.clone())
        })
    }
    fn delete<'a>(&'a self, s: &'a dyn Scope, f: &'a Snapshot) -> PortFuture<'a, ()> {
        Box::pin(async move {
            s.check()?;
            File::restore(f.clone())?;
            let mut state = self.state.lock().map_err(|_| app::storage())?;
            if let Some(bytes) = state.0.remove(&key(f)) {
                state.1 -= bytes.len();
            }
            Ok(())
        })
    }
}
pub struct Directory {
    root: PathBuf,
    work: Arc<Semaphore>,
    closed: AtomicBool,
    failed: AtomicBool,
    jobs: AsyncMutex<Vec<JoinHandle<()>>>,
}
impl Directory {
    pub fn new(root: &str) -> Result<Self, Failure> {
        if root.is_empty() {
            return Err(app::invalid());
        }
        let root = std::path::absolute(root).map_err(|e| app::storage().with_cause(e))?;
        fs::DirBuilder::new()
            .recursive(true)
            .mode(0o700)
            .create(&root)
            .map_err(|e| app::storage().with_cause(e))?;
        Ok(Self {
            root,
            work: Arc::new(Semaphore::new(4)),
            closed: AtomicBool::new(false),
            failed: AtomicBool::new(false),
            jobs: AsyncMutex::new(vec![]),
        })
    }
    async fn run(
        &self,
        s: &dyn Scope,
        op: impl FnOnce() -> Result<Vec<u8>, Failure> + Send + 'static,
    ) -> Result<Vec<u8>, Failure> {
        s.check()?;
        let mut jobs = self.jobs.lock().await;
        if self.closed.load(Ordering::Acquire) {
            return Err(app::storage());
        }
        jobs.retain(|j| !j.is_finished());
        let permit = self
            .work
            .clone()
            .try_acquire_owned()
            .map_err(|_| app::storage())?;
        let (send, receive) = oneshot::channel();
        jobs.push(tokio::task::spawn_blocking(move || {
            let _permit = permit;
            let _ = send.send(op());
        }));
        drop(jobs);
        let result = receive.await.map_err(|_| app::storage())?;
        s.check()?;
        result
    }
    pub async fn close(&self) -> Result<(), Failure> {
        self.closed.store(true, Ordering::Release);
        let mut jobs = self.jobs.lock().await;
        while let Some(job) = jobs.last_mut() {
            if job.await.is_err() {
                self.failed.store(true, Ordering::Release);
            }
            jobs.pop();
        }
        if self.failed.load(Ordering::Acquire) {
            Err(app::storage())
        } else {
            Ok(())
        }
    }
}
fn get(root: &Path, f: &Snapshot) -> Result<Vec<u8>, Failure> {
    File::restore(f.clone())?;
    let disk = Disk::open(root.join(key(f))).map_err(|e| {
        if e.kind() == std::io::ErrorKind::NotFound {
            app::object_missing()
        } else {
            app::storage().with_cause(e)
        }
    })?;
    let mut bytes = vec![];
    disk.take((domain::MAX_BYTES + 1) as u64)
        .read_to_end(&mut bytes)
        .map_err(|e| app::storage().with_cause(e))?;
    validate(f, &bytes).map_err(|e| domain::invalid_state().with_cause(e))?;
    Ok(bytes)
}
fn sync(root: &Path) -> Result<(), Failure> {
    Disk::open(root)
        .and_then(|f| f.sync_all())
        .map_err(|e| app::storage().with_cause(e))
}
struct Temporary(PathBuf);
impl Drop for Temporary {
    fn drop(&mut self) {
        let _ = fs::remove_file(&self.0);
    }
}
impl Objects for Directory {
    fn put<'a>(&'a self, s: &'a dyn Scope, f: &'a Snapshot, bytes: Vec<u8>) -> PortFuture<'a, ()> {
        Box::pin(async move {
            validate(f, &bytes)?;
            let root = self.root.clone();
            let f = f.clone();
            self.run(s, move || {
                use kernel::id::Generator;
                let temp =
                    Temporary(root.join(format!(".upload-{}", kernel::id::RandomIds.new_id()?)));
                let mut disk = OpenOptions::new()
                    .write(true)
                    .create_new(true)
                    .mode(0o600)
                    .open(&temp.0)
                    .map_err(|e| app::storage().with_cause(e))?;
                disk.write_all(&bytes)
                    .and_then(|_| disk.sync_all())
                    .map_err(|e| app::storage().with_cause(e))?;
                drop(disk);
                if let Err(e) = fs::hard_link(&temp.0, root.join(key(&f))) {
                    if e.kind() == std::io::ErrorKind::AlreadyExists {
                        get(&root, &f)?;
                        return Ok(vec![]);
                    }
                    return Err(app::storage().with_cause(e));
                }
                sync(&root)?;
                Ok(vec![])
            })
            .await
            .map(|_| ())
        })
    }
    fn get<'a>(&'a self, s: &'a dyn Scope, f: &'a Snapshot) -> PortFuture<'a, Vec<u8>> {
        Box::pin(async move {
            let root = self.root.clone();
            let f = f.clone();
            self.run(s, move || get(&root, &f)).await
        })
    }
    fn delete<'a>(&'a self, s: &'a dyn Scope, f: &'a Snapshot) -> PortFuture<'a, ()> {
        Box::pin(async move {
            File::restore(f.clone())?;
            let root = self.root.clone();
            let f = f.clone();
            self.run(s, move || {
                if let Err(e) = fs::remove_file(root.join(key(&f))) {
                    if e.kind() != std::io::ErrorKind::NotFound {
                        return Err(app::storage().with_cause(e));
                    }
                }
                sync(&root)?;
                Ok(vec![])
            })
            .await
            .map(|_| ())
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[tokio::test]
    async fn canceled_caller_does_not_release_or_detach_blocking_work() {
        let directory = Directory {
            root: PathBuf::new(),
            work: Arc::new(Semaphore::new(4)),
            closed: AtomicBool::new(false),
            failed: AtomicBool::new(false),
            jobs: AsyncMutex::new(vec![]),
        };
        let scope = events::dispatcher::Control::default();
        let (entered, started) = oneshot::channel();
        let (release, wait) = std::sync::mpsc::channel();
        let mut work = Box::pin(directory.run(&scope, move || {
            let _ = entered.send(());
            let _ = wait.recv();
            Ok(vec![])
        }));
        tokio::select! { _=started=>{}, _=&mut work=>panic!("work finished before release") }
        drop(work);
        assert_eq!(directory.work.available_permits(), 3);
        let mut close = Box::pin(directory.close());
        std::future::poll_fn(|cx| {
            assert!(close.as_mut().poll(cx).is_pending());
            std::task::Poll::Ready(())
        })
        .await;
        release.send(()).unwrap();
        close.await.unwrap();
        assert_eq!(directory.work.available_permits(), 4);
        assert!(directory.jobs.lock().await.is_empty());
    }
    #[tokio::test]
    async fn canceled_close_retains_only_unjoined_tasks() {
        let directory = Directory {
            root: PathBuf::new(),
            work: Arc::new(Semaphore::new(4)),
            closed: AtomicBool::new(false),
            failed: AtomicBool::new(false),
            jobs: AsyncMutex::new(vec![]),
        };
        let (release, mut wait) = tokio::sync::watch::channel(false);
        let pending = tokio::spawn(async move {
            let _ = wait.changed().await;
        });
        let done = tokio::spawn(async {});
        while !done.is_finished() {
            tokio::task::yield_now().await;
        }
        directory.jobs.lock().await.extend([pending, done]);
        let mut close = Box::pin(directory.close());
        std::future::poll_fn(|cx| {
            assert!(close.as_mut().poll(cx).is_pending());
            std::task::Poll::Ready(())
        })
        .await;
        drop(close);
        assert_eq!(directory.jobs.lock().await.len(), 1);
        release.send_replace(true);
        directory.close().await.unwrap();
        assert!(directory.jobs.lock().await.is_empty());
        assert!(
            directory
                .run(&events::dispatcher::Control::default(), || Ok(vec![]))
                .await
                .is_err()
        );
    }
}
