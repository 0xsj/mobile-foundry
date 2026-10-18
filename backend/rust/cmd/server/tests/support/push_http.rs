use axum::{
    Router,
    body::{Body, to_bytes},
    http::{HeaderMap, Request},
};
use kernel::{
    clock::SystemClock,
    context::{PortFuture, Scope},
    id::{Generator, Id, RandomIds},
};
use notifications::app::push::{PushAddress, PushDisposition, PushGateway};
use serde_json::{Value, json};
use std::sync::{
    Arc,
    atomic::{AtomicUsize, Ordering},
};
use tower::ServiceExt;
struct Gateway {
    calls: AtomicUsize,
    outcome: AtomicUsize,
}
impl PushGateway for Gateway {
    fn send_preview<'a>(
        &'a self,
        s: &'a dyn Scope,
        _: PushAddress,
        _: Id,
    ) -> PortFuture<'a, PushDisposition> {
        Box::pin(async move {
            s.check()?;
            self.calls.fetch_add(1, Ordering::SeqCst);
            match self.outcome.load(Ordering::SeqCst) {
                0 => Ok(PushDisposition::Accepted),
                1 => Err(notifications::infra::push::unavailable()),
                _ => Ok(PushDisposition::InvalidTarget),
            }
        })
    }
}
fn id() -> String {
    RandomIds.new_id().unwrap().to_string()
}
pub async fn call(
    router: &Router,
    method: &str,
    path: &str,
    token: &str,
    body: &str,
    headers: &[(String, String)],
) -> (u16, HeaderMap, Value) {
    let mut request = Request::builder()
        .method(method)
        .uri(path)
        .header("content-type", "application/json")
        .header("content-length", body.len().to_string());
    if !token.is_empty() {
        request = request.header("authorization", format!("Bearer {token}"));
    }
    for (k, v) in headers {
        request = request.header(k, v);
    }
    let mut request = request.body(Body::from(body.to_owned())).unwrap();
    request
        .extensions_mut()
        .insert(httpx::RequestContext::new(id(), None));
    request
        .extensions_mut()
        .insert(httpx::PeerAddress("127.0.0.1".parse().unwrap()));
    let log = httpx::logger::Logger::new(
        std::io::sink(),
        runtime_config::LogFormat::Json,
        runtime_config::LogLevel::Debug,
    );
    let response = router
        .clone()
        .layer(axum::middleware::from_fn_with_state(
            httpx::BoundaryState::from(log),
            httpx::boundary,
        ))
        .oneshot(request)
        .await
        .unwrap();
    let (parts, body) = response.into_parts();
    let bytes = to_bytes(body, 65536).await.unwrap();
    let text = String::from_utf8(bytes.to_vec()).unwrap();
    for secret in [
        "PRIVATE-installation-address",
        "i1.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "private test passphrase",
    ] {
        assert!(!text.contains(secret));
    }
    let value = serde_json::from_slice(&bytes).unwrap_or(Value::Null);
    (parts.status.as_u16(), parts.headers, value)
}
pub async fn login(router: &Router) -> Value {
    let name = id();
    let body = json!({"login":name,"display_name":"Person","password":"private test passphrase"})
        .to_string();
    assert_eq!(
        call(router, "POST", "/v1/identity/register", "", &body, &[])
            .await
            .0,
        201
    );
    let body = json!({"login":name,"password":"private test passphrase"}).to_string();
    let (status, _, grant) = call(router, "POST", "/v1/identity/sign-in", "", &body, &[]).await;
    assert_eq!(status, 200, "{grant}");
    grant
}
pub async fn conformance(
    identity_pool: Option<sqlx::PgPool>,
    notifications_pool: Option<sqlx::PgPool>,
) {
    let clock = Arc::new(SystemClock);
    let ids = Arc::new(RandomIds);
    let accounts = account::Module::memory(clock.clone(), ids.clone());
    let identity = match identity_pool {
        Some(pool) => identity::Module::postgres(pool, clock.clone(), ids.clone())
            .await
            .unwrap(),
        None => identity::Module::memory(clock.clone(), ids).await.unwrap(),
    };
    let mut builder = capability::Builder::default();
    accounts.register(&mut builder).unwrap();
    identity.register(&mut builder).unwrap();
    let registry = builder.freeze();
    let principals = Arc::new(principal::capabilities::Resolver(Arc::new(
        registry.client("api").unwrap(),
    )));
    let access = Arc::new(notifications::infra::capabilities::Client(Arc::new(
        registry.client("notifications").unwrap(),
    )));
    let module = match notifications_pool {
        Some(pool) => notifications::Module::postgres(pool, clock, access, principals),
        None => notifications::Module::memory(clock, access, principals),
    };
    let default = module.routes();
    let path = format!("/v1/installations/{}/push-preview", id());
    assert_eq!(call(&default, "POST", &path, "", "{}", &[]).await.0, 404);
    let gateway = Arc::new(Gateway {
        calls: AtomicUsize::new(0),
        outcome: AtomicUsize::new(0),
    });
    let module = module.with_push(&registry, gateway.clone()).unwrap();
    let router = module.routes().merge(identity.routes());
    let owner = login(&router).await;
    let foreign = login(&router).await;
    let token = owner["access_token"].as_str().unwrap();
    let installation = id();
    let preview = id();
    let proof = "i1.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    let create=json!({"installation_id":installation,"registration_id":id(),"provider":"fcm","application":"dev.example.app","environment":"production","token":"PRIVATE-installation-address"}).to_string();
    assert_eq!(
        call(
            &router,
            "POST",
            "/v1/installations",
            token,
            &create,
            &[("X-Installation-Key".into(), proof.into())]
        )
        .await
        .0,
        201
    );
    let path = format!("/v1/installations/{installation}/push-preview");
    let body = json!({"preview_id":preview}).to_string();
    let headers = vec![
        ("X-Installation-Key".into(), proof.into()),
        ("If-Match".into(), "\"1\"".into()),
    ];
    let cases: Vec<Value> = serde_json::from_str(include_str!(
        "../../../../../../contracts/fixtures/backend-push-providers/http.json"
    ))
    .unwrap();
    for case in cases {
        let method = case["method"].as_str().unwrap_or("POST");
        let token = match case["auth"].as_str() {
            Some("anonymous") => "",
            Some("foreign") => foreign["access_token"].as_str().unwrap(),
            _ => token,
        };
        let mut headers = headers.clone();
        match case["proof"].as_str() {
            Some("missing") => headers.retain(|(k, _)| k != "X-Installation-Key"),
            Some("wrong") => headers[0].1 = "i1.BAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".into(),
            _ => {}
        }
        if let Some(version) = case.get("if_match") {
            headers.retain(|(k, _)| k != "If-Match");
            if let Some(v) = version.as_str() {
                headers.push(("If-Match".into(), v.into()));
            }
        }
        if let Some(extra) = case["headers"].as_array() {
            for pair in extra {
                headers.push((
                    pair[0].as_str().unwrap().into(),
                    pair[1].as_str().unwrap().replace("$PROOF$", proof),
                ));
            }
        }
        let body = case["body"]
            .as_str()
            .unwrap_or(&body)
            .replace("$PREVIEW$", &preview);
        let target = format!("{path}{}", case["query"].as_str().unwrap_or(""));
        let before = gateway.calls.load(Ordering::SeqCst);
        let (status, response, value) =
            call(&router, method, &target, token, &body, &headers).await;
        assert_eq!(
            u64::from(status),
            case["status"].as_u64().unwrap(),
            "{}: {value}",
            case["name"]
        );
        assert_eq!(
            gateway.calls.load(Ordering::SeqCst) - before,
            usize::from(status == 200),
            "{}",
            case["name"]
        );
        if status == 200 {
            assert_eq!(
                value,
                json!({"preview_id":preview,"disposition":"accepted","retired":false})
            );
            assert!(!response.contains_key("etag"));
            assert_eq!(response["cache-control"], "no-store");
        }
        if status == 405 {
            assert_eq!(response["allow"], "POST");
        }
    }
    let before = gateway.calls.load(Ordering::SeqCst);
    for _ in 0..2 {
        assert_eq!(
            call(&router, "POST", &path, token, &body, &headers).await.0,
            200
        );
    }
    assert_eq!(gateway.calls.load(Ordering::SeqCst), before + 2);
    gateway.outcome.store(1, Ordering::SeqCst);
    assert_eq!(
        call(&router, "POST", &path, token, &body, &headers).await.0,
        503
    );
    gateway.outcome.store(2, Ordering::SeqCst);
    let (status, _, value) = call(&router, "POST", &path, token, &body, &headers).await;
    assert_eq!(status, 200);
    assert_eq!(value["retired"], true);
    assert_eq!(value["disposition"], "invalid_target");
    let before = gateway.calls.load(Ordering::SeqCst);
    assert_eq!(
        call(&router, "POST", &path, token, &body, &headers).await.0,
        409
    );
    assert_eq!(gateway.calls.load(Ordering::SeqCst), before);
    let (_, _, state) = call(
        &router,
        "GET",
        &format!("/v1/installations/{installation}"),
        token,
        "",
        &[("X-Installation-Key".into(), proof.into())],
    )
    .await;
    assert_eq!(state["registered"], false);
    assert_eq!(state["version"], 2);
    // A different still-enabled binding is refused immediately after revocation,
    // even though this harness deliberately runs no event cleanup worker.
    let another = id();
    let create = create.replace(&installation, &another);
    assert_eq!(
        call(
            &router,
            "POST",
            "/v1/installations",
            token,
            &create,
            &[("X-Installation-Key".into(), proof.into())]
        )
        .await
        .0,
        201
    );
    assert_eq!(
        call(
            &router,
            "POST",
            "/v1/identity/sign-out",
            "",
            &json!({"refresh_token":owner["refresh_token"]}).to_string(),
            &[]
        )
        .await
        .0,
        204
    );
    assert_eq!(
        call(
            &router,
            "POST",
            &format!("/v1/installations/{another}/push-preview"),
            token,
            &body,
            &headers
        )
        .await
        .0,
        401
    );
    assert_eq!(gateway.calls.load(Ordering::SeqCst), before);
    identity.close().await.unwrap();
    println!(
        "Preview HTTP: shared guards, Identity/proof/CAS, no receipts/retry, retirement and revocation passed"
    );
}
