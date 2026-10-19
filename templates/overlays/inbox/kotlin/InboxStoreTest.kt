package dev.mobilefoundry.product.connection

import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.session.SessionScope
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class InboxStoreTest {
    private val account = SessionScope.make("go.test","00000000-0000-4000-8000-000000000003","00000000-0000-4000-8000-000000000002")!!
    private class Repository(val scope: SessionScope, val gate: CompletableDeferred<Unit>? = null, val duplicatePage: Boolean = false) : NotificationInboxRepository {
        var enabled = true; var settingsWrites = 0; var loseSetting = true
        var read = false; var readWrites = 0
        private val item get() = InboxNotification.make("00000000-0000-4000-8000-000000000001",scope.accountId,scope.principalId,"00000000-0000-4000-8000-000000000004",Instant.ofEpochSecond(1000),Instant.ofEpochSecond(1001),if (read) Instant.ofEpochSecond(1002) else null)!!
        override suspend fun page(cursor: String?, limit: Int, unreadOnly: Boolean): AppResult<NotificationInboxPage> {
            if (gate != null) withContext(NonCancellable) { gate.await() }
            return Outcome.Ok(NotificationInboxPage(if (unreadOnly && read) emptyList() else listOf(item),if (gate != null) 999 else if (read) 0 else 1,if (duplicatePage && cursor == null) "next" else null))
        }
        override suspend fun mediaEnabled(): AppResult<Boolean> = Outcome.Ok(enabled)
        override suspend fun setMediaEnabled(enabled: Boolean): AppResult<Boolean> {
            settingsWrites++; this.enabled = enabled
            if (loseSetting) { loseSetting=false;return Outcome.Err(Failure.Unavailable(FailureMeta("Acknowledgement lost"))) };return Outcome.Ok(enabled)
        }
        override suspend fun markRead(item: InboxNotification): AppResult<InboxNotification> {
            readWrites++; read = true
            return Outcome.Err(Failure.Unavailable(FailureMeta("Acknowledgement lost")))
        }
    }
    @Test fun uncertainPreferenceRequiresReadbackAndNeverAutoReplays() = runTest {
        val repo = Repository(account); val model = InboxStore(this);model.bind(repo,account);advanceUntilIdle()
        assertTrue(model.canEdit);assertEquals(true,model.state.value.mediaEnabled)
        model.setMediaEnabled(false);advanceUntilIdle();assertTrue(model.state.value.needsRefresh);assertFalse(model.canEdit);assertEquals(true,model.state.value.mediaEnabled)
        model.setMediaEnabled(false);assertEquals(1,repo.settingsWrites)
        model.refresh();advanceUntilIdle();assertEquals(false,model.state.value.mediaEnabled);assertTrue(model.canEdit)
        model.bind(null,null);assertTrue(model.state.value.items.isEmpty());assertNull(model.state.value.unreadCount)
        model.bind(repo,account);advanceUntilIdle();assertEquals(false,model.state.value.mediaEnabled);assertEquals(1,repo.settingsWrites)
    }
    @Test fun lateCompletionCannotPublishAfterAccountReplacement() = runTest {
        val gate = CompletableDeferred<Unit>();val old = Repository(account,gate);val model = InboxStore(this);model.bind(old,account);runCurrent()
        val other = SessionScope.make(account.authorityId,account.principalId,"00000000-0000-4000-8000-000000000099")!!
        model.bind(Repository(other),other);runCurrent();gate.complete(Unit);advanceUntilIdle()
        assertEquals(1L,model.state.value.unreadCount);assertTrue(model.state.value.items.all { it.accountId == other.accountId })
        model.bind(null,null)
    }
    @Test fun unconfirmedReadRequiresServerCountAndDoesNotReplay() = runTest {
        val repo = Repository(account); val model = InboxStore(this)
        model.bind(repo,account); advanceUntilIdle()
        val item = model.state.value.items.single()
        model.markRead(item); advanceUntilIdle()
        assertTrue(model.state.value.needsRefresh); assertFalse(model.canEdit); assertEquals(1L,model.state.value.unreadCount)
        model.markRead(item); assertEquals(1,repo.readWrites)
        model.refresh(); advanceUntilIdle()
        assertEquals(0L,model.state.value.unreadCount); assertFalse(model.state.value.items.single().isUnread); assertTrue(model.canEdit)
        model.chooseUnreadOnly(true); advanceUntilIdle()
        assertTrue(model.state.value.items.isEmpty()); assertEquals(0L,model.state.value.unreadCount); assertFalse(model.state.value.needsRefresh)
        assertEquals(1,repo.readWrites); model.bind(null,null)
    }
    @Test fun duplicatePagingStopsUntilFullRefresh() = runTest {
        val repo = Repository(account,duplicatePage=true); val model = InboxStore(this)
        model.bind(repo,account); advanceUntilIdle(); assertTrue(model.canLoadMore)
        model.loadMore(); advanceUntilIdle()
        assertEquals(1,model.state.value.items.size); assertTrue(model.state.value.needsRefresh); assertFalse(model.canLoadMore); assertNotNull(model.state.value.failure)
        model.refresh(); advanceUntilIdle()
        assertEquals(1,model.state.value.items.size); assertTrue(model.canLoadMore); assertFalse(model.state.value.needsRefresh)
        model.bind(null,null)
    }
}
