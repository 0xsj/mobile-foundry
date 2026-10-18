//! Private, bounded SMTP delivery. TLS validates certificates/hostnames; no send retry.
use crate::{
    app::{
        ports::{PortFuture, Scope},
        recovery::{Mail, Mailer, mail_unavailable},
    },
    domain::{Secret, recovery},
};
use base64::{Engine, engine::general_purpose::STANDARD};
use kernel::Failure;
use std::{sync::Arc, time::Duration};
use tokio::{
    io::{AsyncRead, AsyncReadExt, AsyncWrite, AsyncWriteExt},
    net::TcpStream,
    sync::Semaphore,
};
use tokio_rustls::{
    TlsConnector,
    rustls::{self, pki_types::ServerName},
};
pub struct Disabled;
impl Mailer for Disabled {
    fn send<'a>(&'a self, _: &'a dyn Scope, _: Mail) -> PortFuture<'a, ()> {
        Box::pin(async { Err(mail_unavailable()) })
    }
}
#[derive(Default, serde::Deserialize)]
#[serde(default, deny_unknown_fields)]
struct Wire {
    #[serde(alias = "Address")]
    address: String,
    #[serde(alias = "From")]
    from: String,
    #[serde(alias = "Username")]
    username: String,
    #[serde(alias = "Password")]
    password: String,
    #[serde(alias = "Mode")]
    mode: String,
}
pub struct Sender {
    address: String,
    host: String,
    from: String,
    username: String,
    password: Secret,
    mode: String,
    tls: Arc<rustls::ClientConfig>,
    work: Semaphore,
}
trait Stream: AsyncRead + AsyncWrite + Send + Unpin {}
impl<T: AsyncRead + AsyncWrite + Send + Unpin> Stream for T {}
type Socket = Box<dyn Stream>;
pub fn load(path: Option<&str>, development: bool) -> Result<Arc<dyn Mailer>, Failure> {
    let Some(path) = path else {
        return Ok(Arc::new(Disabled));
    };
    use std::io::Read;
    let mut bytes = zeroize::Zeroizing::new(Vec::new());
    std::fs::File::open(path)
        .map_err(|e| mail_unavailable().with_cause(e))?
        .take(16385)
        .read_to_end(&mut bytes)
        .map_err(|e| mail_unavailable().with_cause(e))?;
    if bytes.len() > 16384 {
        return Err(mail_unavailable());
    }
    let config: Wire = serde_json::from_slice(&bytes).map_err(|_| mail_unavailable())?;
    let mut roots = rustls::RootCertStore::empty();
    roots.add_parsable_certificates(rustls_native_certs::load_native_certs().certs);
    Ok(Arc::new(Sender::new(config, development, roots)?))
}
impl Sender {
    fn new(c: Wire, development: bool, roots: rustls::RootCertStore) -> Result<Self, Failure> {
        let Some((host, port)) = c.address.rsplit_once(':') else {
            return Err(mail_unavailable());
        };
        let host = host
            .trim_start_matches('[')
            .trim_end_matches(']')
            .to_string();
        if host.is_empty()
            || port.parse::<u16>().ok().is_none_or(|p| p == 0)
            || c.address.contains(['\r', '\n', '\0'])
            || recovery::email(&c.from).ok().as_ref() != Some(&c.from)
            || c.username.contains(['\r', '\n'])
            || c.password.contains(['\r', '\n'])
            || c.username.len() > 256
            || c.password.len() > 1024
            || c.username.is_empty() != c.password.is_empty()
        {
            return Err(mail_unavailable());
        }
        if !(["tls", "starttls"].contains(&c.mode.as_str())
            || development && c.mode == "development")
        {
            return Err(mail_unavailable());
        }
        if c.mode != "development" && roots.is_empty() {
            return Err(mail_unavailable());
        }
        let tls = rustls::ClientConfig::builder_with_provider(Arc::new(
            rustls::crypto::ring::default_provider(),
        ))
        .with_protocol_versions(&[&rustls::version::TLS13, &rustls::version::TLS12])
        .map_err(|_| mail_unavailable())?
        .with_root_certificates(roots)
        .with_no_client_auth();
        Ok(Self {
            address: c.address,
            host,
            from: c.from,
            username: c.username,
            password: Secret::new(c.password),
            mode: c.mode,
            tls: Arc::new(tls),
            work: Semaphore::new(4),
        })
    }
    async fn secure(&self, socket: Socket) -> Result<Socket, Failure> {
        let name = ServerName::try_from(self.host.clone()).map_err(|_| mail_unavailable())?;
        let stream = TlsConnector::from(self.tls.clone())
            .connect(name, socket)
            .await
            .map_err(|e| mail_unavailable().with_cause(e))?;
        Ok(Box::new(stream))
    }
    async fn deliver(&self, mail: Mail) -> Result<(), Failure> {
        let mut socket: Socket = Box::new(
            TcpStream::connect(&self.address)
                .await
                .map_err(|e| mail_unavailable().with_cause(e))?,
        );
        if self.mode == "tls" {
            socket = self.secure(socket).await?;
        }
        expect(&mut socket, 220).await?;
        command(&mut socket, "EHLO mobile-foundry.local", 250).await?;
        if self.mode == "starttls" {
            command(&mut socket, "STARTTLS", 220).await?;
            socket = self.secure(socket).await?;
            command(&mut socket, "EHLO mobile-foundry.local", 250).await?;
        }
        if !self.username.is_empty() {
            if self.mode == "development"
                && !matches!(self.host.as_str(), "localhost" | "127.0.0.1" | "::1")
            {
                return Err(mail_unavailable());
            }
            let auth =
                zeroize::Zeroizing::new(format!("\0{}\0{}", self.username, self.password.expose()));
            let command_text =
                zeroize::Zeroizing::new(format!("AUTH PLAIN {}", STANDARD.encode(auth.as_bytes())));
            command(&mut socket, &command_text, 235).await?;
        }
        command(&mut socket, &format!("MAIL FROM:<{}>", self.from), 250).await?;
        command(&mut socket, &format!("RCPT TO:<{}>", mail.to), 25).await?;
        command(&mut socket, "DATA", 354).await?;
        let data = zeroize::Zeroizing::new(format!(
            "From: {}\r\nTo: {}\r\nSubject: Account security code\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=utf-8\r\n\r\nPurpose: {}\r\nCode: {}\r\nExpires in 20 minutes. If you did not request this, ignore this message.\r\n.\r\n",
            self.from,
            mail.to,
            mail.purpose.as_str(),
            mail.token.expose()
        ));
        socket
            .write_all(data.as_bytes())
            .await
            .map_err(|e| mail_unavailable().with_cause(e))?;
        socket
            .flush()
            .await
            .map_err(|e| mail_unavailable().with_cause(e))?;
        expect(&mut socket, 250).await?;
        // DATA acknowledgment is acceptance. Close without risking a retry on QUIT loss.
        Ok(())
    }
}
impl Mailer for Sender {
    fn send<'a>(&'a self, s: &'a dyn Scope, mail: Mail) -> PortFuture<'a, ()> {
        Box::pin(async move {
            s.check()?;
            let _permit = self.work.try_acquire().map_err(|_| mail_unavailable())?;
            let prefix = if mail.purpose == recovery::Purpose::VerifyEmail {
                "e1."
            } else {
                "p1."
            };
            let token = mail.token.expose();
            if recovery::email(&mail.to).ok().as_ref() != Some(&mail.to)
                || token.len() != 46
                || !token.starts_with(prefix)
                || !token[3..]
                    .bytes()
                    .all(|b| b.is_ascii_alphanumeric() || b"_-".contains(&b))
            {
                return Err(mail_unavailable());
            }
            tokio::time::timeout(Duration::from_secs(5), self.deliver(mail))
                .await
                .map_err(|_| mail_unavailable())?
        })
    }
}
async fn command(socket: &mut Socket, text: &str, code: u16) -> Result<(), Failure> {
    socket
        .write_all(text.as_bytes())
        .await
        .map_err(|e| mail_unavailable().with_cause(e))?;
    socket
        .write_all(b"\r\n")
        .await
        .map_err(|e| mail_unavailable().with_cause(e))?;
    socket
        .flush()
        .await
        .map_err(|e| mail_unavailable().with_cause(e))?;
    expect(socket, code).await
}
async fn expect(socket: &mut Socket, wanted: u16) -> Result<(), Failure> {
    for _ in 0..32 {
        let mut line = Vec::new();
        loop {
            let b = socket
                .read_u8()
                .await
                .map_err(|e| mail_unavailable().with_cause(e))?;
            line.push(b);
            if line.len() > 1024 {
                return Err(mail_unavailable());
            }
            if b == b'\n' {
                break;
            }
        }
        if line.len() < 5 || !line.ends_with(b"\r\n") || !line[..3].iter().all(u8::is_ascii_digit) {
            return Err(mail_unavailable());
        }
        let code = ((line[0] - b'0') as u16) * 100
            + ((line[1] - b'0') as u16) * 10
            + (line[2] - b'0') as u16;
        if !(code == wanted || wanted < 100 && code / 10 == wanted) {
            return Err(mail_unavailable());
        }
        match line[3] {
            b' ' => return Ok(()),
            b'-' => {}
            _ => return Err(mail_unavailable()),
        }
    }
    Err(mail_unavailable())
}

#[cfg(test)]
mod tests;
