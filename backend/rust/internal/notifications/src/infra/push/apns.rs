use super::*;
use crate::domain::installation::Secret;
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use ring::{
    rand::SystemRandom,
    signature::{ECDSA_P256_SHA256_FIXED_SIGNING, EcdsaKeyPair},
};
pub(super) struct Sender {
    route: PushRoute,
    team: String,
    key_id: String,
    pub(super) key: EcdsaKeyPair,
    clock: Arc<dyn Clock>,
    transport: Arc<Transport>,
    pub(super) endpoint: String,
    cache: Mutex<Option<(u64, Secret)>>,
}
impl Sender {
    pub(super) fn new(
        route: PushRoute,
        team: String,
        key_id: String,
        pem: &[u8],
        clock: Arc<dyn Clock>,
        transport: Arc<Transport>,
    ) -> Result<Self, Failure> {
        let valid = |s: &str| {
            s.len() == 10
                && s.bytes()
                    .all(|b| b.is_ascii_uppercase() || b.is_ascii_digit())
        };
        if !valid(&team) || !valid(&key_id) {
            return Err(configuration());
        }
        let key = EcdsaKeyPair::from_pkcs8(
            &ECDSA_P256_SHA256_FIXED_SIGNING,
            &pkcs8(pem)?,
            &SystemRandom::new(),
        )
        .map_err(|_| configuration())?;
        let endpoint = if route.environment == "sandbox" {
            "https://api.sandbox.push.apple.com"
        } else {
            "https://api.push.apple.com"
        }
        .into();
        Ok(Self {
            route,
            team,
            key_id,
            key,
            clock,
            transport,
            endpoint,
            cache: Mutex::new(None),
        })
    }
    pub(super) fn authorization(&self) -> Result<Secret, Failure> {
        let now = crate::app::now(self.clock.as_ref())?;
        let mut cache = self.cache.lock().map_err(|_| credentials())?;
        if let Some((issued, token)) = cache.as_ref() {
            if now >= *issued && now - *issued < 50 * 60 * 1_000_000 {
                return Ok(token.clone());
            }
        }
        let h = serde_json::to_vec(&serde_json::json!({"alg":"ES256","kid":self.key_id}))
            .map_err(|_| credentials())?;
        let c = serde_json::to_vec(&serde_json::json!({"iss":self.team,"iat":now/1_000_000}))
            .map_err(|_| credentials())?;
        let input = Zeroizing::new(format!(
            "{}.{}",
            URL_SAFE_NO_PAD.encode(h),
            URL_SAFE_NO_PAD.encode(c)
        ));
        let signature = self
            .key
            .sign(&SystemRandom::new(), input.as_bytes())
            .map_err(|_| credentials())?;
        let token = Secret::new(format!(
            "{}.{}",
            *input,
            URL_SAFE_NO_PAD.encode(signature.as_ref())
        ));
        *cache = Some((now, token.clone()));
        Ok(token)
    }
    pub(super) async fn send(
        &self,
        s: &dyn Scope,
        address: PushAddress,
        preview: Id,
    ) -> Result<PushDisposition, Failure> {
        let token = address.spec.token.expose();
        if address.spec.route != self.route
            || token.len() < 2
            || token.len() > 4096
            || token.len() % 2 != 0
            || !token
                .bytes()
                .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
        {
            return Err(route_error());
        }
        let auth = self.authorization()?;
        let mut h = headers(&format!("bearer {}", auth.expose()), "application/json")?;
        for (name, value) in [
            ("apns-topic", self.route.application.as_str()),
            ("apns-push-type", "alert"),
            ("apns-priority", "10"),
            ("apns-expiration", "0"),
            ("apns-id", preview.as_str()),
        ] {
            h.insert(
                reqwest::header::HeaderName::from_static(name),
                HeaderValue::from_str(value).map_err(|_| route_error())?,
            );
        }
        let mut payload = data(&preview);
        payload["aps"] = serde_json::json!({"alert":{"title":TITLE,"body":BODY},"sound":"default"});
        let wire = serde_json::to_vec(&payload).map_err(|_| unavailable())?;
        let (status, body) = self
            .transport
            .response(
                s,
                true,
                &format!("{}/3/device/{}", self.endpoint, token),
                h,
                wire,
            )
            .await?;
        admit(
            status,
            &body,
            address.registered_at,
            crate::app::now(self.clock.as_ref())?,
        )
    }
}
pub(super) fn admit(
    status: u16,
    body: &[u8],
    registered: u64,
    now: u64,
) -> Result<PushDisposition, Failure> {
    if status == 200 && body.is_empty() {
        return Ok(PushDisposition::Accepted);
    }
    #[derive(Deserialize)]
    struct Rejection {
        reason: Option<String>,
        timestamp: Option<i64>,
    }
    if let Ok(r) = serde_json::from_slice::<Rejection>(body) {
        if status == 400 && r.reason.as_deref() == Some("BadDeviceToken") {
            return Ok(PushDisposition::InvalidTarget);
        }
        if status == 410 && r.reason.as_deref() == Some("Unregistered") {
            if let Some(at) = r
                .timestamp
                .and_then(|v| u64::try_from(v).ok())
                .filter(|v| *v > 0)
                .and_then(|v| v.checked_mul(1000))
            {
                if at >= registered && at <= now.saturating_add(300_000_000) {
                    return Ok(PushDisposition::InvalidTarget);
                }
            }
        }
    }
    Err(provider_error(status))
}
