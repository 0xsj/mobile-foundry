use super::*;
use events::dispatcher::Control;
use kernel::id::{Generator, RandomIds};
use tokio_rustls::{
    TlsAcceptor,
    rustls::pki_types::{CertificateDer, PrivateKeyDer, PrivatePkcs8KeyDer},
};
struct Owned(std::path::PathBuf);
impl Drop for Owned {
    fn drop(&mut self) {
        let _ = std::fs::remove_dir_all(&self.0);
    }
}
fn certificates() -> (Owned, Vec<u8>, Vec<u8>) {
    let path = std::env::temp_dir().join(format!(
        "mobile-recovery-tls-{}",
        RandomIds.new_id().unwrap()
    ));
    std::fs::create_dir(&path).unwrap();
    let owned = Owned(path.clone());
    for args in [
        vec![
            "req",
            "-x509",
            "-newkey",
            "rsa:2048",
            "-nodes",
            "-keyout",
            "key.pem",
            "-out",
            "cert.pem",
            "-days",
            "1",
            "-subj",
            "/CN=localhost",
            "-addext",
            "subjectAltName=DNS:localhost",
            "-addext",
            "basicConstraints=critical,CA:FALSE",
        ],
        vec![
            "x509", "-in", "cert.pem", "-outform", "DER", "-out", "cert.der",
        ],
        vec![
            "pkcs8", "-topk8", "-nocrypt", "-in", "key.pem", "-outform", "DER", "-out", "key.der",
        ],
    ] {
        let result = std::process::Command::new("openssl")
            .args(args)
            .current_dir(&path)
            .output()
            .expect("local TLS acceptance needs openssl");
        assert!(
            result.status.success(),
            "test certificate generation failed"
        );
    }
    let cert = std::fs::read(path.join("cert.der")).unwrap();
    let key = std::fs::read(path.join("key.der")).unwrap();
    (owned, cert, key)
}
fn server_config(cert: Vec<u8>, key: Vec<u8>) -> Arc<rustls::ServerConfig> {
    Arc::new(
        rustls::ServerConfig::builder_with_provider(Arc::new(
            rustls::crypto::ring::default_provider(),
        ))
        .with_protocol_versions(&[&rustls::version::TLS13, &rustls::version::TLS12])
        .unwrap()
        .with_no_client_auth()
        .with_single_cert(
            vec![CertificateDer::from(cert)],
            PrivateKeyDer::Pkcs8(PrivatePkcs8KeyDer::from(key)),
        )
        .unwrap(),
    )
}
async fn line(socket: &mut Socket) -> String {
    let mut bytes = Vec::new();
    loop {
        let c = socket.read_u8().await.unwrap();
        bytes.push(c);
        assert!(bytes.len() < 4096);
        if c == b'\n' {
            break;
        }
    }
    String::from_utf8(bytes).unwrap()
}
async fn say(socket: &mut Socket, s: &str) {
    socket.write_all(s.as_bytes()).await.unwrap();
    socket.flush().await.unwrap();
}
#[tokio::test]
async fn tls_and_starttls_validate_host_and_accept_only_after_data() {
    let (_owned, cert, key) = certificates();
    let server = server_config(cert.clone(), key);
    for mode in ["tls", "starttls"] {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = format!("localhost:{}", listener.local_addr().unwrap().port());
        let cfg = server.clone();
        let worker = tokio::spawn(async move {
            let mut socket: Socket = Box::new(listener.accept().await.unwrap().0);
            if mode == "tls" {
                socket = Box::new(TlsAcceptor::from(cfg.clone()).accept(socket).await.unwrap());
            }
            say(&mut socket, "220 capture\r\n").await;
            assert!(line(&mut socket).await.starts_with("EHLO "));
            say(&mut socket, "250-capture\r\n250 STARTTLS\r\n").await;
            if mode == "starttls" {
                assert_eq!(line(&mut socket).await, "STARTTLS\r\n");
                say(&mut socket, "220 upgrade\r\n").await;
                socket = Box::new(TlsAcceptor::from(cfg).accept(socket).await.unwrap());
                assert!(line(&mut socket).await.starts_with("EHLO "));
                say(&mut socket, "250 capture\r\n").await;
            }
            assert!(line(&mut socket).await.starts_with("MAIL FROM:"));
            say(&mut socket, "250 sender\r\n").await;
            assert!(line(&mut socket).await.starts_with("RCPT TO:"));
            say(&mut socket, "251 recipient\r\n").await;
            assert_eq!(line(&mut socket).await, "DATA\r\n");
            say(&mut socket, "354 data\r\n").await;
            let mut data = String::new();
            loop {
                let next = line(&mut socket).await;
                if next == ".\r\n" {
                    break;
                }
                data.push_str(&next);
            }
            assert!(data.contains("Purpose: verify_email\r\nCode: e1."));
            say(&mut socket, "250 accepted\r\n").await;
        });
        let mut roots = rustls::RootCertStore::empty();
        roots.add(CertificateDer::from(cert.clone())).unwrap();
        let sender = Sender::new(
            Wire {
                address,
                from: "sender@example.test".into(),
                mode: mode.into(),
                ..Wire::default()
            },
            false,
            roots,
        )
        .unwrap();
        sender
            .send(
                &Control::default(),
                Mail {
                    to: "owner@example.test".into(),
                    purpose: recovery::Purpose::VerifyEmail,
                    token: Secret::new(format!("e1.{}", "a".repeat(43))),
                },
            )
            .await
            .unwrap();
        worker.await.unwrap();
    }
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let address = listener.local_addr().unwrap().to_string();
    let worker = tokio::spawn(async move {
        let socket = listener.accept().await.unwrap().0;
        assert!(TlsAcceptor::from(server).accept(socket).await.is_err());
    });
    let mut roots = rustls::RootCertStore::empty();
    roots.add(CertificateDer::from(cert)).unwrap();
    let sender = Sender::new(
        Wire {
            address,
            from: "sender@example.test".into(),
            mode: "tls".into(),
            ..Wire::default()
        },
        false,
        roots,
    )
    .unwrap();
    assert!(
        sender
            .send(
                &Control::default(),
                Mail {
                    to: "owner@example.test".into(),
                    purpose: recovery::Purpose::VerifyEmail,
                    token: Secret::new(format!("e1.{}", "a".repeat(43)))
                }
            )
            .await
            .is_err()
    );
    worker.await.unwrap();
    assert!(
        Sender::new(
            Wire {
                address: "localhost:25".into(),
                from: "sender@example.test".into(),
                mode: "development".into(),
                ..Wire::default()
            },
            false,
            rustls::RootCertStore::empty()
        )
        .is_err()
    );
}
