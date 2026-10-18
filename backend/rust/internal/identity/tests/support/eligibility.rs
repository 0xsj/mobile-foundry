use events::dispatcher::Control;
use identity::{
    app::{
        ports::{Purpose, Store, Tokens},
        query::session_eligibility::SessionStatus,
    },
    domain::{DisplayName, Login, Principal, Secret, Session, Timestamp},
    infra::security::TokenAdapter,
};
use kernel::id::{Generator, Id, RandomIds};
use std::sync::Arc;
pub const AT: u64 = 1_800_000_000_000_000;
pub fn id() -> Id {
    RandomIds.new_id().unwrap()
}
pub async fn issued<T: Store>(store: &T) -> Session {
    let s = Control::default();
    let at = Timestamp::from_micros(AT).unwrap();
    let actor = id();
    let hash = Secret::new("PRIVATE-checkpoint".into());
    let p = Principal::new(
        actor.clone(),
        Login::new(actor.as_str()).unwrap(),
        DisplayName::new("PRIVATE actor").unwrap(),
        hash.clone(),
        at,
    )
    .unwrap();
    store.register(&s, p).await.unwrap();
    let session = Session::new(
        id(),
        actor,
        TokenAdapter.issue(&s, Purpose::Access).unwrap().digest,
        TokenAdapter.issue(&s, Purpose::Refresh).unwrap().digest,
        at,
    )
    .unwrap();
    store
        .issue_checked(&s, session.clone(), &hash)
        .await
        .unwrap();
    session
}
pub async fn conformance<T: Store + SessionStatus + 'static>(store: Arc<T>) {
    let s = Control::default();
    let original = issued(store.as_ref()).await;
    let v = original.state();
    let cases: Vec<serde_json::Value> = serde_json::from_str(include_str!(
        "../../../../../../contracts/fixtures/backend-push/sessions.json"
    ))
    .unwrap();
    for c in cases {
        let principal = if c["wrong_principal"] == true {
            id()
        } else {
            v.principal_id.clone()
        };
        let session = if c["missing_session"] == true {
            id()
        } else {
            v.id.clone()
        };
        let now = Timestamp::from_micros((AT as i64 + c["offset_micros"].as_i64().unwrap()) as u64)
            .unwrap();
        assert_eq!(
            store.eligible(&s, &principal, &session, now).await.unwrap(),
            c["eligible"].as_bool().unwrap(),
            "{}",
            c["name"]
        );
    }
    let renewed = TokenAdapter.issue(&s, Purpose::Refresh).unwrap().digest;
    store
        .rotate(
            &s,
            v.refresh.clone(),
            TokenAdapter.issue(&s, Purpose::Access).unwrap().digest,
            renewed.clone(),
            Timestamp::from_micros(AT + 60_000_000).unwrap(),
        )
        .await
        .unwrap();
    let later = Timestamp::from_micros(AT + 1_200_000_000).unwrap();
    assert!(
        store
            .eligible(&s, &v.principal_id, &v.id, later)
            .await
            .unwrap()
    );
    store.revoke(&s, renewed, later).await.unwrap();
    assert!(
        !store
            .eligible(&s, &v.principal_id, &v.id, later)
            .await
            .unwrap()
    );
    let canceled = Control::default();
    canceled.cancel();
    assert!(
        store
            .eligible(&canceled, &v.principal_id, &v.id, later)
            .await
            .is_err()
    );
}
