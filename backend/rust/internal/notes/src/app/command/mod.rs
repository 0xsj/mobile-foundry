use crate::{
    app::{self, Dependencies},
    domain::{self, Action, Receipt, Write as Input},
};
use kernel::{Failure, context::Scope, id::Id};
#[derive(Clone)]
pub struct Write(pub Dependencies);
impl Write {
    pub async fn handle(
        &self,
        s: &dyn Scope,
        actor: &Id,
        account: &Id,
        input: Input,
    ) -> Result<Receipt, Failure> {
        let d = &self.0;
        app::authorize(s, d.access.as_ref(), actor, account).await?;
        input.validate()?;
        let candidate = if input.action == Action::Create
            && d.store
                .receipt(s, account, actor, &input.command_id)
                .await?
                .is_none()
        {
            Some(d.ids.new_id()?)
        } else {
            None
        };
        let result = d
            .store
            .apply(s, account, actor, input.clone(), candidate)
            .await?;
        let r = result.snapshot();
        if r.command_id != input.command_id
            || &r.account_id != account
            || &r.principal_id != actor
            || r.action != input.action
            || r.title != input.title
            || (input.action != Action::Create
                && (Some(&r.note_id) != input.note_id.as_ref() || r.version != input.expected + 1))
        {
            return Err(domain::invalid_state());
        }
        if let Some(changes) = &d.changes {
            changes.changed(account);
        }
        Ok(result)
    }
}
