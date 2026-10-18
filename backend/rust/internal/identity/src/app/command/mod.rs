pub mod recovery;
use crate::{
    app::{
        Grant,
        ports::{Passwords, Purpose, Rotation, Scope, Store, Tokens},
    },
    domain::{self, DisplayName, Login, Password, Principal, Secret, Session, Timestamp},
};
use kernel::{Failure, clock::Clock, id::Generator};
use std::sync::Arc;

#[derive(Clone)]
pub struct Dependencies {
    pub store: Arc<dyn Store>,
    pub passwords: Arc<dyn Passwords>,
    pub tokens: Arc<dyn Tokens>,
    pub ids: Arc<dyn Generator>,
    pub clock: Arc<dyn Clock>,
}

#[derive(Clone)]
pub struct Register(pub Dependencies);
impl Register {
    pub async fn handle(
        &self,
        scope: &dyn Scope,
        login: &str,
        name: &str,
        password: Secret,
    ) -> Result<Principal, Failure> {
        scope.check()?;
        let login = Login::new(login)?;
        let name = DisplayName::new(name)?;
        let password = Password::new(password)?;
        let hash = self.0.passwords.hash(scope, password).await?;
        let principal = Principal::new(
            self.0.ids.new_id()?,
            login,
            name,
            hash,
            Timestamp::from_system_time(self.0.clock.now())?,
        )?;
        self.0.store.register(scope, principal.clone()).await?;
        Ok(principal)
    }
}

#[derive(Clone)]
pub struct SignIn(pub Dependencies);
impl SignIn {
    pub async fn handle(
        &self,
        scope: &dyn Scope,
        login: &str,
        password: Secret,
    ) -> Result<Grant, Failure> {
        scope.check()?;
        let login = Login::new(login).map_err(|_| domain::credential_refused())?;
        let password = Password::new(password).map_err(|_| domain::credential_refused())?;
        let principal = match self.0.store.by_login(scope, login.value()).await? {
            Some(principal) => principal,
            None => {
                self.0.passwords.dummy(scope, password).await?;
                return Err(domain::credential_refused());
            }
        };
        if !self
            .0
            .passwords
            .verify(scope, principal.password_hash().clone(), password)
            .await?
        {
            return Err(domain::credential_refused());
        }
        let access = self.0.tokens.issue(scope, Purpose::Access)?;
        let refresh = self.0.tokens.issue(scope, Purpose::Refresh)?;
        let session = Session::new(
            self.0.ids.new_id()?,
            principal.id().clone(),
            access.digest,
            refresh.digest,
            Timestamp::from_system_time(self.0.clock.now())?,
        )?;
        self.0
            .store
            .issue_checked(scope, session.clone(), principal.password_hash())
            .await?;
        Ok(Grant {
            principal,
            session,
            access: access.raw,
            refresh: refresh.raw,
        })
    }
}

#[derive(Clone)]
pub struct Renew(pub Dependencies);
impl Renew {
    pub async fn handle(&self, scope: &dyn Scope, token: Secret) -> Result<Grant, Failure> {
        let digest = self.0.tokens.digest(scope, &token, Purpose::Refresh)?;
        let access = self.0.tokens.issue(scope, Purpose::Access)?;
        let refresh = self.0.tokens.issue(scope, Purpose::Refresh)?;
        let session = match self
            .0
            .store
            .rotate(
                scope,
                digest,
                access.digest,
                refresh.digest,
                Timestamp::from_system_time(self.0.clock.now())?,
            )
            .await?
        {
            Rotation::Reused => return Err(domain::credential_refused()),
            Rotation::Rotated(session) => session,
        };
        let principal = self
            .0
            .store
            .by_id(scope, &session.state().principal_id)
            .await?
            .ok_or_else(domain::invalid_state)?;
        Ok(Grant {
            principal,
            session,
            access: access.raw,
            refresh: refresh.raw,
        })
    }
}

#[derive(Clone)]
pub struct SignOut(pub Dependencies);
impl SignOut {
    pub async fn handle(&self, scope: &dyn Scope, token: Secret) -> Result<(), Failure> {
        let digest = self.0.tokens.digest(scope, &token, Purpose::Refresh)?;
        self.0
            .store
            .revoke(
                scope,
                digest,
                Timestamp::from_system_time(self.0.clock.now())?,
            )
            .await
    }
}
