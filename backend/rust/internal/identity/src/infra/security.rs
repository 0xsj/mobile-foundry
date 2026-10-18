//! Protocol-compatible crypto and bounded process-local credential admission.
use crate::{
    app::{
        self,
        ports::{Issued, Passwords, PortFuture, Purpose, Scope, Tokens},
    },
    domain::{self, Digest, Password, Secret},
};
use argon2::{Algorithm, Argon2, Params, Version};
use base64::{
    Engine,
    engine::general_purpose::{STANDARD_NO_PAD, URL_SAFE_NO_PAD},
};
use kernel::Failure;
use sha2::{Digest as _, Sha256};
use std::{
    collections::HashMap,
    net::IpAddr,
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, Ordering},
    },
    time::{Duration, Instant},
};
use subtle::ConstantTimeEq;
use tokio::sync::Semaphore;
use zeroize::Zeroizing;

const PARAMS: &str = "argon2id$v=19$m=19456,t=2,p=1";
pub struct PasswordAdapter {
    work: Arc<Semaphore>,
    closed: AtomicBool,
    dummy: Secret,
    #[cfg(test)]
    hook: Option<Arc<ComputeHook>>,
}
#[cfg(test)]
struct ComputeHook {
    ready: std::sync::mpsc::SyncSender<()>,
    release: Mutex<std::sync::mpsc::Receiver<()>>,
}
struct StartupScope;
impl Scope for StartupScope {
    fn check(&self) -> Result<(), Failure> {
        Ok(())
    }
    fn metadata(&self) -> app::ports::Metadata {
        Default::default()
    }
}
impl PasswordAdapter {
    pub async fn new() -> Result<Self, Failure> {
        let mut adapter = Self {
            work: Arc::new(Semaphore::new(2)),
            closed: AtomicBool::new(false),
            dummy: Secret::new(String::new()),
            #[cfg(test)]
            hook: None,
        };
        adapter.dummy = adapter
            .hash(
                &StartupScope,
                Password::new(Secret::new("unused-dummy-credential".into()))?,
            )
            .await?;
        Ok(adapter)
    }
    async fn calculate(
        &self,
        scope: &dyn Scope,
        password: Password,
        salt: [u8; 16],
    ) -> Result<Zeroizing<[u8; 32]>, Failure> {
        scope.check()?;
        if self.closed.load(Ordering::Acquire) {
            return Err(app::busy());
        }
        let permit = self
            .work
            .clone()
            .try_acquire_owned()
            .map_err(|_| app::busy())?;
        if self.closed.load(Ordering::Acquire) {
            return Err(app::busy());
        }
        #[cfg(test)]
        let hook = self.hook.clone();
        let key = tokio::task::spawn_blocking(move || {
            let _permit = permit; // Cancellation cannot release capacity while the KDF still runs.
            #[cfg(test)]
            if let Some(hook) = hook {
                let _ = hook.ready.send(());
                let _ = hook.release.lock().expect("test gate").recv();
            }
            let params = Params::new(19456, 2, 1, Some(32)).map_err(|_| domain::invalid_state())?;
            let mut key = Zeroizing::new([0; 32]);
            Argon2::new(Algorithm::Argon2id, Version::V0x13, params)
                .hash_password_into(password.expose().as_bytes(), &salt, &mut *key)
                .map_err(|_| domain::invalid_state())?;
            Ok::<_, Failure>(key)
        })
        .await
        .map_err(|_| domain::invalid_state())??;
        scope.check()?;
        Ok(key)
    }
    /// Stops admission, then waits for both permits, including canceled callers' blocking jobs.
    pub async fn close(&self) -> Result<(), Failure> {
        self.closed.store(true, Ordering::Release);
        let _drained = self
            .work
            .clone()
            .acquire_many_owned(2)
            .await
            .map_err(|_| domain::invalid_state())?;
        Ok(())
    }
}
impl Passwords for PasswordAdapter {
    fn hash<'a>(&'a self, scope: &'a dyn Scope, password: Password) -> PortFuture<'a, Secret> {
        Box::pin(async move {
            scope.check()?;
            let mut salt = [0; 16];
            getrandom::fill(&mut salt)
                .map_err(|cause| domain::invalid_state().with_cause(cause))?;
            let key = self.calculate(scope, password, salt).await?;
            Ok(Secret::new(format!(
                "${PARAMS}${}${}",
                STANDARD_NO_PAD.encode(salt),
                STANDARD_NO_PAD.encode(key.as_slice())
            )))
        })
    }
    fn verify<'a>(
        &'a self,
        scope: &'a dyn Scope,
        hash: Secret,
        password: Password,
    ) -> PortFuture<'a, bool> {
        Box::pin(async move {
            let parts: Vec<_> = hash.expose().split('$').collect();
            if parts.len() != 6 || parts[1..4].join("$") != PARAMS {
                return Err(domain::invalid_state());
            }
            let salt: [u8; 16] = STANDARD_NO_PAD
                .decode(parts[4])
                .map_err(|_| domain::invalid_state())?
                .try_into()
                .map_err(|_| domain::invalid_state())?;
            let expected: Zeroizing<Vec<u8>> = Zeroizing::new(
                STANDARD_NO_PAD
                    .decode(parts[5])
                    .map_err(|_| domain::invalid_state())?,
            );
            if expected.len() != 32 {
                return Err(domain::invalid_state());
            }
            let actual = self.calculate(scope, password, salt).await?;
            Ok(bool::from(actual.as_slice().ct_eq(expected.as_slice())))
        })
    }
    fn dummy<'a>(&'a self, scope: &'a dyn Scope, password: Password) -> PortFuture<'a, ()> {
        Box::pin(async move {
            self.verify(scope, self.dummy.clone(), password)
                .await
                .map(|_| ())
        })
    }
}

#[derive(Default)]
pub struct TokenAdapter;
fn prefix(purpose: Purpose) -> &'static str {
    match purpose {
        Purpose::Access => "a1.",
        Purpose::Refresh => "r1.",
        Purpose::VerifyEmail => "e1.",
        Purpose::ResetPassword => "p1.",
    }
}
impl Tokens for TokenAdapter {
    fn issue(&self, scope: &dyn Scope, purpose: Purpose) -> Result<Issued, Failure> {
        scope.check()?;
        let mut bytes = Zeroizing::new([0u8; 32]);
        getrandom::fill(&mut *bytes).map_err(|cause| domain::invalid_state().with_cause(cause))?;
        let mut value = prefix(purpose).to_owned();
        URL_SAFE_NO_PAD.encode_string(bytes.as_slice(), &mut value);
        let raw = Secret::new(value);
        let digest = self.digest(scope, &raw, purpose)?;
        Ok(Issued { raw, digest })
    }
    fn digest(
        &self,
        scope: &dyn Scope,
        token: &Secret,
        purpose: Purpose,
    ) -> Result<Digest, Failure> {
        scope.check()?;
        let value = token.expose();
        if value.len() != 46 || !value.starts_with(prefix(purpose)) {
            return Err(domain::credential_refused());
        }
        let bytes = Zeroizing::new(
            URL_SAFE_NO_PAD
                .decode(&value[3..])
                .map_err(|_| domain::credential_refused())?,
        );
        if bytes.len() != 32 {
            return Err(domain::credential_refused());
        }
        let encoded: String = Sha256::digest(value.as_bytes())
            .iter()
            .map(|b| format!("{b:02x}"))
            .collect();
        Digest::parse(encoded)
    }
}

#[derive(Default)]
pub struct Gate {
    entries: Mutex<HashMap<(IpAddr, &'static str), Bucket>>,
}
impl app::ports::AttemptGate for Gate {
    fn allow(&self, address: IpAddr, endpoint: &'static str) -> Result<(), Failure> {
        Gate::allow(self, address, endpoint)
    }
}
struct Bucket {
    tokens: f64,
    at: Instant,
}
impl Gate {
    pub fn allow(&self, address: IpAddr, endpoint: &'static str) -> Result<(), Failure> {
        self.allow_at(address, endpoint, Instant::now())
    }
    fn allow_at(
        &self,
        address: IpAddr,
        endpoint: &'static str,
        now: Instant,
    ) -> Result<(), Failure> {
        let mut entries = self.entries.lock().map_err(|_| domain::invalid_state())?;
        let key = (address, endpoint);
        if !entries.contains_key(&key) && entries.len() >= 1000 {
            entries.retain(|_, v| now.duration_since(v.at) <= Duration::from_secs(60));
            if entries.len() >= 1000 {
                return Err(app::busy());
            }
        }
        let bucket = entries.entry(key).or_insert(Bucket {
            tokens: 12.,
            at: now,
        });
        bucket.tokens = (bucket.tokens + now.duration_since(bucket.at).as_secs_f64()).min(12.);
        bucket.at = now;
        if bucket.tokens < 1. {
            return Err(app::busy());
        }
        bucket.tokens -= 1.;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[tokio::test]
    async fn canceled_caller_keeps_kdf_permit_until_owned_close_finishes() {
        let mut adapter = PasswordAdapter::new().await.unwrap();
        let (ready, entered) = std::sync::mpsc::sync_channel(1);
        let (release, released) = std::sync::mpsc::channel();
        adapter.hook = Some(Arc::new(ComputeHook {
            ready,
            release: Mutex::new(released),
        }));
        let adapter = Arc::new(adapter);
        let worker = tokio::spawn({
            let adapter = adapter.clone();
            async move {
                adapter
                    .hash(
                        &StartupScope,
                        Password::new(Secret::new("fixture-password-123".into())).unwrap(),
                    )
                    .await
            }
        });
        tokio::task::spawn_blocking(move || entered.recv_timeout(Duration::from_secs(3)).unwrap())
            .await
            .unwrap();
        worker.abort();
        assert!(worker.await.unwrap_err().is_cancelled());
        assert_eq!(adapter.work.available_permits(), 1);
        let close = tokio::spawn({
            let adapter = adapter.clone();
            async move { adapter.close().await }
        });
        while !adapter.closed.load(Ordering::Acquire) {
            tokio::task::yield_now().await;
        }
        assert!(!close.is_finished());
        assert!(
            adapter
                .hash(
                    &StartupScope,
                    Password::new(Secret::new("fixture-password-123".into())).unwrap()
                )
                .await
                .is_err()
        );
        release.send(()).unwrap();
        tokio::time::timeout(Duration::from_secs(5), close)
            .await
            .unwrap()
            .unwrap()
            .unwrap();
        assert_eq!(adapter.work.available_permits(), 2);
    }
    #[test]
    fn edge_gate_is_bounded_per_source_and_endpoint() {
        let gate = Gate::default();
        let now = Instant::now();
        let ip = "127.0.0.1".parse().unwrap();
        for _ in 0..12 {
            gate.allow_at(ip, "register", now).unwrap();
        }
        assert!(gate.allow_at(ip, "register", now).is_err());
        gate.allow_at(ip, "sign-in", now).unwrap();
        gate.allow_at(ip, "register", now + Duration::from_secs(1))
            .unwrap();
        for i in 1..999 {
            gate.allow_at(std::net::Ipv4Addr::from(i).into(), "other", now)
                .unwrap();
        }
        assert!(gate.allow_at("::1".parse().unwrap(), "new", now).is_err());
        gate.allow_at("::1".parse().unwrap(), "new", now + Duration::from_secs(61))
            .unwrap();
    }
}
