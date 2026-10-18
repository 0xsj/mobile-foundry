use axum::http::HeaderMap;
use kernel::Failure;
pub fn bearer(headers: &HeaderMap) -> Result<&str, Failure> {
    let mut values = headers.get_all("authorization").iter();
    match (values.next(), values.next()) {
        (Some(value), None) => value
            .to_str()
            .ok()
            .and_then(|v| v.split_once(' '))
            .filter(|(scheme, token)| {
                scheme.eq_ignore_ascii_case("Bearer")
                    && !token.is_empty()
                    && !token.contains([' ', '\t', '\r', '\n'])
            })
            .map(|(_, token)| token)
            .ok_or_else(principal::required),
        _ => Err(principal::required()),
    }
}
