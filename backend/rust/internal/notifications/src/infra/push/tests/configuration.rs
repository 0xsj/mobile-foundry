use super::*;
use tls::Material;
#[test]
fn private_configuration_is_bounded_exact_offline_and_closed() {
    let material = Material::new();
    let clock = Arc::new(TestClock(AtomicU64::new(NOW)));
    let config = material.path.join("push.json");
    std::fs::write(
        material.path.join("service-account.json"),
        serde_json::to_vec(&material.service_account()).unwrap(),
    )
    .unwrap();
    let apns = serde_json::json!({"provider":"apns","application":"dev.example.app","environment":"sandbox","team_id":"TEAMID0001","key_id":"KEYID00001","key_file":"apns.p8"});
    let fcm = serde_json::json!({"provider":"fcm","application":"dev.example.app","environment":"production","project_id":"test-project","service_account_file":"service-account.json"});
    let valid = serde_json::json!({"routes":[apns.clone(),fcm.clone()]});
    std::fs::write(&config, serde_json::to_vec(&valid).unwrap()).unwrap();
    let gateway = Gateway::load(&config, clock.clone()).unwrap();
    assert_eq!(gateway.routes.len(), 2);
    assert_eq!(format!("{gateway:?}"), "PushGateway { redacted }");
    assert!(
        matches!(&gateway.routes[&key(&address("apns").spec.route)],Sender::Apns(s) if s.endpoint=="https://api.sandbox.push.apple.com")
    );
    assert!(
        matches!(&gateway.routes[&key(&address("fcm").spec.route)],Sender::Fcm(s) if s.token_url==fcm::OAUTH&&s.endpoint=="https://fcm.googleapis.com/v1/projects/test-project/messages:send")
    );
    gateway.close();
    for value in [
        serde_json::json!({"routes":[]}),
        serde_json::json!({"routes":[apns.clone(),apns.clone()]}),
        serde_json::json!({"routes":[apns.clone()],"endpoint":"https://private.example"}),
        serde_json::json!({"routes":[{"provider":"apns","application":"dev.example.app","environment":"sandbox","key_file":"apns.p8","team_id":"bad","key_id":"KEYID00001"}]}),
    ] {
        std::fs::write(&config, serde_json::to_vec(&value).unwrap()).unwrap();
        assert_eq!(
            Gateway::load(&config, clock.clone())
                .unwrap_err()
                .public()
                .code
                .unwrap(),
            "push.configuration"
        );
    }
    for field in [
        "token_uri",
        "project_id",
        "universe_domain",
        "private_key",
        "client_email",
    ] {
        let mut account = material.service_account();
        account[field] = "private-invalid-value".into();
        std::fs::write(
            material.path.join("service-account.json"),
            serde_json::to_vec(&account).unwrap(),
        )
        .unwrap();
        std::fs::write(
            &config,
            serde_json::to_vec(&serde_json::json!({"routes":[fcm.clone()]})).unwrap(),
        )
        .unwrap();
        let e = Gateway::load(&config, clock.clone()).unwrap_err();
        assert_eq!(e.public().code.unwrap(), "push.configuration");
        assert!(!format!("{e:?} {e}").contains("private-invalid-value"));
    }
    std::fs::write(&config, vec![b' '; 65537]).unwrap();
    assert!(Gateway::load(&config, clock.clone()).is_err());
    assert!(Gateway::load(&material.path, clock.clone()).is_err());
    assert!(
        crate::load_push_gateway(None, clock.clone())
            .unwrap()
            .is_none()
    );
    assert!(crate::load_push_gateway(Some(""), clock).unwrap().is_none());
}
