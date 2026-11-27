package tv.medusa.app.data.repository

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.medusa.app.data.local.ServerSyncPreferencesStore
import tv.medusa.app.data.local.UserPreferences
import tv.medusa.app.data.remote.NetworkClientFactory
import tv.medusa.app.data.remote.tmdb.TmdbApiService

class MedusaServerSyncTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun apply_syncsTmdbProxyAndTraktCredentials_includingLegacyPayloadWithoutConfiguredFlag() = runBlocking {
        val baseUrl = server.url("/").toString().trimEnd('/')
        val store = FakeSyncStore(
            UserPreferences(
                jellyfinServerUrl = baseUrl,
                jellyfinAccessToken = "jf-token",
                tmdbApiKey = "",
                tmdbUseServer = true,
                traktUseServer = true
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """{"tmdb_configured":true,"trakt_synced":true,"trakt_username":"alice","trakt_revision":3}"""
            )
        )
        // Simula respuesta sin `configured` explícito para comprobar compatibilidad retroactiva.
        server.enqueue(
            MockResponse().setBody(
                """{"access_token":"trakt-tok","client_id":"cid-1","client_secret":"sec-1","username":"alice","revision":3}"""
            )
        )

        val result = MedusaServerSync.apply(store)

        assertTrue(result.isSuccess)
        assertTrue(store.prefs.tmdbServerConfigured)
        assertTrue(store.prefs.tmdbUseServer)
        assertTrue(store.prefs.hasServerTmdbProxy)
        assertTrue(store.prefs.hasTmdbAccess)
        assertEquals("trakt-tok", store.prefs.traktAccessToken)
        assertEquals("cid-1", store.prefs.traktClientId)
        assertEquals("sec-1", store.prefs.traktClientSecret)
        assertEquals(3, store.prefs.traktServerRevision)

        val configReq = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/Medusa/Config", configReq.path)
        assertTrue(configReq.getHeader("Authorization")?.contains("jf-token") == true)
        val traktReq = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/Medusa/Trakt/Credentials", traktReq.path)
    }

    @Test
    fun apply_preservesTmdbUseServerOnTransientError_andDisablesServerFlagOn404() = runBlocking {
        val baseUrl = server.url("/").toString().trimEnd('/')
        val store = FakeSyncStore(
            UserPreferences(
                jellyfinServerUrl = baseUrl,
                jellyfinAccessToken = "jf-token",
                tmdbApiKey = "",
                tmdbUseServer = true,
                tmdbServerConfigured = true
            )
        )
        server.enqueue(MockResponse().setResponseCode(503))

        val transientResult = MedusaServerSync.apply(store)
        assertTrue(transientResult.isFailure)
        assertTrue(store.prefs.tmdbUseServer)
        assertTrue(store.prefs.tmdbServerConfigured)

        server.enqueue(MockResponse().setResponseCode(404))
        val notFoundResult = MedusaServerSync.apply(store)
        assertTrue(notFoundResult.isFailure)
        assertTrue(store.prefs.tmdbUseServer)
        assertFalse(store.prefs.tmdbServerConfigured)
        assertFalse(store.prefs.hasTmdbAccess)
    }

    @Test
    fun apply_clearsSyncedTraktWhenRemoteUnlinked_butRespectsLocalOverride() = runBlocking {
        val baseUrl = server.url("/").toString().trimEnd('/')
        val syncedStore = FakeSyncStore(
            UserPreferences(
                jellyfinServerUrl = baseUrl,
                jellyfinAccessToken = "jf-token",
                traktAccessToken = "old-server-tok",
                traktUseServer = true,
                traktServerRevision = 2
            )
        )
        server.enqueue(MockResponse().setBody("""{"tmdb_configured":false,"trakt_synced":false}"""))

        MedusaServerSync.apply(syncedStore)
        assertEquals("", syncedStore.prefs.traktAccessToken)
        assertEquals(0, syncedStore.prefs.traktServerRevision)
        assertTrue(syncedStore.prefs.traktUseServer)

        val localOverrideStore = FakeSyncStore(
            UserPreferences(
                jellyfinServerUrl = baseUrl,
                jellyfinAccessToken = "jf-token",
                traktAccessToken = "my-local-tok",
                traktUseServer = false
            )
        )
        server.enqueue(
            MockResponse().setBody("""{"tmdb_configured":true,"trakt_synced":true,"trakt_revision":4}""")
        )

        MedusaServerSync.apply(localOverrideStore)
        assertEquals("my-local-tok", localOverrideStore.prefs.traktAccessToken)
        assertFalse(localOverrideStore.prefs.traktUseServer)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun createAuthenticatedService_sendsAuthOnlyToMatchingServerPrefix() = runBlocking {
        val external = MockWebServer()
        external.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", external.url("/trending/all/week").toString())
            )
            external.enqueue(MockResponse().setBody("""{"page":1,"results":[]}"""))

            val api = NetworkClientFactory.createAuthenticatedService(
                baseUrl = server.url("/Medusa/Tmdb/").toString(),
                serviceClass = TmdbApiService::class.java,
                authHeader = "MediaBrowser Token=\"secret-jf\""
            )
            api.getTrending(apiKey = "")

            val firstReq = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("MediaBrowser Token=\"secret-jf\"", firstReq.getHeader("Authorization"))
            val redirectedReq = external.takeRequest(5, TimeUnit.SECONDS)!!
            assertNull(redirectedReq.getHeader("Authorization"))
            assertNull(redirectedReq.getHeader("X-Emby-Authorization"))
        } finally {
            external.shutdown()
        }
    }

    @Test
    fun userPreferences_hasTmdbAccessReflectsLocalKeyOrConfiguredProxy() {
        val neither = UserPreferences(tmdbApiKey = "", tmdbUseServer = true, tmdbServerConfigured = false)
        assertFalse(neither.hasTmdbAccess)

        val proxyOnly = UserPreferences(
            jellyfinServerUrl = "https://jf.local",
            jellyfinAccessToken = "tok",
            tmdbApiKey = "",
            tmdbUseServer = true,
            tmdbServerConfigured = true
        )
        assertTrue(proxyOnly.hasServerTmdbProxy)
        assertTrue(proxyOnly.hasTmdbAccess)

        val localOverride = proxyOnly.copy(tmdbApiKey = "my-key", tmdbUseServer = false)
        assertFalse(localOverride.hasServerTmdbProxy)
        assertTrue(localOverride.hasTmdbAccess)
    }

    private class FakeSyncStore(var prefs: UserPreferences) : ServerSyncPreferencesStore {
        override suspend fun readPreferences(): UserPreferences = prefs

        override suspend fun setTmdbServerConfigured(configured: Boolean) {
            prefs = prefs.copy(tmdbServerConfigured = configured)
        }

        override suspend fun applyServerTraktCredentials(
            accessToken: String,
            clientId: String,
            clientSecret: String,
            revision: Int
        ) {
            prefs = prefs.copy(
                traktAccessToken = accessToken,
                traktClientId = clientId,
                traktClientSecret = clientSecret,
                traktUseServer = true,
                traktServerRevision = revision
            )
        }

        override suspend fun clearServerSyncedTraktCredentials() {
            prefs = prefs.copy(
                traktAccessToken = "",
                traktServerRevision = 0,
                traktUseServer = true
            )
        }
    }
}
