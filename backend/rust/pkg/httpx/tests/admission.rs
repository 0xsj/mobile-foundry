use axum::{
    body::{Body, Bytes, to_bytes},
    http::{HeaderMap, HeaderName, HeaderValue, StatusCode},
};
use httpx::{
    RequestContext,
    admission::{self, AdmissionError},
};
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};

#[derive(Default, Deserialize, Serialize)]
struct Payload {
    #[serde(default)]
    name: Option<String>,
    #[serde(default)]
    detail: Option<Detail>,
}
#[derive(Default, Deserialize, Serialize)]
struct Detail {
    #[serde(default)]
    enabled: Option<bool>,
}
#[derive(Deserialize)]
struct JsonCase {
    name: String,
    body: String,
    headers: Vec<(String, String)>,
    pad_to: Option<usize>,
    expected: Value,
}
#[derive(Deserialize)]
struct HeaderCase {
    name: String,
    operation: String,
    headers: Vec<(String, String)>,
    expected: Value,
}

fn headers(pairs: &[(String, String)]) -> HeaderMap {
    let mut headers = HeaderMap::new();
    for (name, value) in pairs {
        headers.append(
            HeaderName::from_bytes(name.as_bytes()).unwrap(),
            HeaderValue::from_bytes(value.as_bytes()).unwrap(),
        );
    }
    headers
}
fn context() -> RequestContext {
    RequestContext::new("fixture-request".into(), None)
}
async fn refusal(error: AdmissionError) -> Value {
    let response = error.response(&context());
    let status = response.status().as_u16();
    let bytes = to_bytes(response.into_body(), 16384).await.unwrap();
    let value: Value = serde_json::from_slice(&bytes).unwrap();
    assert!(!String::from_utf8_lossy(&bytes).contains("SECRET"));
    assert_eq!(value["kind"], "invalid");
    json!({"status":status,"code":value["code"],"detail":value["detail"]})
}

#[tokio::test]
async fn shared_json_admission_corpus() {
    let cases: Vec<JsonCase> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-runtime/admission.json"
    ))
    .unwrap();
    for case in cases {
        let mut body = case.body;
        if let Some(length) = case.pad_to {
            body.push_str(&" ".repeat(length - body.len()));
        }
        let result = admission::decode_json::<Payload>(
            &context(),
            &headers(&case.headers),
            Body::from(body),
        )
        .await;
        let observed = match result {
            Ok(value) => json!({"status":200,"value":value}),
            Err(error) => refusal(error).await,
        };
        assert_eq!(observed, case.expected, "{}", case.name);
    }
}

#[tokio::test]
async fn shared_version_and_key_corpus() {
    let cases: Vec<HeaderCase> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-runtime/headers.json"
    ))
    .unwrap();
    for case in cases {
        let headers = headers(&case.headers);
        let observed = match case.operation.as_str() {
            "version" => match admission::required_version(&headers) {
                Ok(version) => {
                    assert_eq!(
                        version.etag().to_str().unwrap(),
                        format!("\"{}\"", version.value())
                    );
                    json!({"status":200,"value":version.value()})
                }
                Err(error) => refusal(error).await,
            },
            "key" => match admission::idempotency_key(&headers) {
                Ok(key) => json!({"status":200,"value":key}),
                Err(error) => refusal(error).await,
            },
            _ => panic!("unknown corpus operation"),
        };
        assert_eq!(observed, case.expected, "{}", case.name);
    }
    for value in [0, admission::MAX_VERSION + 1, u64::MAX] {
        assert!(admission::Version::new(value).is_err());
    }
}

#[tokio::test(start_paused = true)]
async fn body_read_deadline_and_io_failure_are_distinct() {
    let headers = headers(&[("content-type".into(), "application/json".into())]);
    let blocked =
        Body::from_stream(futures_util::stream::pending::<Result<Bytes, std::io::Error>>());
    let error = admission::decode_json::<Payload>(&context(), &headers, blocked)
        .await
        .err()
        .unwrap();
    assert_eq!(error.status(), StatusCode::GATEWAY_TIMEOUT);
    assert_eq!(error.failure().kind(), kernel::Kind::Timeout);
    let broken = Body::from_stream(futures_util::stream::once(async {
        Err::<Bytes, _>(std::io::Error::other("SECRET transport"))
    }));
    let error = admission::decode_json::<Payload>(&context(), &headers, broken)
        .await
        .err()
        .unwrap();
    assert!(std::error::Error::source(error.failure()).is_some());
    assert_eq!(refusal(error).await["code"], "http.invalid_json");
}

struct CannotEncode;
impl Serialize for CannotEncode {
    fn serialize<S: serde::Serializer>(&self, _: S) -> Result<S::Ok, S::Error> {
        Err(serde::ser::Error::custom("SECRET encoding detail"))
    }
}
#[tokio::test]
async fn json_encoding_failure_precedes_response_creation() {
    let error = admission::json_response(&context(), StatusCode::OK, &CannotEncode).unwrap_err();
    assert!(std::error::Error::source(&error).is_some());
    assert_eq!(error.public().message, "An unexpected error occurred.");
    assert_eq!(error.public().code, None);
    for code in [100, 204, 205, 304, 600] {
        assert!(
            admission::json_response(&context(), StatusCode::from_u16(code).unwrap(), &json!({}))
                .is_err()
        );
    }
}
