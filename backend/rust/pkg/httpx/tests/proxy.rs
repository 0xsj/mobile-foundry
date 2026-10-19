use axum::{body::Body, extract::Request};
use httpx::{PeerAddress, proxy};
use runtime_config::TrustedProxies;
#[test]
fn shared_trusted_proxy_corpus() {
    let cases: Vec<serde_json::Value> = serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-operations/proxy.json"
    ))
    .unwrap();
    for case in cases {
        let mut request = Request::new(Body::empty());
        request
            .extensions_mut()
            .insert(PeerAddress(case["peer"].as_str().unwrap().parse().unwrap()));
        for value in case["headers"].as_array().unwrap() {
            request
                .headers_mut()
                .append("x-forwarded-for", value.as_str().unwrap().parse().unwrap());
        }
        let result = proxy::admit(
            &mut request,
            &TrustedProxies::parse(case["cidrs"].as_str().unwrap()).unwrap(),
        );
        assert_eq!(
            if result.is_ok() { 200 } else { 400 },
            case["status"],
            "{}",
            case["name"]
        );
        if result.is_ok() {
            assert_eq!(
                request
                    .extensions()
                    .get::<PeerAddress>()
                    .unwrap()
                    .0
                    .to_string(),
                case["ip"],
                "{}",
                case["name"]
            );
        }
    }
}
