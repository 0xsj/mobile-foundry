use super::*;
use axum::{Router, routing::get};
use runtime_config::{LogFormat, LogLevel};
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::TcpStream,
    sync::{Notify, oneshot},
};

fn server(timeout: Duration) -> Server {
    Server::new(
        Logger::new(std::io::sink(), LogFormat::Json, LogLevel::Info),
        timeout,
    )
    .unwrap()
}
async fn request(address: std::net::SocketAddr, path: &str) -> String {
    let mut stream = TcpStream::connect(address).await.unwrap();
    stream
        .write_all(
            format!("GET {path} HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                .as_bytes(),
        )
        .await
        .unwrap();
    let mut bytes = Vec::new();
    stream.read_to_end(&mut bytes).await.unwrap();
    String::from_utf8(bytes).unwrap()
}

#[tokio::test]
async fn actual_listener_health_and_clean_shutdown() {
    let server = server(Duration::from_secs(1));
    let serving = server.serving();
    assert!(!serving.is_ready());
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let address = listener.local_addr().unwrap();
    let (stop, stopped) = oneshot::channel();
    let task = tokio::spawn(server.serve(listener, async {
        let _ = stopped.await;
    }));
    let response =
        tokio::time::timeout(Duration::from_secs(2), request(address, "/v1/health/ready"))
            .await
            .unwrap();
    assert!(serving.is_ready());
    assert!(response.starts_with("HTTP/1.1 200"));
    assert!(response.ends_with("{\"status\":\"ok\"}\n"));
    stop.send(()).unwrap();
    tokio::time::timeout(Duration::from_secs(2), task)
        .await
        .unwrap()
        .unwrap()
        .unwrap();
    assert!(!serving.is_ready());
    assert!(TcpStream::connect(address).await.is_err());
}

#[tokio::test]
async fn selected_dependency_probe_fails_readiness_while_liveness_still_succeeds() {
    struct Failed;
    impl Readiness for Failed {
        fn check(&self) -> ProbeFuture<'_> {
            Box::pin(async {
                Err(Failure::new(
                    Kind::Unavailable,
                    "fixture.delivery_exhausted",
                    "SECRET",
                ))
            })
        }
    }
    let server = server(Duration::from_secs(1)).with_probe(Arc::new(Failed));
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let address = listener.local_addr().unwrap();
    let (stop, stopped) = oneshot::channel();
    let task = tokio::spawn(server.serve(listener, async {
        let _ = stopped.await;
    }));
    let ready = request(address, "/v1/health/ready").await;
    assert!(ready.starts_with("HTTP/1.1 503"));
    assert!(!ready.contains("SECRET"));
    assert!(
        request(address, "/v1/health/live")
            .await
            .starts_with("HTTP/1.1 200")
    );
    stop.send(()).unwrap();
    task.await.unwrap().unwrap();
}

#[tokio::test]
async fn readiness_closes_before_active_request_finishes_draining() {
    let server = server(Duration::from_secs(1));
    let serving = server.serving();
    let entered = Arc::new(Notify::new());
    let release = Arc::new(Notify::new());
    let route = Router::new().route(
        "/slow",
        get({
            let entered = entered.clone();
            let release = release.clone();
            move || {
                let entered = entered.clone();
                let release = release.clone();
                async move {
                    entered.notify_one();
                    release.notified().await;
                    "completed"
                }
            }
        }),
    );
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let address = listener.local_addr().unwrap();
    let (stop, stopped) = oneshot::channel();
    let task = tokio::spawn(server.serve_router(
        listener,
        async {
            let _ = stopped.await;
        },
        route,
    ));
    let client = tokio::spawn(async move { request(address, "/slow").await });
    tokio::time::timeout(Duration::from_secs(2), entered.notified())
        .await
        .unwrap();
    assert!(serving.is_ready());
    stop.send(()).unwrap();
    tokio::time::timeout(Duration::from_secs(2), async {
        while serving.is_ready() {
            tokio::task::yield_now().await;
        }
    })
    .await
    .unwrap();
    assert!(!task.is_finished());
    release.notify_one();
    let response = tokio::time::timeout(Duration::from_secs(2), client)
        .await
        .unwrap()
        .unwrap();
    assert!(response.ends_with("completed"));
    task.await.unwrap().unwrap();
}

struct Dropped(Arc<AtomicBool>);
impl Drop for Dropped {
    fn drop(&mut self) {
        self.0.store(true, Ordering::Release);
    }
}

#[tokio::test]
async fn drain_timeout_aborts_and_joins_owned_connection_tasks() {
    let server = server(Duration::from_millis(20));
    let serving = server.serving();
    let entered = Arc::new(Notify::new());
    let dropped = Arc::new(AtomicBool::new(false));
    let route = Router::new().route(
        "/blocked",
        get({
            let entered = entered.clone();
            let dropped = dropped.clone();
            move || {
                let entered = entered.clone();
                let dropped = dropped.clone();
                async move {
                    let _guard = Dropped(dropped);
                    entered.notify_one();
                    std::future::pending::<()>().await;
                    "unreachable"
                }
            }
        }),
    );
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let address = listener.local_addr().unwrap();
    let (stop, stopped) = oneshot::channel();
    let task = tokio::spawn(server.serve_router(
        listener,
        async {
            let _ = stopped.await;
        },
        route,
    ));
    let client = tokio::spawn(async move { request(address, "/blocked").await });
    tokio::time::timeout(Duration::from_secs(2), entered.notified())
        .await
        .unwrap();
    stop.send(()).unwrap();
    let error = tokio::time::timeout(Duration::from_secs(2), task)
        .await
        .unwrap()
        .unwrap()
        .unwrap_err();
    assert_eq!(error.kind(), Kind::Timeout);
    assert!(!serving.is_ready());
    assert!(dropped.load(Ordering::Acquire));
    client.await.unwrap();
    assert!(TcpStream::connect(address).await.is_err());
}
