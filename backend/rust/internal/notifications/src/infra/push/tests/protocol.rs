use super::*;
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use ring::signature::{self, KeyPair, UnparsedPublicKey};
use std::sync::atomic::AtomicUsize;
use tls::{Material, Reply, Server};
fn jwt(value: &str) -> (serde_json::Value, serde_json::Value, Vec<u8>, Vec<u8>) {
    let parts: Vec<_> = value.split('.').collect();
    assert_eq!(parts.len(), 3);
    (
        serde_json::from_slice(&URL_SAFE_NO_PAD.decode(parts[0]).unwrap()).unwrap(),
        serde_json::from_slice(&URL_SAFE_NO_PAD.decode(parts[1]).unwrap()).unwrap(),
        format!("{}.{}", parts[0], parts[1]).into_bytes(),
        URL_SAFE_NO_PAD.decode(parts[2]).unwrap(),
    )
}
#[tokio::test]
async fn apns_http2_signatures_shared_responses_and_cache() {
    let material = Material::new();
    let clock = Arc::new(TestClock(AtomicU64::new(NOW)));
    let address = address("apns");
    let preview = id();
    let transport = material.transport();
    let mut sender = apns::Sender::new(
        address.spec.route.clone(),
        "TEAMID0001".into(),
        "KEYID00001".into(),
        &material.apns,
        clock.clone(),
        transport.clone(),
    )
    .unwrap();
    let public = sender.key.public_key().as_ref().to_vec();
    let expected_preview = preview.clone();
    let calls = Arc::new(AtomicUsize::new(0));
    let count = calls.clone();
    let row = Arc::new(Mutex::new((200, String::new())));
    let selected = row.clone();
    let server = Server::start(&material, move |r| {
        count.fetch_add(1, Ordering::SeqCst);
        assert_eq!(r.version, hyper::Version::HTTP_2);
        assert_eq!(r.path, "/3/device/000102aaff");
        for (k, v) in [
            ("apns-topic", "dev.example.app"),
            ("apns-push-type", "alert"),
            ("apns-priority", "10"),
            ("apns-expiration", "0"),
            ("apns-id", expected_preview.as_str()),
        ] {
            assert_eq!(r.headers[k], v);
        }
        let (header, claims, input, sig) = jwt(r.headers["authorization"]
            .to_str()
            .unwrap()
            .strip_prefix("bearer ")
            .unwrap());
        assert_eq!(
            header,
            serde_json::json!({"alg":"ES256","kid":"KEYID00001"})
        );
        assert_eq!(
            claims,
            serde_json::json!({"iss":"TEAMID0001","iat":NOW/1_000_000})
        );
        assert_eq!(sig.len(), 64);
        UnparsedPublicKey::new(&signature::ECDSA_P256_SHA256_FIXED, &public)
            .verify(&input, &sig)
            .unwrap();
        let body: serde_json::Value = serde_json::from_slice(&r.body).unwrap();
        let mut expected = data(&expected_preview);
        expected["aps"] =
            serde_json::json!({"alert":{"title":TITLE,"body":BODY},"sound":"default"});
        assert_eq!(body, expected);
        let (status, body) = selected.lock().unwrap().clone();
        Reply::new(status, body)
    })
    .await;
    sender.endpoint = server.url.clone();
    for case in corpus().cases.into_iter().filter(|r| r.provider == "apns") {
        *row.lock().unwrap() = (case.status, case.body);
        let before = calls.load(Ordering::SeqCst);
        assert_eq!(
            outcome(
                sender
                    .send(&Control::default(), address.clone(), preview.clone())
                    .await
            ),
            case.expected,
            "{}",
            case.name
        );
        assert_eq!(calls.load(Ordering::SeqCst), before + 1);
    }
    let first = sender.authorization().unwrap();
    assert_eq!(first.expose(), sender.authorization().unwrap().expose());
    clock.0.store(NOW + 50 * 60 * 1_000_000, Ordering::SeqCst);
    assert_ne!(first.expose(), sender.authorization().unwrap().expose());
    clock.0.store(NOW - 1_000_000, Ordering::SeqCst);
    let back = sender.authorization().unwrap();
    let (_, claims, _, _) = jwt(back.expose());
    assert_eq!(claims["iat"], (NOW - 1_000_000) / 1_000_000);
    let before = calls.load(Ordering::SeqCst);
    for bad in ["UPPERCASE", "a", "abc", "ab/00", ""] {
        let mut a = address.clone();
        a.spec.token = Secret::new(bad.into());
        assert_eq!(
            sender
                .send(&Control::default(), a, preview.clone())
                .await
                .unwrap_err()
                .public()
                .code
                .unwrap(),
            "push.route_not_selected"
        );
    }
    assert_eq!(calls.load(Ordering::SeqCst), before);
    transport.close();
    server.close().await;
}
#[tokio::test]
async fn fcm_signed_oauth_coalescing_shared_responses_and_refresh() {
    let material = Material::new();
    let clock = Arc::new(TestClock(AtomicU64::new(NOW)));
    let address = address("fcm");
    let preview = id();
    let transport = material.transport();
    let mut sender = fcm::Sender::new(
        address.spec.route.clone(),
        "test-project".into(),
        &serde_json::to_vec(&material.service_account()).unwrap(),
        clock.clone(),
        transport.clone(),
    )
    .unwrap();
    let public = std::fs::read(material.path.join("public.der")).unwrap();
    let expected_preview = preview.clone();
    let oauth = Arc::new(AtomicUsize::new(0));
    let refreshes = oauth.clone();
    let sends = Arc::new(AtomicUsize::new(0));
    let calls = sends.clone();
    let row = Arc::new(Mutex::new((
        200,
        "{\"name\":\"projects/test-project/messages/accepted\"}".into(),
    )));
    let selected = row.clone();
    let server=Server::start(&material,move |r| {
        if r.path=="/token" {
            refreshes.fetch_add(1,Ordering::SeqCst);assert!(!r.headers.contains_key("authorization"));assert_eq!(r.headers["content-type"],"application/x-www-form-urlencoded");
            let form=httpx::query::unique(std::str::from_utf8(&r.body).unwrap()).unwrap();assert_eq!(form["grant_type"],"urn:ietf:params:oauth:grant-type:jwt-bearer");let (header,claims,input,sig)=jwt(&form["assertion"]);
            assert_eq!(header,serde_json::json!({"alg":"RS256","kid":"test-key","typ":"JWT"}));assert_eq!(claims["iss"],"sender@test-project.iam.gserviceaccount.com");assert_eq!(claims["aud"],fcm::OAUTH);assert_eq!(claims["scope"],"https://www.googleapis.com/auth/firebase.messaging");assert_eq!(claims["exp"].as_u64().unwrap()-claims["iat"].as_u64().unwrap(),3600);UnparsedPublicKey::new(&signature::RSA_PKCS1_2048_8192_SHA256,&public).verify(&input,&sig).unwrap();
            return Reply::new(200,"{\"access_token\":\"test-only-access\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        }
        calls.fetch_add(1,Ordering::SeqCst);assert_eq!(r.path,"/v1/projects/test-project/messages:send");assert_eq!(r.headers["authorization"],"Bearer test-only-access");
        let payload:serde_json::Value=serde_json::from_slice(&r.body).unwrap();assert_eq!(payload,serde_json::json!({"message":{"token":"opaque:registration-address","notification":{"title":TITLE,"body":BODY},"data":data(&expected_preview),"android":{"ttl":"0s","restricted_package_name":"dev.example.app"}}}));
        let (status,body)=selected.lock().unwrap().clone();Reply::new(status,body)
    }).await;
    sender.token_url = format!("{}/token", server.url);
    sender.endpoint = format!("{}/v1/projects/test-project/messages:send", server.url);
    let sender = Arc::new(sender);
    let mut tasks = tokio::task::JoinSet::new();
    for _ in 0..8 {
        let sender = sender.clone();
        let address = address.clone();
        let preview = preview.clone();
        tasks.spawn(async move {
            sender
                .send(&Control::default(), address, preview)
                .await
                .unwrap()
        });
    }
    while let Some(task) = tasks.join_next().await {
        assert_eq!(task.unwrap(), PushDisposition::Accepted);
    }
    assert_eq!(oauth.load(Ordering::SeqCst), 1);
    assert_eq!(sends.load(Ordering::SeqCst), 8);
    for case in corpus().cases.into_iter().filter(|r| r.provider == "fcm") {
        *row.lock().unwrap() = (case.status, case.body);
        let before = sends.load(Ordering::SeqCst);
        assert_eq!(
            outcome(
                sender
                    .send(&Control::default(), address.clone(), preview.clone())
                    .await
            ),
            case.expected,
            "{}",
            case.name
        );
        assert_eq!(sends.load(Ordering::SeqCst), before + 1);
    }
    assert_eq!(oauth.load(Ordering::SeqCst), 1);
    for now in [NOW + 3_539_000_000, NOW + 3_540_000_000, NOW - 1_000_000] {
        clock.0.store(now, Ordering::SeqCst);
        sender.authorization(&Control::default()).await.unwrap();
    }
    assert_eq!(oauth.load(Ordering::SeqCst), 3);
    // A canceled cache waiter must not start an OAuth call or a send.
    let guard = sender.cache.lock().await;
    let control = Control::default();
    let waiting = sender.authorization(&control);
    tokio::pin!(waiting);
    tokio::select! {_=&mut waiting=>panic!("cache lock was not held"),_=tokio::time::sleep(Duration::from_millis(30))=>{}}
    control.cancel();
    let result = tokio::time::timeout(Duration::from_millis(100), &mut waiting)
        .await
        .unwrap();
    assert_eq!(result.unwrap_err().kind(), Kind::Canceled);
    drop(guard);
    transport.close();
    server.close().await;
}
#[tokio::test]
async fn transport_bounds_tls_redirects_cancellation_and_close() {
    let material = Material::new();
    let calls = Arc::new(AtomicUsize::new(0));
    let seen = calls.clone();
    let server = Server::start(&material, move |r| {
        seen.fetch_add(1, Ordering::SeqCst);
        match r.path.as_str() {
            "/large" => Reply::new(200, "private-provider-details".repeat(1000)),
            "/redirect" => {
                let mut r = Reply::new(307, "");
                r.location = Some("/followed".into());
                r
            }
            "/stall" => {
                let mut r = Reply::new(200, "");
                r.delay = Duration::from_secs(10);
                r
            }
            _ => Reply::new(200, ""),
        }
    })
    .await;
    let transport = material.transport();
    let scope = Control::default();
    assert_eq!(
        transport
            .response(
                &scope,
                true,
                &format!("{}/redirect", server.url),
                HeaderMap::new(),
                vec![]
            )
            .await
            .unwrap()
            .0,
        307
    );
    assert_eq!(calls.load(Ordering::SeqCst), 1);
    assert_eq!(
        transport
            .response(
                &scope,
                true,
                &format!("{}/large", server.url),
                HeaderMap::new(),
                vec![]
            )
            .await
            .unwrap_err()
            .public()
            .code
            .unwrap(),
        "push.provider_unavailable"
    );
    let raw = Transport::new().unwrap();
    assert!(
        raw.response(&scope, true, &server.url, HeaderMap::new(), vec![])
            .await
            .is_err()
    );
    let wrong_host = server.url.replace("localhost", "127.0.0.1");
    assert!(
        transport
            .response(&scope, true, &wrong_host, HeaderMap::new(), vec![])
            .await
            .is_err()
    );
    let control = Control::default();
    let url = format!("{}/stall", server.url);
    let future = transport.response(&control, true, &url, HeaderMap::new(), vec![]);
    tokio::pin!(future);
    tokio::select! { _=&mut future=>panic!("stalled response returned early"),_=tokio::time::sleep(Duration::from_millis(100))=>{} }
    control.cancel();
    assert_eq!(
        tokio::time::timeout(Duration::from_millis(100), &mut future)
            .await
            .unwrap()
            .unwrap_err()
            .kind(),
        Kind::Canceled
    );
    transport.close();
    assert!(transport.client(true).is_err());
    raw.close();
    server.close().await;
}

#[tokio::test]
async fn oauth_failure_never_reaches_the_message_endpoint() {
    let material = Material::new();
    let transport = material.transport();
    let address = address("fcm");
    let mut sender = fcm::Sender::new(
        address.spec.route.clone(),
        "test-project".into(),
        &serde_json::to_vec(&material.service_account()).unwrap(),
        Arc::new(TestClock(AtomicU64::new(NOW))),
        transport.clone(),
    )
    .unwrap();
    let response = Arc::new(Mutex::new((200, String::new())));
    let selected = response.clone();
    let requests = Arc::new(AtomicUsize::new(0));
    let seen = requests.clone();
    let server = Server::start(&material, move |r| {
        assert_eq!(r.path, "/token", "OAuth refusal must not send a message");
        seen.fetch_add(1, Ordering::SeqCst);
        let (status, body) = selected.lock().unwrap().clone();
        Reply::new(status, body)
    })
    .await;
    sender.token_url = format!("{}/token", server.url);
    sender.endpoint = format!("{}/send", server.url);
    let valid = serde_json::json!({"access_token":"private-oauth-token","token_type":"Bearer","expires_in":3600});
    let mut bodies = vec![
        "private-provider-details".into(),
        "{}".into(),
        "private-provider-details".repeat(1000),
    ];
    for (field, value) in [
        ("access_token", serde_json::json!("")),
        ("access_token", serde_json::json!("private invalid token")),
        ("access_token", serde_json::json!("x".repeat(4097))),
        ("token_type", serde_json::json!("Basic")),
        ("expires_in", serde_json::json!(0)),
        ("expires_in", serde_json::json!(3601)),
        ("expires_in", serde_json::json!("3600")),
    ] {
        let mut token = valid.clone();
        token[field] = value;
        bodies.push(token.to_string());
    }
    for body in bodies {
        *response.lock().unwrap() = (200, body);
        let before = requests.load(Ordering::SeqCst);
        assert_eq!(
            outcome(
                sender
                    .send(&Control::default(), address.clone(), id())
                    .await
            ),
            "push.provider_unavailable"
        );
        assert_eq!(requests.load(Ordering::SeqCst), before + 1);
    }
    for (status, expected) in [
        (401, "push.provider_credentials"),
        (403, "push.provider_credentials"),
        (429, "push.provider_limited"),
        (503, "push.provider_unavailable"),
    ] {
        *response.lock().unwrap() = (status, "private-provider-details".into());
        let before = requests.load(Ordering::SeqCst);
        assert_eq!(
            outcome(
                sender
                    .send(&Control::default(), address.clone(), id())
                    .await
            ),
            expected
        );
        assert_eq!(requests.load(Ordering::SeqCst), before + 1);
    }
    transport.close();
    server.close().await;
}

#[tokio::test]
async fn facade_closes_retained_gateways_and_refuses_unselected_routes_offline() {
    let material = Material::new();
    let path = material.path.join("push.json");
    let config = serde_json::json!({"routes":[{"provider":"apns","application":"dev.example.app","environment":"sandbox","team_id":"TEAMID0001","key_id":"KEYID00001","key_file":"apns.p8"}]});
    std::fs::write(&path, config.to_string()).unwrap();
    let owner = crate::load_push_gateway(
        Some(path.to_str().unwrap()),
        Arc::new(TestClock(AtomicU64::new(NOW))),
    )
    .unwrap()
    .unwrap();
    let gateway = owner.gateway();
    let mut target = address("apns");
    target.spec.route.application = "dev.other.app".into();
    assert_eq!(
        outcome(
            gateway
                .send_preview(&Control::default(), target, id())
                .await
        ),
        "push.route_not_selected"
    );
    let mut target = address("apns");
    target.spec.token = Secret::new("UPPERCASE".into());
    assert_eq!(
        outcome(
            gateway
                .send_preview(&Control::default(), target, id())
                .await
        ),
        "push.route_not_selected"
    );
    drop(owner);
    assert_eq!(
        outcome(
            gateway
                .send_preview(&Control::default(), address("apns"), id())
                .await
        ),
        "push.provider_unavailable"
    );
}
