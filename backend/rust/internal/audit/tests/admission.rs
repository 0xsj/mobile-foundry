use audit::{
    app::{
        command::Record,
        ports::{PrincipalStore, Store},
    },
    infra::{
        events::{Receiver, Source},
        memory::MemoryStore,
    },
};
use events::{Consumer, Envelope, dispatcher::Control};
use kernel::{context::Metadata, id::Id};
use serde::Deserialize;
use std::{
    sync::Arc,
    time::{Duration, SystemTime, UNIX_EPOCH},
};
struct Fixed;
impl kernel::clock::Clock for Fixed {
    fn now(&self) -> SystemTime {
        UNIX_EPOCH + Duration::from_secs(1_800_000_000)
    }
}
#[derive(Deserialize)]
struct Case {
    name: String,
    source: String,
    valid: bool,
    envelope: serde_json::Value,
    data_wire: Option<String>,
}
fn envelope(case: &Case) -> Option<Envelope> {
    let e = &case.envelope;
    let id = |key| Id::parse(e[key].as_str()?).ok();
    let value = chrono::DateTime::parse_from_rfc3339(e["occurred_at"].as_str()?).ok()?;
    Some(Envelope {
        id: id("id")?,
        name: e["name"].as_str()?.into(),
        producer: e["producer"].as_str()?.into(),
        account_id: if e["account_id"].is_null() {
            None
        } else {
            Some(id("account_id")?)
        },
        actor_id: id("actor_id")?,
        version: e["aggregate_version"].as_u64()?,
        occurred_at: value.timestamp_micros().try_into().ok()?,
        metadata: Metadata {
            request_id: e["metadata"]["request_id"]
                .as_str()
                .unwrap_or_default()
                .into(),
            correlation_id: e["metadata"]["correlation_id"].as_str().map(str::to_string),
        },
        data: case
            .data_wire
            .as_ref()
            .map(|v| v.as_bytes().to_vec())
            .unwrap_or_else(|| serde_json::to_vec(&e["data"]).unwrap()),
    })
}
#[tokio::test]
async fn shared_event_admission_and_selected_evidence() {
    let mut cases: Vec<Case> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-audit/events.json"
    ))
    .unwrap();
    cases.extend(
        serde_json::from_str::<Vec<Case>>(include_str!(
            "../../../../../contracts/fixtures/backend-recovery/events.json"
        ))
        .unwrap(),
    );
    cases.extend(
        serde_json::from_str::<Vec<Case>>(include_str!(
            "../../../../../contracts/fixtures/backend-files/events.json"
        ))
        .unwrap(),
    );
    for case in cases {
        let store = Arc::new(MemoryStore::default());
        let receiver = Receiver {
            record: Record {
                store: store.clone(),
                clock: Arc::new(Fixed),
            },
            source: if case.source == "account" {
                Source::Account
            } else if case.source == "files" {
                Source::Files
            } else {
                Source::Identity
            },
        };
        let message = envelope(&case);
        let valid = if let Some(message) = &message {
            receiver
                .handle(&Control::default(), message.clone())
                .await
                .is_ok()
        } else {
            false
        };
        assert_eq!(valid, case.valid, "{}", case.name);
        if valid {
            let message = message.unwrap();
            let page = if let Some(id) = &message.account_id {
                store.list(&Control::default(), id, 0, 100).await.unwrap()
            } else {
                store
                    .list_principal(&Control::default(), &message.actor_id, 0, 100)
                    .await
                    .unwrap()
            };
            assert_eq!(page.records.len(), 1);
            let evidence = page.records[0].entry.evidence();
            assert_eq!(
                evidence.action,
                message.name.trim_end_matches(".v1").trim_end_matches(".v2")
            );
            assert_eq!(evidence.request_id, message.metadata.request_id);
            assert_eq!(evidence.version, message.version);
        }
    }
}
#[tokio::test(flavor = "multi_thread", worker_threads = 4)]
async fn concurrent_inbox_replay_is_atomic_and_preserves_first_recorded_time() {
    let cases: Vec<Case> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-audit/events.json"
    ))
    .unwrap();
    let message = envelope(&cases[0]).unwrap();
    let account = message.account_id.clone().unwrap();
    let store = Arc::new(MemoryStore::default());
    let receiver = Arc::new(Receiver {
        record: Record {
            store: store.clone(),
            clock: Arc::new(Fixed),
        },
        source: Source::Account,
    });
    let barrier = Arc::new(tokio::sync::Barrier::new(8));
    let mut tasks = Vec::new();
    for _ in 0..8 {
        let (receiver, barrier, message) = (receiver.clone(), barrier.clone(), message.clone());
        tasks.push(tokio::spawn(async move {
            barrier.wait().await;
            receiver.handle(&Control::default(), message).await.unwrap()
        }));
    }
    for task in tasks {
        task.await.unwrap();
    }
    let first = store
        .list(&Control::default(), &account, 0, 100)
        .await
        .unwrap();
    assert_eq!(first.records.len(), 1);
    let mut changed = message.clone();
    changed.actor_id = Id::parse("00000000-0000-4000-8000-000000000099").unwrap();
    assert_eq!(
        receiver
            .handle(&Control::default(), changed)
            .await
            .unwrap_err()
            .public()
            .code
            .as_deref(),
        Some("audit.event_conflict")
    );
    let entry = audit::domain::Entry::new(
        first.records[0].entry.evidence().clone(),
        Fixed.now() + Duration::from_secs(99),
    )
    .unwrap();
    store
        .record(&Control::default(), "audit.account", entry)
        .await
        .unwrap();
    let again = store
        .list(&Control::default(), &account, 0, 100)
        .await
        .unwrap();
    assert_eq!(again.records.len(), 1);
    assert_eq!(
        again.records[0].entry.recorded_at(),
        first.records[0].entry.recorded_at()
    );
    assert!(
        store
            .list(&Control::default(), &message.actor_id, 0, 100)
            .await
            .unwrap()
            .records
            .is_empty()
    );
}
use kernel::clock::Clock;
