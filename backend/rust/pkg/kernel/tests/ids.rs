use kernel::id::Id;
use serde::Deserialize;
#[derive(Deserialize)]
struct Case {
    value: String,
    valid: bool,
}

#[test]
fn shared_identifier_corpus() {
    let cases: Vec<Case> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-runtime/ids.json"
    ))
    .unwrap();
    for case in cases {
        let result = Id::parse(&case.value);
        assert_eq!(result.is_ok(), case.valid, "{:?}", case.value);
        if let Ok(id) = result {
            assert_eq!(id.as_str(), case.value);
        } else {
            let public = result.unwrap_err().public();
            assert_eq!(public.code.as_deref(), Some("id.invalid"));
            assert_eq!(public.message, "Provide a valid identifier.");
        }
    }
}
