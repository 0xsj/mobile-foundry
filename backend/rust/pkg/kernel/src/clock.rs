//! Business wall time is injectable; request deadlines still use the runtime's monotonic clock.
use std::time::SystemTime;

pub trait Clock: Send + Sync {
    fn now(&self) -> SystemTime;
}

#[derive(Clone, Copy, Default)]
pub struct SystemClock;
impl Clock for SystemClock {
    fn now(&self) -> SystemTime {
        SystemTime::now()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{
        sync::Arc,
        time::{Duration, UNIX_EPOCH},
    };
    struct Fixed(SystemTime);
    impl Clock for Fixed {
        fn now(&self) -> SystemTime {
            self.0
        }
    }

    #[test]
    fn business_clock_is_swappable_without_runtime_time() {
        let expected = UNIX_EPOCH + Duration::from_secs(42);
        let clock: Arc<dyn Clock> = Arc::new(Fixed(expected));
        assert_eq!(clock.now(), expected);
        assert_eq!(clock.now(), clock.now());
    }
}
