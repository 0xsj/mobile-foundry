use kernel::id::Id;
use notes::domain::{self, Action, Write};
#[test]
fn shared_title_policy_and_fingerprint_v1() {
    let titles: Vec<serde_json::Value> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/notes/create-titles.json"
    ))
    .unwrap();
    for case in titles {
        assert_eq!(
            domain::valid_title(case["title"].as_str().unwrap()),
            case["error"].is_null(),
            "{}",
            case["id"]
        );
    }
    let cases: Vec<serde_json::Value> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-notes/fingerprints.json"
    ))
    .unwrap();
    for case in cases {
        let w = &case["input"];
        let id = |v: &serde_json::Value| Id::parse(v.as_str().unwrap()).unwrap();
        let input = Write {
            command_id: id(&w["command_id"]),
            action: Action::parse(w["action"].as_str().unwrap()).unwrap(),
            note_id: if w["note_id"] == "" {
                None
            } else {
                Some(id(&w["note_id"]))
            },
            title: w["title"].as_str().unwrap().into(),
            expected: w["expected_version"].as_u64().unwrap(),
        };
        let hash = notes::infra::wire::fingerprint(&input)
            .unwrap()
            .iter()
            .map(|b| format!("{b:02x}"))
            .collect::<String>();
        assert_eq!(hash, case["sha256"]);
    }
}
