use crate::{
    app::{
        command::Dependencies,
        ports::{Purpose as TokenPurpose, Scope},
        recovery::{Mail, Mailer, RecoveryStore, challenge_refused},
    },
    domain::{
        self, Password, Principal, Secret, Timestamp,
        recovery::{self, Challenge, Purpose},
    },
};
use kernel::{Failure, id::Id};
use std::sync::Arc;
#[derive(Clone)]
pub struct Recovery {
    pub core: Dependencies,
    pub store: Arc<dyn RecoveryStore>,
    pub mail: Arc<dyn Mailer>,
}
impl Recovery {
    async fn stage(
        &self,
        s: &dyn Scope,
        p: Principal,
        address: String,
        purpose: Purpose,
    ) -> Result<(), Failure> {
        let token_purpose = match purpose {
            Purpose::VerifyEmail => TokenPurpose::VerifyEmail,
            Purpose::ResetPassword => TokenPurpose::ResetPassword,
        };
        let issued = self.core.tokens.issue(s, token_purpose)?;
        let now = Timestamp::from_system_time(self.core.clock.now())?;
        let expires = Timestamp::from_micros(
            now.micros()
                .checked_add(recovery::CHALLENGE_LIFETIME)
                .ok_or_else(domain::invalid_state)?,
        )?;
        self.store
            .begin_challenge(
                s,
                Challenge {
                    principal_id: p.id().clone(),
                    purpose,
                    email: address.clone(),
                    digest: issued.digest,
                    credential_hash: p.password_hash().clone(),
                    created_at: now,
                    expires_at: expires,
                },
            )
            .await?;
        self.mail
            .send(
                s,
                Mail {
                    to: address,
                    purpose,
                    token: issued.raw,
                },
            )
            .await
    }
    pub async fn request_verification(
        &self,
        s: &dyn Scope,
        actor: &Id,
        address: &str,
        password: Secret,
    ) -> Result<(), Failure> {
        let address = recovery::email(address)?;
        let password = Password::new(password).map_err(|_| domain::credential_refused())?;
        let p = self
            .core
            .store
            .by_id(s, actor)
            .await?
            .ok_or_else(domain::credential_refused)?;
        if !self
            .core
            .passwords
            .verify(s, p.password_hash().clone(), password)
            .await?
        {
            return Err(domain::credential_refused());
        }
        self.stage(s, p, address, Purpose::VerifyEmail).await
    }
    pub async fn verify(&self, s: &dyn Scope, token: Secret) -> Result<(), Failure> {
        let digest = self
            .core
            .tokens
            .digest(s, &token, TokenPurpose::VerifyEmail)
            .map_err(|_| challenge_refused())?;
        self.store
            .verify_email(
                s,
                digest,
                Timestamp::from_system_time(self.core.clock.now())?,
            )
            .await
    }
    /// Worker-only lookup; the public endpoint enqueues without address lookup.
    pub async fn request_reset(&self, s: &dyn Scope, address: &str) -> Result<(), Failure> {
        let address = recovery::email(address)?;
        if let Some(p) = self.store.by_email(s, &address).await? {
            self.stage(s, p, address, Purpose::ResetPassword).await?;
        }
        Ok(())
    }
    pub async fn reset(
        &self,
        s: &dyn Scope,
        token: Secret,
        password: Secret,
    ) -> Result<(), Failure> {
        let password = Password::new(password)?;
        let digest = self
            .core
            .tokens
            .digest(s, &token, TokenPurpose::ResetPassword)
            .map_err(|_| challenge_refused())?;
        let hash = self.core.passwords.hash(s, password).await?;
        self.store
            .reset_password(
                s,
                digest,
                hash,
                Timestamp::from_system_time(self.core.clock.now())?,
            )
            .await
    }
    pub async fn change(
        &self,
        s: &dyn Scope,
        actor: &Id,
        current: Secret,
        next: Secret,
    ) -> Result<(), Failure> {
        let next = Password::new(next)?;
        let current = Password::new(current).map_err(|_| domain::credential_refused())?;
        let p = self
            .core
            .store
            .by_id(s, actor)
            .await?
            .ok_or_else(domain::credential_refused)?;
        if !self
            .core
            .passwords
            .verify(s, p.password_hash().clone(), current)
            .await?
        {
            return Err(domain::credential_refused());
        }
        let hash = self.core.passwords.hash(s, next).await?;
        self.store
            .change_password(
                s,
                actor,
                p.password_hash().clone(),
                hash,
                Timestamp::from_system_time(self.core.clock.now())?,
            )
            .await
    }
}
