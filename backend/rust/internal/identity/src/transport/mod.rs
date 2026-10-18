pub mod capability;
pub mod http;

fn time(value: crate::domain::Timestamp) -> String {
    let value = chrono::DateTime::from_timestamp_micros(value.micros() as i64)
        .expect("domain timestamp has RFC3339 range");
    let base = value.format("%Y-%m-%dT%H:%M:%S").to_string();
    let micros = value.timestamp_subsec_micros();
    if micros == 0 {
        format!("{base}Z")
    } else {
        format!("{base}.{}Z", format!("{micros:06}").trim_end_matches('0'))
    }
}
