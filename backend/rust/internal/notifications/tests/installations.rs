#[path = "support/installations.rs"]
mod support;
#[tokio::test]
async fn memory_registry_conformance() {
    support::conformance(std::sync::Arc::new(
        notifications::infra::memory::MemoryStore::default(),
    ))
    .await;
}
#[test]
fn restoration_bounds_and_private_formatting() {
    use notifications::domain::{
        MAX_SEQUENCE, MAX_TIME,
        installation::{Installation, Secret},
    };
    let a = support::actor();
    let m = support::mutation(&a);
    let original = m.create(support::AT).unwrap();
    for printed in [
        format!("{m:?}"),
        format!("{original:?}"),
        format!("{:?}", original.snapshot()),
        format!("{:?}", m.spec),
        format!("{}", m.proof_digest),
    ] {
        for private in [
            m.actor.principal.as_str(),
            m.actor.session.as_str(),
            m.spec.token.expose(),
            m.proof_digest.expose(),
        ] {
            assert!(!printed.contains(private));
        }
    }
    let base = original.snapshot().clone();
    for field in [
        "digest",
        "zero",
        "overflow",
        "clock",
        "expiry",
        "disabled-token",
        "time",
    ] {
        let mut s = base.clone();
        match field {
            "digest" => s.proof_digest = Secret::new("A".repeat(64)),
            "zero" => s.version = 0,
            "overflow" => s.version = MAX_SEQUENCE + 1,
            "clock" => s.updated_at -= 1,
            "expiry" => s.expires_at = s.created_at,
            "disabled-token" => s.enabled = false,
            "time" => s.updated_at = MAX_TIME + 1,
            _ => unreachable!(),
        }
        assert!(Installation::restore(s).is_err(), "{field}");
    }
    let retired = original.disable(support::AT - 1).unwrap();
    assert_eq!(retired.snapshot().updated_at, support::AT);
    assert_eq!(retired.disable(support::AT + 5).unwrap(), retired);
    let mut s = base;
    s.version = MAX_SEQUENCE;
    let full = Installation::restore(s).unwrap();
    assert!(full.disable(support::AT + 1).is_err());
    assert!(
        original
            .bind(
                a.principal,
                a.session,
                support::id(),
                m.spec,
                support::AT - 1,
                a.expires_at
            )
            .is_err()
    );
}
