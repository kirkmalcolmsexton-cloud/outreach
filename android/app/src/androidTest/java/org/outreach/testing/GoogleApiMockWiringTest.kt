package org.outreach.testing

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.outreach.core.data.AppConfigStore
import org.outreach.core.data.GeocodingService
import org.outreach.core.data.OutreachDatabase
import org.outreach.core.data.OutreachRepository
import org.outreach.core.data.OutreachServiceLocator

@RunWith(AndroidJUnit4::class)
class GoogleApiMockWiringTest {
    @After
    fun clearOverrides() {
        OutreachUiTestEnvironment.clearOverrides()
    }

    @Test
    fun installOverrides_usesFakeSheetsApiForRepositoryCalls() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fakeSheetsApi = FakeSheetsApi(
            tabsBySpreadsheetId = mapOf("sheet-test" to listOf("60618", "60657"))
        )
        val repository = OutreachRepository(
            dao = OutreachDatabase.create(context).dao(),
            configStore = AppConfigStore(context),
            sheetsApi = fakeSheetsApi,
            geocoder = GeocodingService(context)
        )
        OutreachUiTestEnvironment.installOverrides(repository = repository)

        val tabs = OutreachServiceLocator.repository?.availableTabs("sheet-test").orEmpty()
        assertEquals(listOf("60618", "60657"), tabs)
    }

    @Test
    fun sampleDriveSpreadsheet_canBeSelected_andProvidesRows() = runBlocking {
        val fakeSheetsApi = FakeSheetsApi.withSampleDriveSpreadsheet()

        val resolvedId = fakeSheetsApi.resolveSpreadsheetIdFromDriveMetadata(
            displayName = "Sample Outreach Households",
            lastModifiedMillis = null
        )
        assertEquals("sample-drive-sheet-001", resolvedId)

        val tabs = fakeSheetsApi.listTabs(resolvedId!!)
        assertEquals(listOf("60618"), tabs)

        val rows = fakeSheetsApi.fetchRows(resolvedId, "60618")
        assertEquals(2, rows.size)
        assertEquals("Sample Person One", rows[0].name)
        assertNotNull(rows[0].streetAddress)
    }
}
