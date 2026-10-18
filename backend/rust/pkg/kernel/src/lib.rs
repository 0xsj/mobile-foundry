//! Transport-independent failures. Diagnostics and causes never become public implicitly.
pub mod clock;
pub mod context;
pub mod id;

use serde::{Deserialize, Serialize};
use std::{collections::BTreeMap, error::Error, fmt, sync::Arc};

#[derive(Clone, Copy, Debug, PartialEq, Eq, Deserialize, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Kind {
    Invalid,
    Unauthenticated,
    Forbidden,
    NotFound,
    Conflict,
    RateLimited,
    Unavailable,
    Timeout,
    Canceled,
    Internal,
}

impl Kind {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Invalid => "invalid",
            Self::Unauthenticated => "unauthenticated",
            Self::Forbidden => "forbidden",
            Self::NotFound => "not_found",
            Self::Conflict => "conflict",
            Self::RateLimited => "rate_limited",
            Self::Unavailable => "unavailable",
            Self::Timeout => "timeout",
            Self::Canceled => "canceled",
            Self::Internal => "internal",
        }
    }
}

#[derive(Clone)]
pub struct Failure {
    kind: Kind,
    code: String,
    diagnostic: String,
    public: Option<String>,
    fields: BTreeMap<String, String>,
    cause: Option<Arc<dyn Error + Send + Sync>>,
}

#[derive(Debug, PartialEq, Eq)]
pub struct PublicFailure {
    pub kind: Kind,
    pub code: Option<String>,
    pub message: String,
    pub fields: BTreeMap<String, String>,
}

impl Failure {
    pub fn new(kind: Kind, code: impl Into<String>, diagnostic: impl Into<String>) -> Self {
        Self {
            kind,
            code: code.into(),
            diagnostic: diagnostic.into(),
            public: None,
            fields: BTreeMap::new(),
            cause: None,
        }
    }
    pub fn with_public(mut self, message: impl Into<String>) -> Self {
        self.public = Some(message.into());
        self
    }
    pub fn with_fields(mut self, fields: BTreeMap<String, String>) -> Self {
        self.fields = fields;
        self
    }
    pub fn with_cause(mut self, cause: impl Error + Send + Sync + 'static) -> Self {
        self.cause = Some(Arc::new(cause));
        self
    }
    pub fn kind(&self) -> Kind {
        self.kind
    }
    pub fn public(&self) -> PublicFailure {
        if self.kind == Kind::Internal {
            return PublicFailure {
                kind: self.kind,
                code: None,
                message: "An unexpected error occurred.".into(),
                fields: BTreeMap::new(),
            };
        }
        PublicFailure {
            kind: self.kind,
            code: (!self.code.is_empty()).then(|| self.code.clone()),
            message: self
                .public
                .clone()
                .filter(|s| !s.is_empty())
                .unwrap_or_else(|| "The request failed.".into()),
            fields: if self.kind == Kind::Invalid {
                self.fields.clone()
            } else {
                BTreeMap::new()
            },
        }
    }
}

// Default formatting is safe for accidental logging. Diagnostics require explicit access.
impl fmt::Debug for Failure {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("Failure")
            .field("kind", &self.kind)
            .finish_non_exhaustive()
    }
}
impl fmt::Display for Failure {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{} failure", self.kind.as_str())
    }
}
impl Error for Failure {
    fn source(&self) -> Option<&(dyn Error + 'static)> {
        self.cause.as_deref().map(|e| e as &(dyn Error + 'static))
    }
}
impl Failure {
    /// Private diagnostic access for an explicitly selected diagnostic sink, never HTTP/log defaults.
    pub fn diagnostic(&self) -> &str {
        &self.diagnostic
    }
}
