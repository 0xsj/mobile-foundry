#[path = "support/push_http.rs"]
#[allow(dead_code)]
mod api;
use kernel::{
    context::{PortFuture, Scope},
    id::{Generator, Id, RandomIds},
};
use notifications::app::push::{PushAddress, PushDisposition, PushGateway};
use serde_json::json;
use std::sync::Arc;
fn id() -> String {
    RandomIds.new_id().unwrap().to_string()
}
struct Never;
impl PushGateway for Never {
    fn send_preview<'a>(
        &'a self,
        _: &'a dyn Scope,
        _: PushAddress,
        _: Id,
    ) -> PortFuture<'a, PushDisposition> {
        Box::pin(async { panic!("HTTP mutation implicitly started a worker") })
    }
}
#[tokio::test]
async fn explicit_queue_cancel_and_replacement_without_implicit_worker() {
    let clock = Arc::new(kernel::clock::SystemClock);
    let ids = Arc::new(RandomIds);
    let account = account::Module::memory(clock.clone(), ids.clone());
    let identity = identity::Module::memory(clock.clone(), ids.clone())
        .await
        .unwrap();
    let mut builder = capability::Builder::default();
    account.register(&mut builder).unwrap();
    identity.register(&mut builder).unwrap();
    let registry = builder.freeze();
    let access = Arc::new(notifications::infra::capabilities::Client(Arc::new(
        registry.client("notifications").unwrap(),
    )));
    let principals = Arc::new(principal::capabilities::Resolver(Arc::new(
        registry.client("api").unwrap(),
    )));
    let module = notifications::Module::memory(clock, access, principals)
        .with_push(&registry, Arc::new(Never))
        .unwrap()
        .with_push_jobs(ids)
        .unwrap();
    let router = module.routes().merge(identity.routes());
    let grant = api::login(&router).await;
    let token = grant["access_token"].as_str().unwrap();
    let installation = id();
    let source = id();
    let child = id();
    let proof = "i1.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    let read = vec![("X-Installation-Key".into(), proof.into())];
    let mut write = read.clone();
    write.push(("If-Match".into(), "\"1\"".into()));
    assert_eq!(api::call(&router,"POST","/v1/installations",token,&json!({"installation_id":installation,"registration_id":id(),"provider":"fcm","application":"dev.example.jobs","environment":"production","token":"PRIVATE-installation-address"}).to_string(),&read).await.0,201);
    let base = format!("/v1/installations/{installation}/push-jobs");
    let (status, headers, value) = api::call(
        &router,
        "POST",
        &base,
        token,
        &json!({"job_id":source}).to_string(),
        &write,
    )
    .await;
    assert_eq!(status, 202);
    assert_eq!(value.as_object().unwrap().len(), 8);
    assert_eq!(value["status"], "queued");
    assert!(!headers.contains_key("etag"));
    let path = format!("{base}/{source}/cancel");
    assert_eq!(
        api::call(&router, "POST", &path, token, "{}", &read)
            .await
            .0,
        400
    );
    assert_eq!(
        api::call(&router, "POST", &path, token, "", &write).await.0,
        400
    );
    let (status, _, canceled) = api::call(&router, "POST", &path, token, "", &read).await;
    assert_eq!(status, 200);
    assert_eq!(canceled["status"], "canceled");
    assert_eq!(
        api::call(&router, "POST", &path, token, "", &read).await.2,
        canceled
    );
    let path = format!("{base}/{source}/replacement");
    let body = json!({"job_id":child,"accept_possible_duplicate":false}).to_string();
    let (status, _, replacement) = api::call(&router, "POST", &path, token, &body, &write).await;
    assert_eq!(status, 202);
    assert_eq!(replacement["status"], "queued");
    assert_eq!(replacement["attempts"], 0);
    assert_eq!(
        api::call(&router, "GET", &path, token, "", &read).await.2,
        replacement
    );
    assert_eq!(
        api::call(&router, "POST", &path, token, &body, &write)
            .await
            .2,
        replacement
    );
    assert_eq!(
        api::call(
            &router,
            "GET",
            &format!("{base}/{source}"),
            token,
            "",
            &read
        )
        .await
        .2,
        canceled
    );
    assert_eq!(
        api::call(
            &router,
            "POST",
            &base,
            token,
            &json!({"job_id":child}).to_string(),
            &write
        )
        .await
        .0,
        409
    );
    assert_eq!(
        api::call(
            &router,
            "POST",
            &format!("{base}/{child}/cancel"),
            token,
            "",
            &read
        )
        .await
        .0,
        200
    );
    identity.close().await.unwrap();
}
