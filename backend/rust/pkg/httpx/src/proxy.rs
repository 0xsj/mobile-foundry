//! Resolve only one address from an explicitly trusted accepted socket peer.
use crate::PeerAddress;
use axum::extract::Request;
use kernel::{Failure, Kind};
use runtime_config::TrustedProxies;
pub fn admit(request: &mut Request, trusted: &TrustedProxies) -> Result<(), Failure> {
    let Some(peer) = request.extensions().get::<PeerAddress>().copied() else {
        return Ok(());
    };
    if !trusted.contains(peer.0) {
        return Ok(());
    }
    let mut values = request.headers().get_all("x-forwarded-for").iter();
    let address = values
        .next()
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.parse::<std::net::IpAddr>().ok());
    if values.next().is_some() || address.is_none() {
        return Err(Failure::new(
            Kind::Invalid,
            "http.invalid_json",
            "invalid trusted proxy address",
        )
        .with_public("Send one valid JSON object with the documented fields."));
    }
    let address = address.expect("admitted address");
    let address = match address {
        std::net::IpAddr::V6(ip) => ip.to_ipv4_mapped().map_or(address, Into::into),
        _ => address,
    };
    request.extensions_mut().insert(PeerAddress(address));
    Ok(())
}
