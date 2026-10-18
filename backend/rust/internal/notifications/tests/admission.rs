use serde_json::Value;
#[test]
fn shared_event_admission_and_fingerprints() {
    let cases: Vec<Value> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-notifications/events.json"
    ))
    .unwrap();
    for c in cases {
        let result = postgres_storage::outbox::decode(c["envelope"].clone()).and_then(|mut m| {
            if let Some(raw) = c["data_wire"].as_str() {
                m.data = raw.as_bytes().to_vec();
            }
            let d = notifications::infra::events::admit(&m)?;
            assert_eq!(d.principal_id, m.actor_id);
            assert_eq!(d.event_id, m.id);
            Ok(d)
        });
        assert_eq!(
            result.is_ok(),
            c["valid"].as_bool().unwrap(),
            "{}",
            c["name"]
        );
    }
    let cases: Vec<Value> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-notifications/fingerprints.json"
    ))
    .unwrap();
    for c in cases {
        let i = &c["input"];
        let m=postgres_storage::outbox::decode(serde_json::json!({"id":i["event_id"],"name":"files.upload_completed.v1","producer":"files","account_id":i["account_id"],"actor_id":i["principal_id"],"aggregate_version":2,"occurred_at":i["occurred_at"],"data":{"file_id":i["file_id"],"account_id":i["account_id"],"version":2}})).unwrap();
        let d = notifications::infra::events::admit(&m).unwrap();
        assert_eq!(
            notifications::infra::records::fingerprint(&d).unwrap(),
            c["sha256"].as_str().unwrap()
        );
    }
}
