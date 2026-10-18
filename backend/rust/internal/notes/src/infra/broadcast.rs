use crate::app::ports::Changes as Port;
use kernel::id::Id;
use std::sync::Arc;
pub struct Changes(pub Arc<::broadcast::Hub>);
impl Port for Changes {
    fn changed(&self, account: &Id) {
        self.0.publish(&format!("notes.{account}"));
    }
}
