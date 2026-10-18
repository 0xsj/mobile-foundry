use identity::{
    app::ports::{Metadata, Passwords, Purpose, Scope, Tokens},
    domain::{Password, Secret},
    infra::security::{PasswordAdapter, TokenAdapter},
};
use kernel::{Failure, Kind};
use serde_json::Value;
struct Active;
impl Scope for Active {
    fn check(&self) -> Result<(), Failure> {
        Ok(())
    }
    fn metadata(&self) -> Metadata {
        Default::default()
    }
}
fn vectors() -> Value {
    serde_json::from_str(include_str!(
        "../../../../../contracts/fixtures/backend-identity/security.json"
    ))
    .unwrap()
}
fn password(value: &str) -> Password {
    Password::new(Secret::new(value.into())).unwrap()
}

#[tokio::test]
async fn shared_phc_and_opaque_token_vectors() {
    let v = vectors();
    let passwords = PasswordAdapter::new().await.unwrap();
    let hash = || Secret::new(v["hash"].as_str().unwrap().into());
    assert!(
        passwords
            .verify(&Active, hash(), password(v["password"].as_str().unwrap()))
            .await
            .unwrap()
    );
    assert!(
        !passwords
            .verify(&Active, hash(), password("another-password-123"))
            .await
            .unwrap()
    );
    for value in [
        "malformed".to_owned(),
        v["hash"].as_str().unwrap().replace("m=19456", "m=8"),
        v["hash"].as_str().unwrap().to_owned() + "=",
    ] {
        assert_eq!(
            passwords
                .verify(
                    &Active,
                    Secret::new(value),
                    password("fixture-password-123")
                )
                .await
                .unwrap_err()
                .kind(),
            Kind::Internal
        );
    }
    let round_trip = passwords
        .hash(&Active, password("fixture-password-123"))
        .await
        .unwrap();
    assert!(
        passwords
            .verify(&Active, round_trip, password("fixture-password-123"))
            .await
            .unwrap()
    );
    passwords.close().await.unwrap();
    assert_eq!(
        passwords
            .hash(&Active, password("fixture-password-123"))
            .await
            .unwrap_err()
            .kind(),
        Kind::RateLimited
    );
    for (purpose, key, digest) in [
        (Purpose::Access, "access_token", "access_digest"),
        (Purpose::Refresh, "refresh_token", "refresh_digest"),
    ] {
        let raw = Secret::new(v[key].as_str().unwrap().into());
        assert_eq!(
            TokenAdapter.digest(&Active, &raw, purpose).unwrap().value(),
            v[digest]
        );
        for invalid in [
            raw.expose().to_owned() + "=",
            raw.expose()[..45].to_owned() + "B",
        ] {
            assert_eq!(
                TokenAdapter
                    .digest(&Active, &Secret::new(invalid), purpose)
                    .unwrap_err()
                    .kind(),
                Kind::Unauthenticated
            );
        }
    }
    let refresh = Secret::new(v["refresh_token"].as_str().unwrap().into());
    assert!(
        TokenAdapter
            .digest(&Active, &refresh, Purpose::Access)
            .is_err()
    );
}
