package dev.mobilefoundry.catalog.installations

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PushTokenTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val route = PushRoute.make(PushProvider.FCM,"dev.example.app",PushEnvironment.PRODUCTION)!!

    @Test fun defaultConfigurationDoesNotInitializeFirebaseOrCollect(): Unit = runBlocking(Dispatchers.Main) {
        val tokens = PushTokenStore()
        val before = FirebaseApp.getApps(context).map { it.name }
        val provider = PushTokenComposition.provider(context)
        assertEquals(Outcome.Err(PushTokenFailures.unconfigured),provider.collect())
        assertEquals(before,FirebaseApp.getApps(context).map { it.name })
        assertNull(tokens.state.value.target)
        var requests = 0
        assertEquals(Outcome.Err(PushTokenFailures.unconfigured),AndroidPushTokens(tokens,{ null }) { requests++;Outcome.Ok("unused") }.collect())
        assertEquals(0,requests)
    }

    @Test fun rotationCallbackWinsOverEarlierCollectionAndInvalidTokenIsRefused(): Unit = runBlocking(Dispatchers.Main) {
        val tokens = PushTokenStore();val held = CompletableDeferred<AppResult<String>>();var requests = 0
        val provider = AndroidPushTokens(tokens,{ route }) { requests++;held.await() }
        val pending = async { provider.collect() };yield()
        assertEquals(Outcome.Err(PushTokenFailures.unavailable),provider.collect())
        AndroidPushTokens.received(tokens,route,"opaque.rotated")
        held.complete(Outcome.Ok("opaque.old"))
        assertEquals(Outcome.Ok(Unit),pending.await())
        assertEquals("opaque.rotated",tokens.state.value.target?.token);assertEquals(1,requests)
        tokens.receive(null)
        val invalid = AndroidPushTokens(tokens,{ route }) { Outcome.Ok("not a token") }
        assertEquals(Outcome.Err(PushTokenFailures.unavailable),invalid.collect());assertNull(tokens.state.value.target)
    }

    @Test fun invalidCallbackCannotBeResurrectedByEarlierTask(): Unit = runBlocking(Dispatchers.Main) {
        val tokens = PushTokenStore();val held = CompletableDeferred<AppResult<String>>()
        val provider = AndroidPushTokens(tokens,{ route }) { held.await() }
        val pending = async { provider.collect() };yield()
        AndroidPushTokens.received(tokens,route,"invalid address")
        held.complete(Outcome.Ok("opaque.older"))
        assertEquals(Outcome.Err(PushTokenFailures.unavailable),pending.await());assertNull(tokens.state.value.target)
    }

    @Test fun cancellationDropsCollectionAndPreservesFutureExplicitCollection(): Unit = runBlocking(Dispatchers.Main) {
        val tokens = PushTokenStore();var calls = 0
        val provider = AndroidPushTokens(tokens,{ route }) {
            if (++calls == 1) awaitCancellation() else Outcome.Ok("opaque.next")
        }
        val pending = async { provider.collect() };yield();pending.cancelAndJoin()
        assertNull(tokens.state.value.target)
        assertEquals(Outcome.Ok(Unit),provider.collect());assertEquals("opaque.next",tokens.state.value.target?.token)
    }

    @Suppress("DEPRECATION")
    @Test fun mergedManifestKeepsCollectionOptInAndServicePrivate() {
        val manager = context.packageManager
        val metadata = manager.getApplicationInfo(context.packageName,PackageManager.GET_META_DATA).metaData
        assertFalse(metadata.getBoolean("firebase_messaging_auto_init_enabled",true))
        assertFalse(metadata.getBoolean("firebase_analytics_collection_enabled",true))
        assertFalse(metadata.getBoolean("firebase_messaging_installation_id_enabled",true))
        assertFalse(manager.getServiceInfo(ComponentName(context,PushMessagingService::class.java),0).exported)
        val providers = manager.getPackageInfo(context.packageName,PackageManager.GET_PROVIDERS).providers.orEmpty()
        assertFalse(providers.single { it.name == "com.google.firebase.provider.FirebaseInitProvider" }.exported)
    }
}
