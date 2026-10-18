//! Explicit provider routes. No recipient selection, retries or provider diagnostics.
mod apns;
mod fcm;
#[cfg(test)]
mod tests;
use crate::{
    app::{
        ports::{PortFuture, Scope},
        push::{PushAddress, PushDisposition, PushGateway},
    },
    domain::installation::PushRoute,
};
use kernel::{Failure, Kind, clock::Clock, id::Id};
use reqwest::{
    Client,
    header::{AUTHORIZATION, HeaderMap, HeaderValue},
};
use serde::{Deserialize, de::DeserializeOwned};
use std::{
    collections::BTreeMap,
    fmt,
    io::Read,
    path::{Path, PathBuf},
    sync::{Arc, Mutex},
    time::Duration,
};
use zeroize::Zeroizing;
pub fn configuration() -> Failure {
    Failure::new(
        Kind::Invalid,
        "push.configuration",
        "invalid push provider configuration",
    )
    .with_public("Check the private push provider configuration.")
}
pub fn route_error() -> Failure {
    Failure::new(
        Kind::Invalid,
        "push.route_not_selected",
        "push route or provider token encoding not selected",
    )
    .with_public("This push route or token encoding is not supported by the configured sender.")
}
pub fn unavailable() -> Failure {
    Failure::new(Kind::Unavailable,"push.provider_unavailable","push provider request not confirmed").with_public("The provider did not confirm this request. Refresh registration before another attempt; sending again may duplicate a notification.")
}
pub fn credentials() -> Failure {
    Failure::new(
        Kind::Unavailable,
        "push.provider_credentials",
        "push provider refused credentials or routing configuration",
    )
    .with_public("Check the server's push provider credentials and route.")
}
pub fn provider_error(status: u16) -> Failure {
    match status {
        401 | 403 => credentials(),
        429 => Failure::new(
            Kind::RateLimited,
            "push.provider_limited",
            "push provider throttled the request",
        )
        .with_public("The provider limited this request. No automatic retry was made."),
        _ => unavailable(),
    }
}
#[derive(Default, Deserialize)]
#[serde(default, deny_unknown_fields)]
struct RouteConfig {
    provider: String,
    application: String,
    environment: String,
    team_id: String,
    key_id: String,
    key_file: String,
    project_id: String,
    service_account_file: String,
}
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Configuration {
    routes: Vec<RouteConfig>,
}
enum Sender {
    Apns(apns::Sender),
    Fcm(fcm::Sender),
}
pub struct Gateway {
    routes: BTreeMap<(String, String, String), Sender>,
    transport: Arc<Transport>,
}
impl fmt::Debug for Gateway {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("PushGateway { redacted }")
    }
}
struct Clients {
    apns: Client,
    fcm: Client,
}
struct Transport {
    clients: Mutex<Option<Clients>>,
}
impl Transport {
    fn new() -> Result<Self, Failure> {
        Ok(Self {
            clients: Mutex::new(Some(Clients {
                apns: client_builder(true).build().map_err(|_| configuration())?,
                fcm: client_builder(false).build().map_err(|_| configuration())?,
            })),
        })
    }
    fn client(&self, apns: bool) -> Result<Client, Failure> {
        let clients = self.clients.lock().map_err(|_| unavailable())?;
        let clients = clients.as_ref().ok_or_else(unavailable)?;
        Ok(if apns {
            clients.apns.clone()
        } else {
            clients.fcm.clone()
        })
    }
    fn close(&self) {
        if let Ok(mut clients) = self.clients.lock() {
            clients.take();
        }
    }
    async fn response(
        &self,
        s: &dyn Scope,
        apns: bool,
        url: &str,
        headers: HeaderMap,
        body: Vec<u8>,
    ) -> Result<(u16, Zeroizing<Vec<u8>>), Failure> {
        s.check()?;
        let client = self.client(apns)?;
        let result = async {
            let mut response = client
                .post(url)
                .headers(headers)
                .body(body)
                .send()
                .await
                .map_err(|_| unavailable())?;
            if apns && response.version() != reqwest::Version::HTTP_2 {
                return Err(unavailable());
            }
            let status = response.status().as_u16();
            let mut wire = Zeroizing::new(Vec::new());
            while let Some(chunk) = response.chunk().await.map_err(|_| unavailable())? {
                s.check()?;
                if wire.len() + chunk.len() > 16384 {
                    return Err(unavailable());
                }
                wire.extend_from_slice(&chunk);
            }
            s.check()?;
            Ok((status, wire))
        };
        let result = wait(s, result).await?;
        // Do not retain reqwest errors: they can contain credential-bearing URLs.
        s.check()?;
        result
    }
}
fn client_builder(apns: bool) -> reqwest::ClientBuilder {
    let builder = Client::builder()
        .https_only(true)
        .no_proxy()
        .redirect(reqwest::redirect::Policy::none())
        .retry(reqwest::retry::never())
        .min_tls_version(reqwest::tls::Version::TLS_1_2)
        .connect_timeout(Duration::from_secs(5))
        .timeout(Duration::from_secs(5))
        .pool_max_idle_per_host(4)
        .pool_idle_timeout(Duration::from_secs(60));
    if apns {
        builder.http2_prior_knowledge()
    } else {
        builder
    }
}
pub(super) fn headers(auth: &str, content_type: &'static str) -> Result<HeaderMap, Failure> {
    let mut h = HeaderMap::new();
    if !auth.is_empty() {
        let mut value = HeaderValue::from_str(auth).map_err(|_| unavailable())?;
        value.set_sensitive(true);
        h.insert(AUTHORIZATION, value);
    }
    h.insert("content-type", HeaderValue::from_static(content_type));
    Ok(h)
}
/// Scope is a synchronous port; polling keeps a cache waiter or stalled I/O cancellable
/// without starting a task. Dropping this future drops the operation as well.
async fn wait<T>(
    s: &dyn Scope,
    future: impl std::future::Future<Output = T>,
) -> Result<T, Failure> {
    s.check()?;
    tokio::pin!(future);
    loop {
        tokio::select! {result=&mut future=>{s.check()?;return Ok(result)},_ = tokio::time::sleep(Duration::from_millis(20))=>s.check()?,}
    }
}
fn key(route: &PushRoute) -> (String, String, String) {
    (
        route.provider.clone(),
        route.application.clone(),
        route.environment.clone(),
    )
}
impl Gateway {
    /// Bounded local files only. Loading never contacts Apple or Google.
    pub fn load(path: &Path, clock: Arc<dyn Clock>) -> Result<Self, Failure> {
        let config: Configuration = decode(&read_file(path, 65536)?)?;
        if !(1..=16).contains(&config.routes.len()) {
            return Err(configuration());
        }
        let transport = Arc::new(Transport::new()?);
        let mut routes = BTreeMap::new();
        for r in config.routes {
            let route = PushRoute {
                provider: r.provider,
                application: r.application,
                environment: r.environment,
            };
            if route.validate().is_err() || routes.contains_key(&key(&route)) {
                return Err(configuration());
            }
            let resolve = |value: &str| -> PathBuf {
                let p = Path::new(value);
                if p.is_absolute() {
                    p.into()
                } else {
                    path.parent().unwrap_or(Path::new(".")).join(p)
                }
            };
            let sender = match route.provider.as_str() {
                "apns" => {
                    if !r.project_id.is_empty()
                        || !r.service_account_file.is_empty()
                        || r.key_file.is_empty()
                    {
                        return Err(configuration());
                    }
                    let pem = read_file(&resolve(&r.key_file), 16384)?;
                    Sender::Apns(apns::Sender::new(
                        route.clone(),
                        r.team_id,
                        r.key_id,
                        &pem,
                        clock.clone(),
                        transport.clone(),
                    )?)
                }
                "fcm" => {
                    if !r.team_id.is_empty()
                        || !r.key_id.is_empty()
                        || !r.key_file.is_empty()
                        || r.service_account_file.is_empty()
                    {
                        return Err(configuration());
                    }
                    let wire = read_file(&resolve(&r.service_account_file), 32768)?;
                    Sender::Fcm(fcm::Sender::new(
                        route.clone(),
                        r.project_id,
                        &wire,
                        clock.clone(),
                        transport.clone(),
                    )?)
                }
                _ => return Err(configuration()),
            };
            routes.insert(key(&route), sender);
        }
        Ok(Self { routes, transport })
    }
    /// Called after request/worker drain; dropping the only idle pool owners closes connections.
    pub fn close(&self) {
        self.transport.close();
    }
}
impl PushGateway for Gateway {
    fn send_preview<'a>(
        &'a self,
        s: &'a dyn Scope,
        address: PushAddress,
        preview: Id,
    ) -> PortFuture<'a, PushDisposition> {
        Box::pin(async move {
            s.check()?;
            // Refuse use after closure even when a caller retains a gateway Arc.
            self.transport.client(false)?;
            address.lease.validate().map_err(|_| route_error())?;
            address.spec.validate().map_err(|_| route_error())?;
            if address.registered_at > crate::domain::MAX_TIME {
                return Err(route_error());
            }
            match self
                .routes
                .get(&key(&address.spec.route))
                .ok_or_else(route_error)?
            {
                Sender::Apns(sender) => sender.send(s, address, preview).await,
                Sender::Fcm(sender) => sender.send(s, address, preview).await,
            }
        })
    }
}
fn read_file(path: &Path, limit: u64) -> Result<Zeroizing<Vec<u8>>, Failure> {
    let file = std::fs::File::open(path).map_err(|_| configuration())?;
    if !file.metadata().map_err(|_| configuration())?.is_file() {
        return Err(configuration());
    }
    let mut wire = Zeroizing::new(Vec::new());
    file.take(limit + 1)
        .read_to_end(&mut wire)
        .map_err(|_| configuration())?;
    if wire.len() as u64 > limit {
        return Err(configuration());
    }
    Ok(wire)
}
fn decode<T: DeserializeOwned>(wire: &[u8]) -> Result<T, Failure> {
    serde_json::from_slice(wire).map_err(|_| configuration())
}
fn pkcs8(pem: &[u8]) -> Result<Zeroizing<Vec<u8>>, Failure> {
    use base64::{Engine, engine::general_purpose::STANDARD};
    let pem = std::str::from_utf8(pem)
        .map_err(|_| configuration())?
        .trim();
    let body = pem
        .strip_prefix("-----BEGIN PRIVATE KEY-----")
        .and_then(|v| v.strip_suffix("-----END PRIVATE KEY-----"))
        .ok_or_else(configuration)?;
    let encoded = Zeroizing::new(
        body.chars()
            .filter(|c| !c.is_ascii_whitespace())
            .collect::<String>(),
    );
    Ok(Zeroizing::new(
        STANDARD
            .decode(encoded.as_bytes())
            .map_err(|_| configuration())?,
    ))
}
fn data(id: &Id) -> serde_json::Value {
    serde_json::json!({"version":"1","kind":"push_preview","preview_id":id.as_str()})
}
const TITLE: &str = "Mobile push preview";
const BODY: &str = "Open the app to continue.";
