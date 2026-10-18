use super::*;
use http_body_util::{BodyExt, Full};
use hyper::{
    Request, Response,
    body::{Bytes, Incoming},
    service::service_fn,
};
use hyper_util::rt::{TokioExecutor, TokioIo};
use ring::{
    rand::SystemRandom,
    signature::{ECDSA_P256_SHA256_FIXED_SIGNING, EcdsaKeyPair},
};
use tokio::{
    net::TcpListener,
    sync::oneshot,
    task::{JoinHandle, JoinSet},
};
use tokio_rustls::{
    TlsAcceptor,
    rustls::{
        self,
        pki_types::{CertificateDer, PrivateKeyDer, PrivatePkcs8KeyDer},
    },
};
pub(super) struct Material {
    pub path: PathBuf,
    pub cert: Vec<u8>,
    pub key: Vec<u8>,
    pub apns: Vec<u8>,
}
impl Drop for Material {
    fn drop(&mut self) {
        let _ = std::fs::remove_dir_all(&self.path);
    }
}
impl Material {
    pub fn new() -> Self {
        let path = std::env::temp_dir().join(format!("mobile-push-tls-{}", id()));
        std::fs::create_dir(&path).unwrap();
        let mut value = Self {
            path,
            cert: Vec::new(),
            key: Vec::new(),
            apns: Vec::new(),
        };
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
                "pkcs8", "-topk8", "-nocrypt", "-in", "key.pem", "-outform", "DER", "-out",
                "key.der",
            ],
            vec![
                "rsa",
                "-in",
                "key.pem",
                "-RSAPublicKey_out",
                "-outform",
                "DER",
                "-out",
                "public.der",
            ],
        ] {
            let result = std::process::Command::new("openssl")
                .args(args)
                .current_dir(&value.path)
                .output()
                .expect("local provider acceptance requires openssl");
            assert!(
                result.status.success(),
                "ephemeral test material generation failed"
            );
        }
        value.cert = std::fs::read(value.path.join("cert.der")).unwrap();
        value.key = std::fs::read(value.path.join("key.der")).unwrap();
        let key =
            EcdsaKeyPair::generate_pkcs8(&ECDSA_P256_SHA256_FIXED_SIGNING, &SystemRandom::new())
                .unwrap();
        use base64::{Engine, engine::general_purpose::STANDARD};
        value.apns = format!(
            "-----BEGIN PRIVATE KEY-----\n{}\n-----END PRIVATE KEY-----\n",
            STANDARD.encode(key.as_ref())
        )
        .into_bytes();
        std::fs::write(value.path.join("apns.p8"), &value.apns).unwrap();
        value
    }
    pub fn service_account(&self) -> serde_json::Value {
        serde_json::json!({"type":"service_account","project_id":"test-project","private_key_id":"test-key","private_key":std::fs::read_to_string(self.path.join("key.pem")).unwrap(),"client_email":"sender@test-project.iam.gserviceaccount.com","token_uri":fcm::OAUTH})
    }
    pub fn transport(&self) -> Arc<Transport> {
        let root = reqwest::Certificate::from_der(&self.cert).unwrap();
        Arc::new(Transport {
            clients: Mutex::new(Some(Clients {
                apns: client_builder(true)
                    .add_root_certificate(root.clone())
                    .build()
                    .unwrap(),
                fcm: client_builder(false)
                    .add_root_certificate(root)
                    .build()
                    .unwrap(),
            })),
        })
    }
}
pub(super) struct Capture {
    pub path: String,
    pub headers: HeaderMap,
    pub body: Vec<u8>,
    pub version: hyper::Version,
}
pub(super) struct Reply {
    pub status: u16,
    pub body: String,
    pub location: Option<String>,
    pub delay: Duration,
}
impl Reply {
    pub fn new(status: u16, body: impl Into<String>) -> Self {
        Self {
            status,
            body: body.into(),
            location: None,
            delay: Duration::ZERO,
        }
    }
}
pub(super) struct Server {
    pub url: String,
    stop: Option<oneshot::Sender<()>>,
    worker: Option<JoinHandle<()>>,
}
impl Server {
    pub async fn start(
        material: &Material,
        reply: impl Fn(Capture) -> Reply + Send + Sync + 'static,
    ) -> Self {
        let mut config = rustls::ServerConfig::builder_with_provider(Arc::new(
            rustls::crypto::ring::default_provider(),
        ))
        .with_protocol_versions(&[&rustls::version::TLS13, &rustls::version::TLS12])
        .unwrap()
        .with_no_client_auth()
        .with_single_cert(
            vec![CertificateDer::from(material.cert.clone())],
            PrivateKeyDer::Pkcs8(PrivatePkcs8KeyDer::from(material.key.clone())),
        )
        .unwrap();
        config.alpn_protocols = vec![b"h2".to_vec()];
        let acceptor = TlsAcceptor::from(Arc::new(config));
        let reply = Arc::new(reply);
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let url = format!(
            "https://localhost:{}",
            listener.local_addr().unwrap().port()
        );
        let (stop, mut stopped) = oneshot::channel();
        let worker = tokio::spawn(async move {
            let mut connections = JoinSet::new();
            loop {
                tokio::select! {
                    _=&mut stopped=>break,
                    Some(done)=connections.join_next(),if !connections.is_empty()=>{done.unwrap();},
                    accepted=listener.accept()=>{
                        let (socket,_)=accepted.unwrap();let acceptor=acceptor.clone();let reply=reply.clone();
                        connections.spawn(async move {
                            let Ok(socket)=acceptor.accept(socket).await else { return; };
                            let service=service_fn(move |r:Request<Incoming>| { let reply=reply.clone();async move {
                                let (parts,body)=r.into_parts();let body=body.collect().await.unwrap().to_bytes();
                                let response=reply(Capture {path:parts.uri.path().into(),headers:parts.headers,version:parts.version,body:body.to_vec()});
                                tokio::time::sleep(response.delay).await;
                                let mut result=Response::builder().status(response.status);
                                if let Some(location)=response.location {result=result.header("location",location);}
                                Ok::<_,std::convert::Infallible>(result.body(Full::new(Bytes::from(response.body))).unwrap())
                            }});
                            let _=hyper::server::conn::http2::Builder::new(TokioExecutor::new()).serve_connection(TokioIo::new(socket),service).await;
                        });
                    }
                }
            }
            connections.abort_all();
            while let Some(done) = connections.join_next().await {
                if let Err(e) = done {
                    assert!(e.is_cancelled());
                }
            }
        });
        Self {
            url,
            stop: Some(stop),
            worker: Some(worker),
        }
    }
    pub async fn close(mut self) {
        self.stop.take().unwrap().send(()).unwrap();
        self.worker.take().unwrap().await.unwrap();
    }
}
impl Drop for Server {
    fn drop(&mut self) {
        if let Some(stop) = self.stop.take() {
            let _ = stop.send(());
        }
        if let Some(worker) = self.worker.take() {
            worker.abort();
        }
    }
}
