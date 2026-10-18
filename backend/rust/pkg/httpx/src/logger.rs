//! Allowlisted log events. No URI, body, credential, diagnostic cause, or panic payload.
use runtime_config::{LogFormat, LogLevel};
use serde_json::{Value, json};
use std::{
    io::Write,
    sync::{Arc, Mutex},
};

#[derive(Clone)]
pub struct Logger {
    writer: Arc<Mutex<Box<dyn Write + Send>>>,
    format: LogFormat,
    level: LogLevel,
}
impl Logger {
    pub fn new(writer: impl Write + Send + 'static, format: LogFormat, level: LogLevel) -> Self {
        Self {
            writer: Arc::new(Mutex::new(Box::new(writer))),
            format,
            level,
        }
    }
    fn emit(&self, level: LogLevel, event: Value) {
        if level < self.level {
            return;
        }
        if let Ok(mut writer) = self.writer.lock() {
            match self.format {
                LogFormat::Json => {
                    let _ = writeln!(writer, "{event}");
                }
                LogFormat::Console => {
                    let _ = writeln!(writer, "{level:?} {event}");
                }
            }
        }
    }
    pub fn completed(&self, request_id: &str, method: &str, status: u16, elapsed_ms: u128) {
        self.emit(
            LogLevel::Info,
            json!({"message":"request completed", "request_id":request_id,
            "method":method, "status":status, "elapsed_ms":elapsed_ms}),
        );
    }
    pub fn failure(&self, request_id: &str, kind: &str, code: Option<&str>) {
        self.emit(
            LogLevel::Warn,
            json!({"message":"request failed", "request_id":request_id,"kind":kind,"code":code}),
        );
    }
    pub fn panic(&self, request_id: &str) {
        self.emit(
            LogLevel::Error,
            json!({"message":"request panic", "request_id":request_id}),
        );
    }
    pub fn lifecycle(&self, message: Lifecycle) {
        self.emit(LogLevel::Info, json!({"message":match message {
            Lifecycle::Serving => "server serving", Lifecycle::Draining => "server draining", Lifecycle::Stopped => "server stopped",
        }}));
    }
}
pub enum Lifecycle {
    Serving,
    Draining,
    Stopped,
}
