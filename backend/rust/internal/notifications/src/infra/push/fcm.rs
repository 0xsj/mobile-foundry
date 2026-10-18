use super::*;
use crate::domain::installation::Secret;
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use ring::{
    rand::SystemRandom,
    signature::{RSA_PKCS1_SHA256, RsaKeyPair},
};
use zeroize::Zeroize;
pub(super) const OAUTH: &str = "https://oauth2.googleapis.com/token";
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Account {
    #[serde(rename = "type")]
    kind: String,
    project_id: String,
    private_key_id: String,
    private_key: String,
    client_email: String,
    #[serde(default, rename = "client_id")]
    _client_id: String,
    #[serde(default, rename = "auth_uri")]
    _auth_uri: String,
    token_uri: String,
    #[serde(default, rename = "auth_provider_x509_cert_url")]
    _auth_provider_x509_cert_url: String,
    #[serde(default, rename = "client_x509_cert_url")]
    _client_x509_cert_url: String,
    #[serde(default)]
    universe_domain: String,
}
impl Drop for Account {
    fn drop(&mut self) {
        self.private_key.zeroize();
    }
}
pub(super) struct Cache {
    token: Secret,
    issued: u64,
    expires: u64,
}
pub(super) struct Sender {
    route: PushRoute,
    project: String,
    email: String,
    key_id: String,
    key: RsaKeyPair,
    clock: Arc<dyn Clock>,
    transport: Arc<Transport>,
    pub(super) endpoint: String,
    pub(super) token_url: String,
    pub(super) cache: tokio::sync::Mutex<Option<Cache>>,
}
impl Sender {
    pub(super) fn new(
        route: PushRoute,
        project: String,
        wire: &[u8],
        clock: Arc<dyn Clock>,
        transport: Arc<Transport>,
    ) -> Result<Self, Failure> {
        let c: Account = decode(wire)?;
        let p = project.as_bytes();
        let valid_project = (6..=30).contains(&p.len())
            && p[0].is_ascii_lowercase()
            && p[p.len() - 1].is_ascii_alphanumeric()
            && p.iter()
                .all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || *b == b'-');
        let parts: Vec<_> = c.client_email.split('@').collect();
        let valid_email = parts.len() == 2
            && parts.iter().all(|p| !p.is_empty())
            && c.client_email.is_ascii()
            && c.client_email
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b"._+-@".contains(&b));
        if c.kind != "service_account"
            || c.project_id != project
            || c.token_uri != OAUTH
            || (!c.universe_domain.is_empty() && c.universe_domain != "googleapis.com")
            || !valid_project
            || !valid_email
            || c.private_key_id.is_empty()
        {
            return Err(configuration());
        }
        let key = RsaKeyPair::from_pkcs8(&pkcs8(c.private_key.as_bytes())?)
            .map_err(|_| configuration())?;
        let endpoint = format!("https://fcm.googleapis.com/v1/projects/{project}/messages:send");
        Ok(Self {
            route,
            project,
            email: c.client_email.clone(),
            key_id: c.private_key_id.clone(),
            key,
            clock,
            transport,
            endpoint,
            token_url: OAUTH.into(),
            cache: tokio::sync::Mutex::new(None),
        })
    }
    pub(super) async fn authorization(&self, s: &dyn Scope) -> Result<Secret, Failure> {
        let mut cache = wait(s, self.cache.lock()).await?;
        let now = crate::app::now(self.clock.as_ref())?;
        if let Some(c) = cache.as_ref() {
            if now >= c.issued && now < c.expires.saturating_sub(60_000_000) {
                return Ok(c.token.clone());
            }
        }
        let h =
            serde_json::to_vec(&serde_json::json!({"alg":"RS256","typ":"JWT","kid":self.key_id}))
                .map_err(|_| credentials())?;
        let c=serde_json::to_vec(&serde_json::json!({"iss":self.email,"scope":"https://www.googleapis.com/auth/firebase.messaging","aud":OAUTH,"iat":now/1_000_000,"exp":now/1_000_000+3600})).map_err(|_|credentials())?;
        let input = Zeroizing::new(format!(
            "{}.{}",
            URL_SAFE_NO_PAD.encode(h),
            URL_SAFE_NO_PAD.encode(c)
        ));
        let mut signature = Zeroizing::new(vec![0; self.key.public().modulus_len()]);
        self.key
            .sign(
                &RSA_PKCS1_SHA256,
                &SystemRandom::new(),
                input.as_bytes(),
                &mut signature,
            )
            .map_err(|_| credentials())?;
        let assertion = Secret::new(format!(
            "{}.{}",
            *input,
            URL_SAFE_NO_PAD.encode(&*signature)
        ));
        // Raw base64url and '.' need no form escaping; encode the grant type explicitly.
        let form = format!(
            "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion={}",
            assertion.expose()
        );
        let (status, wire) = self
            .transport
            .response(
                s,
                false,
                &self.token_url,
                headers("", "application/x-www-form-urlencoded")?,
                form.into_bytes(),
            )
            .await?;
        if status != 200 {
            return Err(provider_error(status));
        }
        #[derive(Deserialize)]
        struct Token {
            access_token: String,
            token_type: String,
            expires_in: i64,
        }
        let mut token: Token = serde_json::from_slice(&wire).map_err(|_| unavailable())?;
        if !token.token_type.eq_ignore_ascii_case("bearer")
            || !(1..=3600).contains(&token.expires_in)
            || !(1..=4096).contains(&token.access_token.len())
            || !token.access_token.bytes().all(|b| (33..=126).contains(&b))
        {
            token.access_token.zeroize();
            return Err(unavailable());
        }
        let token_value = Secret::new(std::mem::take(&mut token.access_token));
        *cache = Some(Cache {
            token: token_value.clone(),
            issued: now,
            expires: now + token.expires_in as u64 * 1_000_000,
        });
        Ok(token_value)
    }
    pub(super) async fn send(
        &self,
        s: &dyn Scope,
        address: PushAddress,
        preview: Id,
    ) -> Result<PushDisposition, Failure> {
        if address.spec.route != self.route || address.spec.validate().is_err() {
            return Err(route_error());
        }
        let auth = self.authorization(s).await?;
        let payload = serde_json::json!({"message":{"token":address.spec.token.expose(),"notification":{"title":TITLE,"body":BODY},"data":data(&preview),"android":{"ttl":"0s","restricted_package_name":self.route.application}}});
        let (status, body) = self
            .transport
            .response(
                s,
                false,
                &self.endpoint,
                headers(&format!("Bearer {}", auth.expose()), "application/json")?,
                serde_json::to_vec(&payload).map_err(|_| unavailable())?,
            )
            .await?;
        admit(status, &body, &self.project)
    }
}
pub(super) fn admit(status: u16, body: &[u8], project: &str) -> Result<PushDisposition, Failure> {
    if status == 200 {
        #[derive(Deserialize)]
        struct Accepted {
            name: String,
        }
        if let Ok(a) = serde_json::from_slice::<Accepted>(body) {
            let prefix = format!("projects/{project}/messages/");
            if a.name.starts_with(&prefix) && a.name.len() > prefix.len() {
                return Ok(PushDisposition::Accepted);
            }
        }
        return Err(unavailable());
    }
    #[derive(Deserialize)]
    struct Error {
        error: Detail,
    }
    #[derive(Deserialize)]
    struct Detail {
        code: Option<i64>,
        status: Option<String>,
        #[serde(default)]
        details: Vec<Evidence>,
    }
    #[derive(Deserialize)]
    struct Evidence {
        #[serde(rename = "@type")]
        kind: Option<String>,
        #[serde(rename = "errorCode")]
        code: Option<String>,
    }
    if status == 404 {
        if let Ok(e) = serde_json::from_slice::<Error>(body) {
            if e.error.code == Some(404)
                && e.error.status.as_deref() == Some("NOT_FOUND")
                && e.error.details.iter().any(|d| {
                    d.kind.as_deref() == Some("type.googleapis.com/google.firebase.fcm.v1.FcmError")
                        && d.code.as_deref() == Some("UNREGISTERED")
                })
            {
                return Ok(PushDisposition::InvalidTarget);
            }
        }
    }
    Err(provider_error(status))
}
