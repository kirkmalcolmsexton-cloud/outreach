package org.outreach.core.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleSheetsApiHttpMockTest {
    @Test
    fun listTabs_usesInjectedSheetsBaseUrl_andParsesResponse() {
        runBlocking {
            val server = MockWebServer()
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"sheets":[{"properties":{"title":"60618"}},{"properties":{"title":"60657"}}]}"""
                )
            )
            server.start()
            try {
                val requestedScopes = mutableListOf<List<String>>()
                val tokenProvider = object : GoogleAccessTokenProvider {
                    override suspend fun getAccessToken(vararg scopes: String): String {
                        requestedScopes += scopes.toList()
                        return "test-token"
                    }
                }
                val baseUrl = server.url("/").toString().trimEnd('/')
                val api = GoogleSheetsApi(
                    tokenProvider = tokenProvider,
                    endpoints = GoogleApiEndpoints(
                        driveBaseUrl = baseUrl,
                        sheetsBaseUrl = baseUrl
                    )
                )

                val tabs = api.listTabs("sheet-123")

                assertEquals(listOf("60618", "60657"), tabs)
                assertEquals(1, requestedScopes.size)
                assertTrue(requestedScopes.single().contains("https://www.googleapis.com/auth/spreadsheets"))
                assertEquals(
                    "/v4/spreadsheets/sheet-123?fields=sheets.properties.title",
                    server.takeRequest().path
                )
            } finally {
                server.shutdown()
            }
        }
    }

    @Test
    fun resolveSpreadsheetIdFromDriveMetadata_usesDriveScope_andDriveEndpoint() {
        runBlocking {
            val server = MockWebServer()
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"files":[{"id":"sheet-abc","name":"Volunteer List","modifiedTime":"2025-01-01T10:00:00Z"}]}"""
                )
            )
            server.start()
            try {
                val requestedScopes = mutableListOf<List<String>>()
                val tokenProvider = object : GoogleAccessTokenProvider {
                    override suspend fun getAccessToken(vararg scopes: String): String {
                        requestedScopes += scopes.toList()
                        return "drive-token"
                    }
                }
                val baseUrl = server.url("/").toString().trimEnd('/')
                val api = GoogleSheetsApi(
                    tokenProvider = tokenProvider,
                    endpoints = GoogleApiEndpoints(
                        driveBaseUrl = baseUrl,
                        sheetsBaseUrl = baseUrl
                    )
                )

                val resolved = api.resolveSpreadsheetIdFromDriveMetadata("Volunteer List", null)

                assertEquals("sheet-abc", resolved)
                assertEquals(1, requestedScopes.size)
                assertTrue(requestedScopes.single().contains("https://www.googleapis.com/auth/drive.metadata.readonly"))
                assertTrue(server.takeRequest().path.orEmpty().startsWith("/drive/v3/files"))
            } finally {
                server.shutdown()
            }
        }
    }

    @Test(expected = IOException::class)
    fun listTabs_throwsWhenAccessTokenMissing() {
        runBlocking {
            val tokenProvider = object : GoogleAccessTokenProvider {
                override suspend fun getAccessToken(vararg scopes: String): String? = null
            }
            val api = GoogleSheetsApi(tokenProvider = tokenProvider)
            api.listTabs("sheet-123")
        }
    }
}
