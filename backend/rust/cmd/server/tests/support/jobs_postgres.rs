use super::conformance::{AT, id, job, owner, result};
use events::dispatcher::Control;
use kernel::{
    clock::Clock,
    context::{PortFuture, Scope},
    id::Id,
};
use notifications::{
    app::{
        command::{run_push_job::RunPushJob, send_push_preview::SendPushPreview},
        installations::Installations,
        push::{PushAddress, PushDisposition, PushGateway, SessionEligibility},
        push_jobs::PushJobs,
    },
    domain::push_job::{Code, LEASE, PushJob, Status},
    infra::postgres::PostgresStore,
};
use sqlx::PgPool;
use std::{
    path::PathBuf,
    sync::{
        Arc,
        atomic::{AtomicUsize, Ordering},
    },
    time::{Duration, UNIX_EPOCH},
};
async fn sql(pool: &PgPool, text: &str) {
    sqlx::raw_sql(text).execute(pool).await.unwrap();
}
pub async fn faults(pool: &PgPool) {
    let store = PostgresStore::new(pool.clone());
    let s = Control::default();
    let j = job();
    let key = &j.snapshot().intent.id;
    store.queue(&s, j.clone()).await.unwrap();
    let token = id();
    store.claim(&s, token.clone(), AT).await.unwrap();
    sql(pool,&format!("ALTER TABLE notifications.push_jobs ADD CONSTRAINT reject_job_marker CHECK(id<>'{key}'::uuid OR status<>'sending') NOT VALID")).await;
    assert!(store.begin(&s, key, &token, AT).await.is_err());
    assert_eq!(
        store
            .find(&s, key)
            .await
            .unwrap()
            .unwrap()
            .snapshot()
            .status,
        Status::Leased
    );
    sql(
        pool,
        "ALTER TABLE notifications.push_jobs DROP CONSTRAINT reject_job_marker",
    )
    .await;
    store.begin(&s, key, &token, AT).await.unwrap();
    sql(pool,&format!("ALTER TABLE notifications.push_jobs ADD CONSTRAINT reject_job_result CHECK(id<>'{key}'::uuid OR status<>'accepted') NOT VALID")).await;
    assert!(
        store
            .finish(
                &s,
                key,
                &token,
                result(true, Status::Accepted, Code::None),
                AT
            )
            .await
            .is_err()
    );
    assert_eq!(
        store
            .find(&s, key)
            .await
            .unwrap()
            .unwrap()
            .snapshot()
            .status,
        Status::Sending
    );
    sql(
        pool,
        "ALTER TABLE notifications.push_jobs DROP CONSTRAINT reject_job_result",
    )
    .await;
    store.claim(&s, id(), AT + LEASE).await.unwrap();
    assert_eq!(
        store.find(&s, key).await.unwrap().unwrap().snapshot().code,
        Code::WorkerLost
    );
    let mut child = j.snapshot().intent.clone();
    child.id = id();
    child.retry_of = Some(key.clone());
    child.duplicate_risk_accepted = true;
    let child = PushJob::new(child, AT + LEASE).unwrap();
    sql(pool,&format!("ALTER TABLE notifications.push_jobs ADD CONSTRAINT reject_job_child CHECK(id<>'{}'::uuid) NOT VALID",child.snapshot().intent.id)).await;
    assert!(store.replace(&s, child.clone()).await.is_err());
    assert!(store.replacement(&s, &owner(&j)).await.unwrap().is_none());
    sql(
        pool,
        "ALTER TABLE notifications.push_jobs DROP CONSTRAINT reject_job_child",
    )
    .await;
    store.replace(&s, child.clone()).await.unwrap();
    store.cancel(&s, &owner(&child), AT + LEASE).await.unwrap();
    // Retained capacity includes terminal roots/children; existing replays still work.
    let capacity = job();
    let i = &capacity.snapshot().intent;
    let at = postgres_storage::time(AT).unwrap();
    let count: i64 = sqlx::query_scalar("SELECT count(*) FROM notifications.push_jobs")
        .fetch_one(pool)
        .await
        .unwrap();
    sqlx::query("INSERT INTO notifications.push_jobs(id,installation_id,principal_id,session_id,installation_version,status,attempts,created_at,updated_at,expires_at,next_at,code,retired) SELECT gen_random_uuid(),$1::uuid,$2::uuid,$3::uuid,1,'canceled',0,$4,$4,$4+interval '15 minutes',$4,'user_canceled',false FROM generate_series(1,$5::integer)").bind(i.installation_id.as_str()).bind(i.principal_id.as_str()).bind(i.session_id.as_str()).bind(at).bind((10000-count) as i32).execute(pool).await.unwrap();
    assert_eq!(
        store
            .queue(&s, job())
            .await
            .unwrap_err()
            .public()
            .code
            .as_deref(),
        Some("notifications.capacity")
    );
    assert_eq!(
        store.queue(&s, j.clone()).await.unwrap().snapshot().status,
        Status::Uncertain
    );
    sqlx::query("DELETE FROM notifications.push_jobs WHERE principal_id=$1::uuid")
        .bind(i.principal_id.as_str())
        .execute(pool)
        .await
        .unwrap();
    println!(
        "Jobs PostgreSQL: marker/result/child rollback, retained-capacity/replay and lineage passed"
    );
}
struct Fixed(u64);
impl Clock for Fixed {
    fn now(&self) -> std::time::SystemTime {
        UNIX_EPOCH + Duration::from_micros(self.0)
    }
}
struct Eligible;
impl SessionEligibility for Eligible {
    fn eligible<'a>(&'a self, _: &'a dyn Scope, _: &'a Id, _: &'a Id) -> PortFuture<'a, bool> {
        Box::pin(async { Ok(true) })
    }
}
struct Effect {
    path: PathBuf,
    calls: AtomicUsize,
    hold: bool,
}
impl PushGateway for Effect {
    fn send_preview<'a>(
        &'a self,
        _: &'a dyn Scope,
        _: PushAddress,
        preview: Id,
    ) -> PortFuture<'a, PushDisposition> {
        Box::pin(async move {
            use std::io::Write;
            self.calls.fetch_add(1, Ordering::SeqCst);
            let mut file = std::fs::OpenOptions::new()
                .create(true)
                .append(true)
                .open(&self.path)
                .unwrap();
            writeln!(file, "{preview}").unwrap();
            file.sync_all().unwrap();
            if self.hold {
                std::future::pending().await
            } else {
                Ok(PushDisposition::Accepted)
            }
        })
    }
}
fn run(store: Arc<PostgresStore>, gateway: Arc<dyn PushGateway>, now: u64) -> RunPushJob {
    RunPushJob {
        store: store.clone(),
        send: SendPushPreview {
            targets: store,
            sessions: Arc::new(Eligible),
            gateway,
            clock: Arc::new(Fixed(now)),
        },
        ids: Arc::new(kernel::id::RandomIds),
    }
}
struct Owned {
    path: PathBuf,
    child: Option<std::process::Child>,
}
impl Drop for Owned {
    fn drop(&mut self) {
        if let Some(c) = &mut self.child {
            let _ = c.kill();
            let _ = c.wait();
        }
        let _ = std::fs::remove_dir_all(&self.path);
    }
}
pub async fn crash(pool: &PgPool, url: &str) {
    let s = Control::default();
    let store = Arc::new(PostgresStore::new(pool.clone()));
    let j = job();
    let key = &j.snapshot().intent.id;
    let i = &j.snapshot().intent;
    let spec = notifications::domain::installation::InstallationSpec {
        route: notifications::domain::installation::PushRoute {
            provider: "fcm".into(),
            application: "dev.example.jobs".into(),
            environment: "production".into(),
        },
        token: notifications::domain::installation::Secret::new(format!("PRIVATE-{}", id())),
    };
    store
        .register(
            &s,
            notifications::app::installations::Mutation {
                actor: notifications::app::installations::Actor {
                    principal: i.principal_id.clone(),
                    session: i.session_id.clone(),
                    expires_at: AT + 3_600_000_000,
                },
                id: i.installation_id.clone(),
                registration_id: id(),
                proof_digest: notifications::domain::installation::Secret::new("a".repeat(64)),
                spec,
                expected: 0,
            },
            AT,
        )
        .await
        .unwrap();
    store.queue(&s, j.clone()).await.unwrap();
    let path = std::env::temp_dir().join(format!("mobile-job-crash-{}", id()));
    std::fs::create_dir(&path).unwrap();
    let effect = path.join("effect");
    let mut owned = Owned { path, child: None };
    let mut command = std::process::Command::new(std::env::current_exe().unwrap());
    command
        .args(["--ignored", "--exact", "crash_child", "--nocapture"])
        .env_clear()
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null());
    for key in ["PATH", "HOME", "TMPDIR"] {
        if let Some(value) = std::env::var_os(key) {
            command.env(key, value);
        }
    }
    owned.child = Some(
        command
            .env("TEST_POSTGRES_DISPOSABLE", "1")
            .env("TEST_POSTGRES_URL", url)
            .env("TEST_JOB_CHILD", "1")
            .env("TEST_JOB_EFFECT", &effect)
            .spawn()
            .unwrap(),
    );
    tokio::time::timeout(Duration::from_secs(10), async {
        while std::fs::read_to_string(&effect)
            .ok()
            .is_none_or(|text| text.trim() != key.as_str())
        {
            assert!(
                owned.child.as_mut().unwrap().try_wait().unwrap().is_none(),
                "crash child exited before marker/effect"
            );
            tokio::time::sleep(Duration::from_millis(20)).await;
        }
    })
    .await
    .unwrap();
    let mut child = owned.child.take().unwrap();
    child.kill().unwrap();
    assert!(!child.wait().unwrap().success());
    assert_eq!(
        store
            .find(&s, key)
            .await
            .unwrap()
            .unwrap()
            .snapshot()
            .status,
        Status::Sending
    );
    let fresh = postgres_storage::open(url, 4).await.unwrap();
    let recovered = Arc::new(PostgresStore::new(fresh.clone()));
    let gateway = Arc::new(Effect {
        path: effect.clone(),
        calls: AtomicUsize::new(0),
        hold: false,
    });
    run(recovered.clone(), gateway.clone(), AT + LEASE)
        .handle(&s)
        .await
        .unwrap();
    let state = recovered.find(&s, key).await.unwrap().unwrap();
    assert_eq!(state.snapshot().status, Status::Uncertain);
    assert_eq!(state.snapshot().code, Code::WorkerLost);
    assert_eq!(gateway.calls.load(Ordering::SeqCst), 0);
    assert_eq!(std::fs::read_to_string(&effect).unwrap().lines().count(), 1);
    fresh.close().await;
    println!(
        "Jobs process loss: SIGKILL after committed marker/simulated external effect, fresh connection recovered worker_lost with no resend"
    );
}
pub async fn child() {
    assert_eq!(std::env::var("TEST_JOB_CHILD").as_deref(), Ok("1"));
    assert_eq!(
        std::env::var("TEST_POSTGRES_DISPOSABLE").as_deref(),
        Ok("1")
    );
    let url = std::env::var("TEST_POSTGRES_URL").unwrap();
    let effect = std::env::var("TEST_JOB_EFFECT").unwrap();
    let pool = postgres_storage::open(&url, 4).await.unwrap();
    let store = Arc::new(PostgresStore::new(pool));
    run(
        store,
        Arc::new(Effect {
            path: effect.into(),
            calls: AtomicUsize::new(0),
            hold: true,
        }),
        AT,
    )
    .handle(&Control::default())
    .await
    .unwrap();
    panic!("crash child returned without being killed")
}
