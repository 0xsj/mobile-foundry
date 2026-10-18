//! Upgrades, socket I/O and task lifetime stay outside the application ports.
use crate::app::{Scope, Subscription, query::Subscribe};
use axum::{
    Extension, Router,
    extract::{Request, State},
    http::{Method, StatusCode},
    response::Response,
    routing::any,
};
use futures_util::{FutureExt, SinkExt, StreamExt};
use httpx::{RequestContext, admission::AdmissionError, problem};
use hyper_util::rt::TokioIo;
use kernel::{Failure, Kind, context::Metadata, id::Id};
use std::{
    panic::AssertUnwindSafe,
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
    time::Duration,
};
use tokio::{
    sync::{Mutex, watch},
    task::JoinSet,
    time::{Instant, MissedTickBehavior},
};
use tokio_tungstenite::{
    WebSocketStream,
    tungstenite::{
        Message,
        handshake::server::create_response_with_body,
        protocol::{Role, WebSocketConfig},
    },
};
type Socket = WebSocketStream<TokioIo<hyper::upgrade::Upgraded>>;
#[derive(Clone, Copy)]
struct Timing {
    recheck: Duration,
    lifetime: Duration,
}
impl Default for Timing {
    fn default() -> Self {
        Self {
            recheck: Duration::from_secs(5),
            lifetime: Duration::from_secs(600),
        }
    }
}
struct Connections {
    stopping: AtomicBool,
    failed: AtomicBool,
    stop: watch::Sender<bool>,
    tasks: Mutex<JoinSet<()>>,
}
#[derive(Clone)]
pub struct Health(Arc<Connections>);
impl Health {
    pub fn check(&self) -> Result<(), Failure> {
        if self.0.stopping.load(Ordering::Acquire) || self.0.failed.load(Ordering::Acquire) {
            Err(Failure::new(
                Kind::Unavailable,
                "realtime.stopped",
                "realtime socket owner stopped",
            ))
        } else {
            Ok(())
        }
    }
}
struct Core {
    subscribe: Subscribe,
    principals: Arc<dyn principal::Resolver>,
    connections: Arc<Connections>,
    timing: Timing,
}
#[derive(Clone)]
pub struct Handlers {
    core: Arc<Core>,
}
impl Handlers {
    pub fn new(subscribe: Subscribe, principals: Arc<dyn principal::Resolver>) -> Self {
        let (stop, _) = watch::channel(false);
        Self {
            core: Arc::new(Core {
                subscribe,
                principals,
                connections: Arc::new(Connections {
                    stopping: AtomicBool::new(false),
                    failed: AtomicBool::new(false),
                    stop,
                    tasks: Mutex::new(JoinSet::new()),
                }),
                timing: Timing::default(),
            }),
        }
    }
    pub fn routes(&self) -> Router {
        Router::new()
            .route("/v1/accounts/{account}/realtime", any(dispatch))
            .with_state(self.clone())
    }
    pub fn health(&self) -> Health {
        Health(self.core.connections.clone())
    }
    pub fn stop(&self) {
        self.core
            .connections
            .stopping
            .store(true, Ordering::Release);
        let _ = self.core.connections.stop.send(true);
    }
    pub async fn close(&self) -> Result<(), Failure> {
        self.stop();
        let mut tasks = self.core.connections.tasks.lock().await;
        while let Some(done) = tasks.join_next().await {
            if done.is_err() {
                self.core.connections.failed.store(true, Ordering::Release);
            }
        }
        if self.core.connections.failed.load(Ordering::Acquire) {
            Err(Failure::new(
                Kind::Internal,
                "realtime.task_failed",
                "socket task failed",
            ))
        } else {
            Ok(())
        }
    }
}
fn invalid() -> Failure {
    Failure::new(
        Kind::Invalid,
        "http.invalid_json",
        "invalid realtime admission",
    )
    .with_public("Send one valid JSON object with the documented fields.")
}
async fn dispatch(
    State(h): State<Handlers>,
    Extension(c): Extension<RequestContext>,
    r: Request,
) -> Response {
    if r.method() != Method::GET && r.method() != Method::HEAD {
        return problem::method_not_allowed(&c.request_id, "GET, HEAD");
    }
    match httpx::deadline(&c, async { Ok(handle(&h, &c, r).await) }).await {
        Ok(Ok(r)) => r,
        Ok(Err(e)) => e.response(&c),
        Err(e) => problem::response(&e, &c.request_id),
    }
}
async fn handle(
    h: &Handlers,
    c: &RequestContext,
    mut r: Request,
) -> Result<Response, AdmissionError> {
    if r.method() != Method::GET
        || r.uri().query().is_some_and(|q| !q.is_empty())
        || r.headers().contains_key("origin")
        || r.headers().contains_key("cookie")
    {
        return Err(invalid().into());
    }
    let raw = r.uri().path().split('/').nth(3).unwrap_or("");
    let account =
        Id::parse(&httpx::query::component(raw, false).map_err(|_| kernel::id::invalid())?)?;
    let token = zeroize::Zeroizing::new(httpx::auth::bearer(r.headers())?.to_owned());
    let actor = h.core.principals.resolve(c, &token).await?;
    // Register before ready; refetch after ready closes the subscription/snapshot gap.
    let feed = h.core.subscribe.handle(c, &actor.id, &account).await?;
    if h.core.connections.stopping.load(Ordering::Acquire) {
        return Err(principal::required().into());
    }
    use base64::{Engine, engine::general_purpose::STANDARD};
    let mut keys = r.headers().get_all("sec-websocket-key").iter();
    let key = keys
        .next()
        .and_then(|k| k.to_str().ok())
        .ok_or_else(invalid)?;
    if keys.next().is_some()
        || STANDARD.decode(key).ok().is_none_or(|b| b.len() != 16)
        || r.extensions().get::<hyper::upgrade::OnUpgrade>().is_none()
    {
        return Err(invalid().into());
    }
    let response = create_response_with_body(&r, axum::body::Body::empty).map_err(|_| invalid())?;
    if response.status() != StatusCode::SWITCHING_PROTOCOLS {
        return Err(invalid().into());
    }
    let upgrade = hyper::upgrade::on(&mut r);
    let core = h.core.clone();
    let mut stopped = core.connections.stop.subscribe();
    let mut tasks = h.core.connections.tasks.lock().await;
    while let Some(done) = tasks.try_join_next() {
        if done.is_err() {
            h.core.connections.failed.store(true, Ordering::Release);
        }
    }
    h.health().check()?;
    c.check()?;
    let metadata = kernel::context::Scope::metadata(c);
    let connections = core.connections.clone();
    tasks.spawn(async move {
        let owned = async {
        if *stopped.borrow() { return; }
        tokio::select! {
            biased;
            _ = stopped.changed() => {},
            _ = async {
                let Ok(Ok(io)) = tokio::time::timeout(Duration::from_secs(3), upgrade).await else { return; };
                let config = WebSocketConfig::default()
                    .read_buffer_size(1024).write_buffer_size(0).max_write_buffer_size(4096)
                    .max_message_size(Some(1024)).max_frame_size(Some(1024));
                let socket = WebSocketStream::from_raw_socket(TokioIo::new(io), Role::Server, Some(config)).await;
                let _ = tokio::time::timeout(core.timing.lifetime,
                    session(&core, socket, feed, &token, actor, &account, metadata)).await;
            } => {}
        }
        // Dropping the upgraded stream closes it; the owner retains and joins this task.
        };
        if AssertUnwindSafe(owned).catch_unwind().await.is_err() {
            connections.failed.store(true, Ordering::Release);
        }
    });
    Ok(response)
}
struct Check {
    deadline: Instant,
    metadata: Metadata,
}
impl Scope for Check {
    fn check(&self) -> Result<(), Failure> {
        if Instant::now() >= self.deadline {
            Err(Failure::new(
                Kind::Timeout,
                "",
                "realtime authorization budget elapsed",
            ))
        } else {
            Ok(())
        }
    }
    fn metadata(&self) -> Metadata {
        self.metadata.clone()
    }
}
async fn check(
    core: &Core,
    token: &str,
    original: &principal::Principal,
    account: &Id,
    metadata: &Metadata,
) -> Result<(), Failure> {
    let s = Check {
        deadline: Instant::now() + Duration::from_secs(2),
        metadata: metadata.clone(),
    };
    tokio::time::timeout_at(s.deadline, async {
        let current = core.principals.resolve(&s, token).await?;
        if current.id != original.id || current.session_id != original.session_id {
            return Err(principal::required());
        }
        core.subscribe.check(&s, &original.id, account).await
    })
    .await
    .map_err(|_| Failure::new(Kind::Timeout, "", "realtime authorization budget elapsed"))?
}
async fn send(socket: &mut Socket, message: Message) -> bool {
    matches!(
        tokio::time::timeout(Duration::from_secs(3), socket.send(message)).await,
        Ok(Ok(()))
    )
}
async fn hint(
    core: &Core,
    socket: &mut Socket,
    kind: &str,
    token: &str,
    actor: &principal::Principal,
    account: &Id,
    metadata: &Metadata,
) -> bool {
    if check(core, token, actor, account, metadata).await.is_err() {
        return false;
    }
    let payload = serde_json::json!({
        "type":kind, "resource":"notes", "account_id":account.as_str(), "refetch":true
    })
    .to_string();
    send(socket, Message::Text(payload.into())).await
}
async fn session(
    core: &Core,
    mut socket: Socket,
    mut feed: Box<dyn Subscription>,
    token: &str,
    actor: principal::Principal,
    account: &Id,
    metadata: Metadata,
) {
    if !hint(
        core,
        &mut socket,
        "ready",
        token,
        &actor,
        account,
        &metadata,
    )
    .await
    {
        return;
    }
    let mut ticks =
        tokio::time::interval_at(Instant::now() + core.timing.recheck, core.timing.recheck);
    ticks.set_missed_tick_behavior(MissedTickBehavior::Skip);
    let mut pong: Option<(Instant, Vec<u8>)> = None;
    let mut sequence = 0u64;
    loop {
        tokio::select! {
            biased;
            _ = async {
                if let Some((at, _)) = &pong { tokio::time::sleep_until(*at).await; }
                else { std::future::pending().await }
            } => return,
            _ = ticks.tick() => {
                if check(core, token, &actor, account, &metadata).await.is_err() { return; }
                if pong.is_none() {
                    sequence += 1;
                    let value = sequence.to_be_bytes().to_vec();
                    if !send(&mut socket, Message::Ping(value.clone().into())).await { return; }
                    pong = Some((Instant::now() + Duration::from_secs(3), value));
                }
            },
            message = socket.next() => match message {
                Some(Ok(Message::Pong(value))) => {
                    if pong.as_ref().is_some_and(|(_, expected)| expected.as_slice() == value.as_ref()) { pong = None; }
                },
                Some(Ok(Message::Ping(_))) => {
                    if !matches!(tokio::time::timeout(Duration::from_secs(3), socket.flush()).await, Ok(Ok(()))) { return; }
                },
                // Application messages, close, malformed/oversized input and disconnect end ownership.
                _ => return,
            },
            signal = feed.next() => {
                if !matches!(signal, Ok(true))
                    || !hint(core, &mut socket, "changed", token, &actor, account, &metadata).await { return; }
            },
        }
    }
}

#[cfg(test)]
mod tests;
