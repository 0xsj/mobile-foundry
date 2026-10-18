use notifications::{
    app::installations::InstallationProofs,
    domain::installation::{InstallationSpec, PushRoute, Secret},
    infra::{installation_records as records, security::Proofs},
};
use serde_json::Value;
#[test]
fn shared_proofs_routes_and_target_digests() {
    let s = events::dispatcher::Control::default();
    let cases: Vec<Value> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-installations/proofs.json"
    ))
    .unwrap();
    for c in cases {
        let raw = Secret::new(c["raw"].as_str().unwrap().into());
        let got = Proofs.digest(&s, &raw);
        assert_eq!(got.is_ok(), c["valid"].as_bool().unwrap(), "{}", c["name"]);
        if let Ok(hash) = got {
            assert_eq!(hash.expose(), c["sha256"].as_str().unwrap());
            assert!(records::proof_matches(&hash, &hash));
            assert!(!records::proof_matches(&hash, &Secret::new("b".repeat(64))));
        }
    }
    let cases: Vec<Value> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-installations/specs.json"
    ))
    .unwrap();
    for c in cases {
        let i = &c["input"];
        let spec = InstallationSpec {
            route: PushRoute {
                provider: i["provider"].as_str().unwrap().into(),
                application: i["application"].as_str().unwrap().into(),
                environment: i["environment"].as_str().unwrap().into(),
            },
            token: Secret::new(i["token"].as_str().unwrap().into()),
        };
        assert_eq!(
            spec.validate().is_ok(),
            c["valid"].as_bool().unwrap(),
            "{}",
            c["name"]
        );
        if c["valid"] == true {
            assert_eq!(records::target(&spec), c["target_sha256"].as_str().unwrap());
        }
    }
    let canceled = events::dispatcher::Control::default();
    canceled.cancel();
    assert!(
        Proofs
            .digest(&canceled, &Secret::new("i1.".to_owned() + &"A".repeat(43)))
            .is_err()
    );
}
#[test]
fn shared_versioned_retirement_and_fingerprint() {
    let cases: Vec<Value> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-installations/events.json"
    ))
    .unwrap();
    for c in cases {
        let got = postgres_storage::outbox::decode(c["envelope"].clone()).and_then(|mut m| {
            if let Some(raw) = c["data_wire"].as_str() {
                m.data = raw.as_bytes().to_vec();
            }
            notifications::infra::events::identity::admit(&m)
        });
        assert_eq!(got.is_ok(), c["valid"].as_bool().unwrap(), "{}", c["name"]);
        if let Ok(r) = got {
            assert_eq!(
                records::revocation_fingerprint(&r).unwrap(),
                c["sha256"].as_str().unwrap()
            );
        }
    }
}
