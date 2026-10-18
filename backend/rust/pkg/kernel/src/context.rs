//! Mechanical cancellation and correlation context, shared without business authority.
use crate::Failure;
use std::{future::Future, pin::Pin};

pub type PortFuture<'a, T> = Pin<Box<dyn Future<Output = Result<T, Failure>> + Send + 'a>>;
#[derive(Clone, Debug, Default)]
pub struct Metadata {
    pub request_id: String,
    pub correlation_id: Option<String>,
}
pub trait Scope: Send + Sync {
    fn check(&self) -> Result<(), Failure>;
    fn metadata(&self) -> Metadata;
}
