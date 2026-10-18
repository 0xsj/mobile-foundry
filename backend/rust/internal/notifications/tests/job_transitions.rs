use kernel::id::Id;
use notifications::domain::push_job::{Code, Intent, PushJob, Snapshot, Status};
use serde_json::Value;
const AT: u64 = 1_800_000_000_000_000;
fn id(n: u8) -> Id {
    Id::parse(&format!("00000000-0000-4000-8000-{n:012}")).unwrap()
}
fn number(v: &Value, key: &str, default: u64) -> u64 {
    v[key].as_u64().unwrap_or(default)
}
#[test]
fn shared_job_transition_corpus() {
    let cases: Vec<Value> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-push-jobs/transitions.json"
    ))
    .unwrap();
    for case in cases {
        let state = Status::parse(case["state"].as_str().unwrap()).unwrap();
        let active = matches!(state, Status::Leased | Status::Sending);
        let code = match state {
            Status::Blocked => Code::TargetIneligible,
            Status::Failed => Code::AttemptsExhausted,
            Status::Uncertain => Code::ProviderUnconfirmed,
            Status::Expired => Code::Expired,
            Status::Canceled => Code::UserCanceled,
            _ => Code::None,
        };
        let job = PushJob::restore(Snapshot {
            intent: Intent {
                id: id(1),
                installation_id: id(2),
                principal_id: id(3),
                session_id: id(4),
                installation_version: 1,
                retry_of: None,
                duplicate_risk_accepted: false,
            },
            status: state,
            attempts: number(
                &case,
                "initial_attempts",
                match state {
                    Status::Queued | Status::Expired | Status::Canceled => 0,
                    Status::Failed => 5,
                    _ => 1,
                },
            ) as u8,
            claim: active.then(|| id(5)),
            created_at: AT,
            updated_at: AT,
            expires_at: AT + 900_000_000,
            next_at: AT + number(&case, "next_offset", 0),
            lease_until: active.then(|| AT + number(&case, "lease_offset", 30_000_000)),
            code,
            retired: false,
        })
        .unwrap();
        let now = AT + number(&case, "at", 0);
        let token = if case["wrong_token"] == true {
            id(6)
        } else {
            id(5)
        };
        let result = match case["operation"].as_str().unwrap() {
            "claim" => job.claim_next(id(6), now).map(|(next, claimed)| {
                assert_eq!(
                    claimed,
                    case["claimed"].as_bool().unwrap(),
                    "{}",
                    case["name"]
                );
                next
            }),
            "begin" => job.begin(&token, now),
            "finish" => job.finish(
                &token,
                case["started"] == true,
                Status::parse(case["status"].as_str().unwrap()).unwrap(),
                Code::parse(case["result_code"].as_str().unwrap_or("")).unwrap(),
                case["retired"] == true,
                now,
            ),
            "cancel" => job.cancel(now),
            "replace" => {
                let mut intent = job.snapshot().intent.clone();
                intent.id = id(7);
                intent.retry_of = Some(id(1));
                intent.duplicate_risk_accepted = case["ack"] == true;
                job.replacement(&PushJob::new(intent, now).unwrap())
            }
            _ => panic!("unknown action"),
        };
        let expected = case["expected"].as_str().unwrap();
        match result {
            Err(e) => assert_eq!(e.kind().as_str(), expected, "{}", case["name"]),
            Ok(next) => {
                let v = next.snapshot();
                assert_eq!(v.status.as_str(), expected, "{}", case["name"]);
                assert_eq!(
                    u64::from(v.attempts),
                    number(&case, "attempts", 0),
                    "{}",
                    case["name"]
                );
                if let Some(code) = case["code"].as_str() {
                    assert_eq!(v.code.as_str(), code, "{}", case["name"]);
                }
                if case["next_after"].as_u64().is_some_and(|v| v > 0) {
                    assert_eq!(v.next_at, now + case["next_after"].as_u64().unwrap());
                }
                if case["retired"] == true {
                    assert!(v.retired);
                }
            }
        }
    }
}
