//! Shell-free bounded container readiness, fixed loopback target; no environment input.
use tokio::io::{AsyncReadExt, AsyncWriteExt};
#[tokio::main]
async fn main() -> std::process::ExitCode {
    let ready = tokio::time::timeout(std::time::Duration::from_secs(2), async {
        let mut socket = tokio::net::TcpStream::connect("127.0.0.1:8080").await?;
        socket
            .write_all(
                b"GET /v1/health/ready HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n",
            )
            .await?;
        let mut status = Vec::new();
        loop {
            let byte = socket.read_u8().await?;
            status.push(byte);
            if byte == b'\n' || status.len() >= 128 {
                break;
            }
        }
        Ok::<bool, std::io::Error>(status == b"HTTP/1.1 200 OK\r\n")
    })
    .await;
    if matches!(ready, Ok(Ok(true))) {
        std::process::ExitCode::SUCCESS
    } else {
        std::process::ExitCode::FAILURE
    }
}
