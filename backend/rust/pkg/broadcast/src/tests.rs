use super::*;
#[test]
fn shared_hub_cases() {
    let cases: Vec<serde_json::Value> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-realtime/hub.json"
    ))
    .unwrap();
    for case in cases {
        let hub = Arc::new(Hub::new(
            case["maximum"].as_u64().unwrap() as usize,
            case["per_owner"].as_u64().unwrap() as usize,
        ));
        let mut watches = BTreeMap::new();
        for step in case["steps"].as_array().unwrap() {
            let slot = step["slot"].as_str().unwrap_or("");
            match step["op"].as_str().unwrap() {
                "watch" => {
                    let result = hub.watch(
                        step["key"].as_str().unwrap(),
                        step["owner"].as_str().unwrap(),
                    );
                    if let Some(expected) = step["expected"].as_str() {
                        assert_eq!(result.unwrap_err().public().code.as_deref(), Some(expected));
                    } else {
                        watches.insert(slot.to_owned(), result.unwrap());
                    }
                }
                "publish" => {
                    for _ in 0..step["count"].as_u64().unwrap() {
                        hub.publish(step["key"].as_str().unwrap());
                    }
                }
                "release" => watches.get_mut(slot).unwrap().close(),
                "close" => hub.close(),
                "take" => {
                    let result = match watches.get_mut(slot).unwrap().receiver.try_recv() {
                        Ok(()) => "dirty",
                        Err(mpsc::error::TryRecvError::Empty) => "empty",
                        Err(mpsc::error::TryRecvError::Disconnected) => "closed",
                    };
                    assert_eq!(result, step["expected"], "{}", case["name"]);
                }
                _ => panic!("unknown fixture operation"),
            }
        }
    }
}
#[test]
fn drop_releases_owner_capacity() {
    let hub = Arc::new(Hub::new(1, 1));
    let watch = hub.watch("PRIVATE-key", "PRIVATE-owner").unwrap();
    assert!(!format!("{watch:?} {hub:?}").contains("PRIVATE"));
    drop(watch);
    assert!(hub.watch("other", "PRIVATE-owner").is_ok());
}
