use account::domain::{self, Account, Action};
use kernel::id::Id;
use serde::Deserialize;
use std::time::{Duration, UNIX_EPOCH};
fn id(value: u8) -> Id {
    Id::parse(&format!("00000000-0000-4000-8000-{value:012}")).unwrap()
}
#[test]
fn shared_names_and_restoration_refuse_noncanonical_state() {
    #[derive(Deserialize)]
    struct Case {
        input: String,
        valid: bool,
        canonical: Option<String>,
    }
    let cases: Vec<Case> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/account/names.json"
    ))
    .unwrap();
    for case in cases {
        let result = domain::name(&case.input);
        assert_eq!(result.is_ok(), case.valid);
        if let Some(expected) = case.canonical {
            assert_eq!(result.unwrap(), expected);
        }
    }
    let original = Account::new(
        id(1),
        id(2),
        " Personal ",
        UNIX_EPOCH + Duration::from_nanos(1_000_999),
    )
    .unwrap();
    assert_eq!(original.snapshot().created_at, 1000);
    let next = original
        .rename(" Team ", UNIX_EPOCH + Duration::from_micros(1001))
        .unwrap();
    assert_eq!(next.snapshot().version, 2);
    assert!(next.fact(Action::Created, &id(2)).is_err());
    assert!(next.fact(Action::Renamed, &id(3)).is_err());
    assert!(next.rename("Team", UNIX_EPOCH).is_err());
    assert!(Account::new(id(1), id(2), "Name", UNIX_EPOCH - Duration::from_secs(1)).is_err());
    for mutate in 0..5 {
        let mut snapshot = next.snapshot().clone();
        match mutate {
            0 => snapshot.name = " Team ".into(),
            1 => snapshot.version = 0,
            2 => snapshot.version = domain::MAX_VERSION + 1,
            3 => snapshot.updated_at = snapshot.created_at - 1,
            _ => snapshot.updated_at = domain::MAX_TIME + 1,
        }
        assert!(Account::restore(snapshot).is_err());
    }
    let mut snapshot = next.snapshot().clone();
    snapshot.version = domain::MAX_VERSION;
    assert!(
        Account::restore(snapshot)
            .unwrap()
            .rename("Name", UNIX_EPOCH + Duration::from_secs(1))
            .is_err()
    );
}
