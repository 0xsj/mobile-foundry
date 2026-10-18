use super::*;
use crate::app::{AccountAccess, PortFuture};
use kernel::id::{Generator, RandomIds};
use runtime_config::{LogFormat, LogLevel};
use std::sync::atomic::AtomicUsize;
use tokio::{
    net::{TcpListener, TcpStream},
    sync::oneshot,
    task::JoinHandle,
};
use tokio_tungstenite::tungstenite::client::IntoClientRequest;

fn id() -> Id {
    RandomIds.new_id().unwrap()
}
struct Authority {
    actor: Id,
    session: Id,
    replacement: Id,
    account: Id,
    credential: AtomicUsize,
    access: AtomicUsize,
}
impl Authority {
    fn new() -> Self {
        Self {
            actor: id(),
            session: id(),
            replacement: id(),
            account: id(),
            credential: AtomicUsize::new(0),
            access: AtomicUsize::new(0),
        }
    }
    fn principal(&self) -> principal::Principal {
        principal::Principal {
            id: if self.credential.load(Ordering::Acquire) == 2 {
                self.replacement.clone()
            } else {
                self.actor.clone()
            },
            session_id: if self.credential.load(Ordering::Acquire) == 3 {
                self.replacement.clone()
            } else {
                self.session.clone()
            },
            session_expires_at: std::time::SystemTime::now() + Duration::from_secs(3600),
        }
    }
}
impl principal::Resolver for Authority {
    fn resolve<'a>(
        &'a self,
        s: &'a dyn Scope,
        token: &'a str,
    ) -> PortFuture<'a, principal::Principal> {
        Box::pin(async move {
            s.check()?;
            assert_eq!(token, "PRIVATE-bearer");
            match self.credential.load(Ordering::Acquire) {
                1 => Err(principal::required()),
                4 => std::future::pending().await,
                5 => panic!("PRIVATE-panic"),
                _ => Ok(self.principal()),
            }
        })
    }
}
impl AccountAccess for Authority {
    fn allowed<'a>(
        &'a self,
        s: &'a dyn Scope,
        actor: &'a Id,
        account: &'a Id,
    ) -> PortFuture<'a, bool> {
        Box::pin(async move {
            s.check()?;
            assert_eq!(actor, &self.actor);
            assert_eq!(account, &self.account);
            match self.access.load(Ordering::Acquire) {
                0 => Ok(true),
                1 => Ok(false),
                _ => Err(Failure::new(
                    Kind::Unavailable,
                    "fixture.offline",
                    "PRIVATE-cause",
                )),
            }
        })
    }
}
type Client = WebSocketStream<TcpStream>;
struct Fixture {
    handlers: Handlers,
    authority: Arc<Authority>,
    hub: Arc<broadcast::Hub>,
    address: std::net::SocketAddr,
    stop: oneshot::Sender<()>,
    server: JoinHandle<Result<(), Failure>>,
}
impl Fixture {
    async fn start(recheck: Duration, lifetime: Duration) -> Self {
        let authority = Arc::new(Authority::new());
        let hub = Arc::new(broadcast::Hub::new(4, 1));
        let mut handlers = Handlers::new(
            Subscribe {
                access: authority.clone(),
                feed: Arc::new(crate::infra::local::Local(hub.clone())),
            },
            authority.clone(),
        );
        Arc::get_mut(&mut handlers.core).unwrap().timing = Timing { recheck, lifetime };
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = listener.local_addr().unwrap();
        let (stop, stopped) = oneshot::channel();
        let server = runtime_server::Server::new(
            httpx::logger::Logger::new(std::io::sink(), LogFormat::Json, LogLevel::Info),
            Duration::from_secs(2),
        )
        .unwrap()
        .with_routes(handlers.routes());
        let server = tokio::spawn(server.serve(listener, async {
            let _ = stopped.await;
        }));
        Self {
            handlers,
            authority,
            hub,
            address,
            stop,
            server,
        }
    }
    async fn fast() -> Self {
        Self::start(Duration::from_millis(100), Duration::from_secs(30)).await
    }
    async fn connect(&self) -> Client {
        let mut request = format!(
            "ws://{}/v1/accounts/{}/realtime",
            self.address,
            self.authority.account.as_str()
        )
        .into_client_request()
        .unwrap();
        request
            .headers_mut()
            .insert("authorization", "Bearer PRIVATE-bearer".parse().unwrap());
        let io = TcpStream::connect(self.address).await.unwrap();
        let (client, response) = tokio_tungstenite::client_async(request, io).await.unwrap();
        assert_eq!(response.status(), 101);
        assert_eq!(response.headers()["cache-control"], "no-store");
        assert!(response.headers().contains_key("x-request-id"));
        client
    }
    async fn closed(self) {
        self.handlers.close().await.unwrap();
        self.hub.close();
        self.stop.send(()).unwrap();
        tokio::time::timeout(Duration::from_secs(3), self.server)
            .await
            .unwrap()
            .unwrap()
            .unwrap();
    }
    fn publish(&self) {
        self.hub
            .publish(&format!("notes.{}", self.authority.account.as_str()));
    }
    async fn assert_released(&self) {
        tokio::time::timeout(Duration::from_secs(2), async {
            loop {
                if let Ok(watch) = self.hub.watch("other", self.authority.actor.as_str()) {
                    drop(watch);
                    return;
                }
                tokio::task::yield_now().await;
            }
        })
        .await
        .unwrap();
    }
}
async fn hint(client: &mut Client, account: &Id, kind: &str) {
    let text = tokio::time::timeout(Duration::from_secs(2), async {
        loop {
            match client.next().await.unwrap().unwrap() {
                Message::Text(text) => return text,
                Message::Ping(_) => client.flush().await.unwrap(),
                message => panic!("unexpected frame: {message:?}"),
            }
        }
    })
    .await
    .unwrap();
    assert_eq!(
        serde_json::from_str::<serde_json::Value>(&text).unwrap(),
        serde_json::json!({"type":kind,"resource":"notes","account_id":account.as_str(),"refetch":true})
    );
}
async fn closed(client: &mut Client) {
    tokio::time::timeout(Duration::from_secs(4), async {
        loop {
            match client.next().await {
                None | Some(Err(_)) | Some(Ok(Message::Close(_))) => return,
                Some(Ok(Message::Ping(_))) => {
                    let _ = client.flush().await;
                }
                other => panic!("unexpected data after refusal: {other:?}"),
            }
        }
    })
    .await
    .unwrap();
}

#[tokio::test]
async fn scoped_hints_control_frames_and_disconnect_release() {
    let f = Fixture::fast().await;
    let mut client = f.connect().await;
    hint(&mut client, &f.authority.account, "ready").await;
    client
        .send(Message::Ping(vec![1, 2, 3].into()))
        .await
        .unwrap();
    let reply = client.next().await.unwrap().unwrap();
    assert_eq!(reply, Message::Pong(vec![1, 2, 3].into()));
    f.hub.publish("notes.foreign");
    f.publish();
    hint(&mut client, &f.authority.account, "changed").await;
    client.close(None).await.unwrap();
    drop(client);
    f.assert_released().await;
    let mut client = f.connect().await;
    hint(&mut client, &f.authority.account, "ready").await;
    f.handlers.close().await.unwrap();
    closed(&mut client).await;
    assert!(f.handlers.health().check().is_err());
    f.assert_released().await;
    f.closed().await;
}

#[tokio::test]
async fn periodic_checks_close_revoked_credentials_changed_owners_sessions_and_account_refusals() {
    for mode in 0..5 {
        let f = Fixture::fast().await;
        let mut client = f.connect().await;
        hint(&mut client, &f.authority.account, "ready").await;
        if mode < 3 {
            f.authority.credential.store(mode + 1, Ordering::Release);
        } else {
            f.authority.access.store(mode - 2, Ordering::Release);
        }
        closed(&mut client).await;
        f.assert_released().await;
        f.closed().await;
    }
}

#[tokio::test]
async fn changed_hint_reauthorizes_before_emitting_even_before_next_tick() {
    let f = Fixture::start(Duration::from_secs(60), Duration::from_secs(30)).await;
    let mut client = f.connect().await;
    hint(&mut client, &f.authority.account, "ready").await;
    f.authority.access.store(1, Ordering::Release);
    f.publish();
    closed(&mut client).await;
    f.assert_released().await;
    f.closed().await;
}

#[tokio::test]
async fn app_data_and_oversized_frames_end_subscription() {
    for message in [
        Message::Text("PRIVATE-input".into()),
        Message::Binary(vec![0; 1025].into()),
    ] {
        let f = Fixture::fast().await;
        let mut client = f.connect().await;
        hint(&mut client, &f.authority.account, "ready").await;
        client.send(message).await.unwrap();
        closed(&mut client).await;
        f.assert_released().await;
        f.closed().await;
    }
}

#[tokio::test]
async fn missing_pong_expires_even_when_recheck_ticks_continue() {
    let f = Fixture::fast().await;
    let mut client = f.connect().await;
    hint(&mut client, &f.authority.account, "ready").await;
    // Not polling means tungstenite cannot auto-queue/flush a Pong.
    tokio::time::sleep(Duration::from_millis(3300)).await;
    // Discard the already-buffered Ping without flushing an automatic reply.
    assert!(matches!(client.next().await, Some(Ok(Message::Ping(_)))));
    closed(&mut client).await;
    f.assert_released().await;
    f.closed().await;
}

#[tokio::test]
async fn bounded_socket_lifetime_releases_capacity() {
    let f = Fixture::start(Duration::from_secs(5), Duration::from_millis(150)).await;
    let mut client = f.connect().await;
    hint(&mut client, &f.authority.account, "ready").await;
    closed(&mut client).await;
    f.assert_released().await;
    f.closed().await;
}

#[tokio::test]
async fn authorization_timeout_and_shutdown_cancel_only_read_only_socket_work() {
    let f = Fixture::fast().await;
    let mut client = f.connect().await;
    hint(&mut client, &f.authority.account, "ready").await;
    f.authority.credential.store(4, Ordering::Release);
    f.publish();
    tokio::time::timeout(Duration::from_secs(1), f.handlers.close())
        .await
        .unwrap()
        .unwrap();
    closed(&mut client).await;
    f.assert_released().await;
    f.closed().await;
}

#[tokio::test(start_paused = true)]
async fn reauthorization_has_one_two_second_budget() {
    let authority = Arc::new(Authority::new());
    let handlers = Handlers::new(
        Subscribe {
            access: authority.clone(),
            feed: Arc::new(crate::infra::local::Local(Arc::new(broadcast::Hub::new(
                1, 1,
            )))),
        },
        authority.clone(),
    );
    let original = authority.principal();
    authority.credential.store(4, Ordering::Release);
    let start = Instant::now();
    assert_eq!(
        check(
            &handlers.core,
            "PRIVATE-bearer",
            &original,
            &authority.account,
            &Metadata::default()
        )
        .await
        .unwrap_err()
        .kind(),
        Kind::Timeout
    );
    assert_eq!(Instant::now() - start, Duration::from_secs(2));
    handlers.close().await.unwrap();
}

#[tokio::test]
async fn canceled_close_retains_handles_until_later_join() {
    let f = Fixture::fast().await;
    let (release, released) = oneshot::channel();
    f.handlers.core.connections.tasks.lock().await.spawn(async {
        let _ = released.await;
    });
    assert!(
        tokio::time::timeout(Duration::from_millis(20), f.handlers.close())
            .await
            .is_err()
    );
    assert_eq!(f.handlers.core.connections.tasks.lock().await.len(), 1);
    release.send(()).unwrap();
    f.closed().await;
}

#[tokio::test]
async fn socket_panic_closes_feed_and_fails_owner_readiness() {
    let f = Fixture::fast().await;
    let mut client = f.connect().await;
    hint(&mut client, &f.authority.account, "ready").await;
    f.authority.credential.store(5, Ordering::Release);
    f.publish();
    closed(&mut client).await;
    f.assert_released().await;
    assert_eq!(
        f.handlers.health().check().unwrap_err().kind(),
        Kind::Unavailable
    );
    let failure = f.handlers.close().await.unwrap_err();
    assert_eq!(failure.kind(), Kind::Internal);
    assert!(!format!("{failure:?}").contains("PRIVATE"));
    f.hub.close();
    f.stop.send(()).unwrap();
    f.server.await.unwrap().unwrap();
}

#[tokio::test]
async fn socket_outlives_http_fifteen_second_request_budget() {
    let f = Fixture::start(Duration::from_secs(5), Duration::from_secs(30)).await;
    let mut client = f.connect().await;
    hint(&mut client, &f.authority.account, "ready").await;
    let until = Instant::now() + Duration::from_secs(16);
    loop {
        tokio::select! {
            _=tokio::time::sleep_until(until)=>break,
            message=client.next()=>{
                assert!(matches!(message,Some(Ok(Message::Ping(_)))));
                client.flush().await.unwrap();
            }
        }
    }
    f.publish();
    hint(&mut client, &f.authority.account, "changed").await;
    f.closed().await;
}
