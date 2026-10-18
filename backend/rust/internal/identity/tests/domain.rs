use identity::domain::*;
use kernel::id::Id;
use serde::Deserialize;

#[derive(Deserialize)]
struct Case {
    field: String,
    input: String,
    valid: bool,
    expected: Option<String>,
}
#[test]
fn shared_identity_policy_corpus() {
    let cases: Vec<Case> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-identity/policy.json"
    ))
    .unwrap();
    for case in cases {
        let value = match case.field.as_str() {
            "login" => Login::new(&case.input).map(|v| v.value().to_owned()),
            "display_name" => DisplayName::new(&case.input).map(|v| v.value().to_owned()),
            _ => Password::new(Secret::new(case.input.clone())).map(|v| v.expose().to_owned()),
        };
        assert_eq!(value.is_ok(), case.valid, "{}", case.field);
        if let Some(expected) = case.expected {
            assert_eq!(value.unwrap(), expected);
        }
    }
}
fn id() -> Id {
    Id::parse("11111111-1111-4111-8111-111111111111").unwrap()
}
fn digest(value: char) -> Digest {
    Digest::parse(value.to_string().repeat(64)).unwrap()
}
#[test]
fn session_time_rotation_and_restoration_invariants() {
    let now = Timestamp::from_micros(1_700_000_000_000_000).unwrap();
    let s = Session::new(id(), id(), digest('a'), digest('b'), now).unwrap();
    assert!(!s.active(Timestamp::from_micros(now.micros() - 1).unwrap()));
    assert!(s.active(now));
    assert!(!s.active(s.state().access_expires_at));
    assert!(s.renewable(s.state().access_expires_at));
    assert!(!s.renewable(s.state().expires_at));
    let near_end = Timestamp::from_micros(s.state().expires_at.micros() - 1).unwrap();
    let rotated = s.rotate(digest('c'), digest('d'), near_end).unwrap();
    assert_eq!(rotated.state().expires_at, s.state().expires_at);
    assert_eq!(rotated.state().access_expires_at, s.state().expires_at);
    assert!(rotated.rotate(digest('e'), digest('f'), now).is_err());
    assert!(!rotated.revoke(near_end).unwrap().renewable(near_end));
    let mut corrupt = s.state().clone();
    corrupt.generation = MAX_GENERATION;
    assert!(
        Session::restore(corrupt)
            .unwrap()
            .rotate(digest('c'), digest('d'), now)
            .is_err()
    );
    let mut corrupt = s.state().clone();
    corrupt.access_expires_at = s.state().expires_at;
    assert!(Session::restore(corrupt).is_err());
    let p = Principal::new(
        id(),
        Login::new("PRIVATE-login").unwrap(),
        DisplayName::new("PRIVATE name").unwrap(),
        Secret::new("PRIVATE hash".into()),
        now,
    )
    .unwrap();
    assert!(!format!("{p:?}").contains("PRIVATE"));
    assert!(!format!("{:?}", Secret::new("PRIVATE password".into())).contains("PRIVATE"));
}
