use files::{
    app::{Action, Command},
    domain::{File, Spec, Status},
};
use serde_json::Value;
fn spec(v: &Value) -> Spec {
    Spec {
        name: v["name"].as_str().unwrap().into(),
        content_type: v["content_type"].as_str().unwrap().into(),
        byte_size: v["byte_size"].as_u64().unwrap(),
        sha256: v["sha256"].as_str().unwrap().into(),
    }
}
#[test]
fn shared_spec_policy_and_command_fingerprints() {
    let cases: Vec<Value> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-files/specs.json"
    ))
    .unwrap();
    for c in cases {
        let r = spec(&c["input"]).normalize();
        assert_eq!(r.is_ok(), c["valid"].as_bool().unwrap(), "{}", c["name"]);
        if let Ok(s) = r {
            assert_eq!(s.name, c["canonical"]["name"].as_str().unwrap());
        }
    }
    let cases: Vec<Value> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-files/fingerprints.json"
    ))
    .unwrap();
    for c in cases {
        let v = &c["input"];
        let action = Action::parse(v["action"].as_str().unwrap()).unwrap();
        let command = Command {
            action,
            key: String::new(),
            file_id: if action == Action::Request {
                None
            } else {
                Some(kernel::id::Id::parse(v["file_id"].as_str().unwrap()).unwrap())
            },
            spec: if action == Action::Request {
                Some(spec(v))
            } else {
                None
            },
            expected: v["expected_version"].as_u64().unwrap(),
        };
        let digest = files::infra::wire::fingerprint(&command).unwrap();
        let hex: String = digest.iter().map(|b| format!("{b:02x}")).collect();
        assert_eq!(hex, c["sha256"].as_str().unwrap());
    }
}
#[test]
fn private_state_restoration_and_time_bounds() {
    use kernel::id::{Generator, RandomIds};
    let s = Spec {
        name: "Image".into(),
        content_type: "image/png".into(),
        byte_size: 3,
        sha256: "0".repeat(64),
    };
    let f = File::new(
        RandomIds.new_id().unwrap(),
        RandomIds.new_id().unwrap(),
        RandomIds.new_id().unwrap(),
        s,
        99,
    )
    .unwrap();
    assert!(f.complete(98).is_err());
    let completed = f.complete(100).unwrap();
    assert!(completed.complete(101).is_err());
    let deleted = completed.delete(102).unwrap();
    assert_eq!(deleted.snapshot().version, 3);
    assert!(deleted.delete(103).is_err());
    let mut bad = f.snapshot().clone();
    bad.status = Status::Available;
    assert!(File::restore(bad).is_err());
    let wire = files::infra::wire::encode(&completed).unwrap();
    assert_eq!(files::infra::wire::decode(wire).unwrap(), completed);
}
