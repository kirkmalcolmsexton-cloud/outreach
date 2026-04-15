package org.outreach.core.data

import android.content.Context
import android.location.Geocoder
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.outreach.core.model.AppConfig
import org.outreach.core.model.CollaborationEvent
import org.outreach.core.model.HouseholdRecord
import org.outreach.core.model.RawHouseholdRow
import org.outreach.core.model.SourceMetadata
import org.outreach.core.model.SpreadsheetRowInput
import org.outreach.core.model.VisitUpdate
import org.outreach.core.model.createHouseholdId
import org.outreach.core.model.parseSpreadsheetRow
import org.json.JSONArray
import org.json.JSONObject
import org.outreach.debug.agentDebugLog
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

@Entity(tableName = "households")
data class HouseholdEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streetAddress: String,
    val neighborhood: String,
    val briefComment: String,
    val lastVisited: String?,
    val notes: String?,
    val sheetName: String,
    val rowNumber: Int,
    val latitude: Double?,
    val longitude: Double?,
    val assignedTo: String?
)

@Entity(tableName = "pending_sync")
data class PendingSyncEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val householdId: String,
    val briefComment: String,
    val notes: String?,
    val lastVisitedIsoDate: String,
    val sheetName: String?,
    val rowNumber: Int?
)

@Entity(tableName = "pending_append")
data class PendingAppendEntity(
    @PrimaryKey val householdId: String,
    val name: String,
    val streetAddress: String,
    val neighborhood: String,
    val sheetName: String
)

/** Result of appending a household row; [rowNumber] is the sheet row (1-based). */
data class AppendHouseholdResult(val rowNumber: Int)

@Dao
interface OutreachDao {
    @Query("SELECT * FROM households")
    fun observeHouseholds(): Flow<List<HouseholdEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHouseholds(items: List<HouseholdEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPending(sync: PendingSyncEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingAppend(entity: PendingAppendEntity)

    @Query("SELECT * FROM pending_sync ORDER BY id ASC")
    suspend fun pendingSync(): List<PendingSyncEntity>

    @Query("SELECT * FROM pending_append ORDER BY householdId ASC")
    suspend fun pendingAppends(): List<PendingAppendEntity>

    @Query("DELETE FROM pending_sync WHERE id = :id")
    suspend fun deletePending(id: Long)

    @Query("DELETE FROM pending_append WHERE householdId = :householdId")
    suspend fun deletePendingAppend(householdId: String)

    @Query("UPDATE households SET rowNumber = :rowNumber WHERE id = :id")
    suspend fun updateHouseholdRowNumber(id: String, rowNumber: Int)

    @Query("UPDATE households SET briefComment = :briefComment, notes = :notes, lastVisited = :lastVisited WHERE id = :id")
    suspend fun updateVisit(id: String, briefComment: String, notes: String?, lastVisited: String)

    @Query("UPDATE households SET assignedTo = :assignee WHERE id = :id")
    suspend fun assign(id: String, assignee: String?)

    @Query("SELECT * FROM households WHERE id = :id LIMIT 1")
    suspend fun householdById(id: String): HouseholdEntity?

    @Query("SELECT COUNT(*) FROM households")
    suspend fun householdRowCount(): Int
}

@Database(
    entities = [HouseholdEntity::class, PendingSyncEntity::class, PendingAppendEntity::class],
    version = 3
)
@TypeConverters
abstract class OutreachDatabase : RoomDatabase() {
    abstract fun dao(): OutreachDao

    companion object {
        fun create(context: Context): OutreachDatabase =
            Room.databaseBuilder(context, OutreachDatabase::class.java, "outreach.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}

class AppConfigStore(context: Context) {
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("app-config.preferences_pb") }
    )
    private val spreadsheetKey = stringPreferencesKey("spreadsheet_id")
    private val spreadsheetTitleKey = stringPreferencesKey("spreadsheet_title")
    private val tabsKey = stringPreferencesKey("selected_tabs")
    private val mapBriefFilterKey = stringPreferencesKey("map_brief_filter")
    private val mapDateStartKey = stringPreferencesKey("map_date_start_iso")
    private val mapDateEndKey = stringPreferencesKey("map_date_end_iso")
    private val mapQuickRangeKey = stringPreferencesKey("map_quick_range")
    private val mapOldestRecordsLimitKey = stringPreferencesKey("map_oldest_records_limit")

    val config: Flow<AppConfig> = dataStore.data.map { prefs ->
        AppConfig(
            spreadsheetId = prefs[spreadsheetKey].orEmpty(),
            spreadsheetTitle = prefs[spreadsheetTitleKey].takeUnless { it.isNullOrBlank() },
            selectedTabs = prefs[tabsKey].orEmpty().split(",").filter { it.isNotBlank() }.toSet(),
            mapBriefCommentFilter = prefs[mapBriefFilterKey].orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet(),
            mapDateStartIso = prefs[mapDateStartKey].takeUnless { it.isNullOrBlank() },
            mapDateEndIso = prefs[mapDateEndKey].takeUnless { it.isNullOrBlank() },
            mapQuickRange = prefs[mapQuickRangeKey].orEmpty().ifBlank { "All" },
            mapOldestRecordsLimit = prefs[mapOldestRecordsLimitKey]
                ?.trim()
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
        )
    }

    suspend fun update(config: AppConfig) {
        dataStore.edit { prefs ->
            prefs[spreadsheetKey] = config.spreadsheetId
            val title = config.spreadsheetTitle?.trim().orEmpty()
            if (title.isEmpty()) {
                prefs.remove(spreadsheetTitleKey)
            } else {
                prefs[spreadsheetTitleKey] = title
            }
            prefs[tabsKey] = config.selectedTabs.joinToString(",")
            prefs[mapBriefFilterKey] = config.mapBriefCommentFilter.joinToString(",")
            prefs[mapDateStartKey] = config.mapDateStartIso.orEmpty()
            prefs[mapDateEndKey] = config.mapDateEndIso.orEmpty()
            prefs[mapQuickRangeKey] = config.mapQuickRange
            val oldestLimit = config.mapOldestRecordsLimit
            if (oldestLimit == null || oldestLimit <= 0) {
                prefs.remove(mapOldestRecordsLimitKey)
            } else {
                prefs[mapOldestRecordsLimitKey] = oldestLimit.toString()
            }
        }
    }
}

interface SheetsApi {
    suspend fun listTabs(spreadsheetId: String): List<String>
    /** Spreadsheet document title (file name in Drive / Sheets). */
    suspend fun getSpreadsheetTitle(spreadsheetId: String): String?
    suspend fun fetchRows(spreadsheetId: String, tabName: String): List<SpreadsheetRowInput>
    suspend fun updateVisit(spreadsheetId: String, update: VisitUpdate)
    suspend fun appendHouseholdRow(
        spreadsheetId: String,
        tabName: String,
        name: String,
        streetAddress: String,
        neighborhood: String
    ): AppendHouseholdResult?
    suspend fun validateRequiredHeaders(spreadsheetId: String, tabName: String): Boolean
    /** Allowed brief-comment labels from the `keys` tab [Name] column; empty if tab/column missing. */
    suspend fun fetchBriefCommentPresets(spreadsheetId: String): List<String>
}

class StubSheetsApi : SheetsApi {
    override suspend fun listTabs(spreadsheetId: String): List<String> = listOf("60618", "60657")
    override suspend fun getSpreadsheetTitle(spreadsheetId: String): String? = "Stub spreadsheet"
    override suspend fun fetchRows(spreadsheetId: String, tabName: String): List<SpreadsheetRowInput> = emptyList()
    override suspend fun updateVisit(spreadsheetId: String, update: VisitUpdate) = Unit
    override suspend fun appendHouseholdRow(
        spreadsheetId: String,
        tabName: String,
        name: String,
        streetAddress: String,
        neighborhood: String
    ): AppendHouseholdResult? = AppendHouseholdResult(rowNumber = 2)
    override suspend fun validateRequiredHeaders(spreadsheetId: String, tabName: String): Boolean = true
    override suspend fun fetchBriefCommentPresets(spreadsheetId: String): List<String> = listOf(
        "Not home",
        "Left message",
        "Receptive",
        "Do not visit",
        "Moved",
        "Dawat saath",
        "Other"
    )
}

interface GoogleAccessTokenProvider {
    suspend fun getAccessToken(vararg scopes: String): String?
}

class GoogleSheetsApi(
    private val tokenProvider: GoogleAccessTokenProvider
) : SheetsApi {
    override suspend fun getSpreadsheetTitle(spreadsheetId: String): String? {
        val response = request(
            method = "GET",
            path = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId?fields=properties.title"
        ) ?: return null
        return response.optJSONObject("properties")
            ?.optString("title")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    override suspend fun listTabs(spreadsheetId: String): List<String> {
        val response = request(
            method = "GET",
            path = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId?fields=sheets.properties.title"
        ) ?: return emptyList()
        val sheets = response.optJSONArray("sheets") ?: JSONArray()
        return buildList {
            for (i in 0 until sheets.length()) {
                val title = sheets.optJSONObject(i)
                    ?.optJSONObject("properties")
                    ?.optString("title")
                    ?.trim()
                    .orEmpty()
                if (title.isNotBlank()) add(title)
            }
        }
    }

    override suspend fun fetchRows(spreadsheetId: String, tabName: String): List<SpreadsheetRowInput> {
        val encodedRange = java.net.URLEncoder.encode("$tabName!A:Z", "UTF-8")
        val response = request(
            method = "GET",
            path = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedRange"
        ) ?: return emptyList()
        val values = response.optJSONArray("values") ?: JSONArray()
        if (values.length() <= 1) return emptyList()
        val headers = values.optJSONArray(0)?.toStringList().orEmpty()
        val canonicalByIndex = headers.map { canonicalHeaderName(it) }
        val index = canonicalByIndex
            .mapIndexedNotNull { idx, key -> key?.let { it to idx } }
            .toMap()

        fun at(row: JSONArray, key: String): String? {
            val idx = index[key] ?: return null
            return if (idx < row.length()) row.optString(idx, null) else null
        }

        return buildList {
            for (i in 1 until values.length()) {
                val row = values.optJSONArray(i) ?: continue
                add(
                    SpreadsheetRowInput(
                        briefComments = at(row, "brief comments"),
                        lastVisited = at(row, "last visited"),
                        name = at(row, "name"),
                        streetAddress = at(row, "street address"),
                        neighborhood = at(row, "neighborhood"),
                        notes = at(row, "notes")
                    )
                )
            }
        }
    }

    override suspend fun updateVisit(spreadsheetId: String, update: VisitUpdate) {
        val sheetName = update.sheetName ?: return
        val rowNumber = update.rowNumber ?: return
        if (rowNumber < 1) return
        val headerMap = headerIndex(spreadsheetId, sheetName)
        val briefCol = headerMap["brief comments"] ?: return
        val lastVisitedCol = headerMap["last visited"] ?: return
        val notesCol = headerMap["notes"] ?: return
        val body = JSONObject(
            mapOf(
                "valueInputOption" to "USER_ENTERED",
                "data" to JSONArray(
                    listOf(
                        valueRange(sheetName, rowNumber, briefCol, update.briefComment),
                        valueRange(sheetName, rowNumber, lastVisitedCol, update.lastVisitedIsoDate),
                        valueRange(sheetName, rowNumber, notesCol, update.notes.orEmpty())
                    )
                )
            )
        )
        request(
            method = "POST",
            path = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchUpdate",
            body = body
        )
    }

    override suspend fun appendHouseholdRow(
        spreadsheetId: String,
        tabName: String,
        name: String,
        streetAddress: String,
        neighborhood: String
    ): AppendHouseholdResult? {
        val headers = fetchHeaderRowCells(spreadsheetId, tabName)
        if (headers.isEmpty()) return null
        val row = MutableList(headers.size) { "" }
        headers.forEachIndexed { idx, header ->
            when (canonicalHeaderName(header)) {
                "name" -> row[idx] = name
                "street address" -> row[idx] = streetAddress
                "neighborhood" -> row[idx] = neighborhood
                "brief comments", "last visited", "notes" -> { }
                else -> { }
            }
        }
        val encodedRange = java.net.URLEncoder.encode("$tabName!A:Z", "UTF-8")
        val path =
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedRange:append" +
                "?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS"
        val inner = JSONArray()
        row.forEach { inner.put(it) }
        val values = JSONArray().put(inner)
        val body = JSONObject(mapOf("values" to values))
        val response = request(method = "POST", path = path, body = body) ?: return null
        val updates = response.optJSONObject("updates") ?: return null
        val updatedRange = updates.optString("updatedRange").trim()
        if (updatedRange.isBlank()) return null
        val rowNum = parseSheetRowFromUpdatedRange(updatedRange) ?: return null
        return AppendHouseholdResult(rowNumber = rowNum)
    }

    override suspend fun fetchBriefCommentPresets(spreadsheetId: String): List<String> {
        val encodedRange = java.net.URLEncoder.encode("${GoogleSheetsApi.KEYS_TAB_NAME}!A:Z", "UTF-8")
        val response = request(
            method = "GET",
            path = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedRange"
        ) ?: return emptyList()
        val values = response.optJSONArray("values") ?: return emptyList()
        if (values.length() == 0) return emptyList()
        val headers = values.optJSONArray(0)?.toStringList().orEmpty()
        val canonicalByIndex = headers.map { canonicalHeaderName(it) }
        val nameIdx = canonicalByIndex.indexOfFirst { it == "name" }
        if (nameIdx < 0) return emptyList()
        val orderedUnique = LinkedHashSet<String>()
        for (i in 1 until values.length()) {
            val row = values.optJSONArray(i) ?: continue
            if (nameIdx < row.length()) {
                val cell = row.optString(nameIdx).trim()
                if (cell.isNotBlank()) orderedUnique.add(cell)
            }
        }
        return orderedUnique.toList()
    }

    override suspend fun validateRequiredHeaders(spreadsheetId: String, tabName: String): Boolean {
        val encodedRange = java.net.URLEncoder.encode("$tabName!1:1", "UTF-8")
        val response = request(
            method = "GET",
            path = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedRange"
        ) ?: return false
        val rows = response.optJSONArray("values") ?: return false
        val headers = rows.optJSONArray(0)
            ?.toStringList()
            ?.mapNotNull { canonicalHeaderName(it) }
            ?.toSet()
            .orEmpty()
        val required = setOf("brief comments", "last visited", "name", "street address", "neighborhood", "notes")
        val missing = required.filterNot { it in headers }
        val valid = missing.isEmpty()
        return valid
    }

    private suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject? {
        val token = tokenProvider.getAccessToken(
            "https://www.googleapis.com/auth/spreadsheets",
            "https://www.googleapis.com/auth/drive.file"
        ) ?: return null
        return withContext(Dispatchers.IO) {
            val connection = URL(path).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            if (body != null) {
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }
            }
            val code = connection.responseCode
            val payload = if (code in 200..299) {
                BufferedReader(connection.inputStream.reader()).use { it.readText() }
            } else {
                return@withContext null
            }
            if (payload.isBlank()) null else JSONObject(payload)
        }
    }

    private suspend fun fetchHeaderRowCells(spreadsheetId: String, tabName: String): List<String> {
        val encodedRange = java.net.URLEncoder.encode("$tabName!1:1", "UTF-8")
        val response = request(
            method = "GET",
            path = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedRange"
        ) ?: return emptyList()
        val values = response.optJSONArray("values") ?: return emptyList()
        if (values.length() == 0) return emptyList()
        return values.optJSONArray(0)?.toStringList().orEmpty()
    }

    private suspend fun headerIndex(spreadsheetId: String, tabName: String): Map<String, String> {
        val encodedRange = java.net.URLEncoder.encode("$tabName!1:1", "UTF-8")
        val response = request(
            method = "GET",
            path = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedRange"
        ) ?: return emptyMap()
        val rows = response.optJSONArray("values") ?: return emptyMap()
        val headers = rows.optJSONArray(0)?.toStringList().orEmpty()
        return headers.mapIndexedNotNull { idx, value ->
            canonicalHeaderName(value)?.let { it to columnName(idx + 1) }
        }.toMap()
    }

    private fun valueRange(sheetName: String, rowNumber: Int, columnName: String, value: String): JSONObject {
        return JSONObject(
            mapOf(
                "range" to "$sheetName!$columnName$rowNumber",
                "values" to JSONArray(listOf(JSONArray(listOf(value))))
            )
        )
    }

    private fun columnName(index1Based: Int): String {
        var i = index1Based
        var name = ""
        while (i > 0) {
            val rem = (i - 1) % 26
            name = ('A'.code + rem).toChar() + name
            i = (i - 1) / 26
        }
        return name
    }

    private fun JSONArray.toStringList(): List<String> =
        buildList {
            for (i in 0 until length()) add(optString(i).orEmpty())
        }

    private fun canonicalHeaderName(raw: String): String? {
        val normalized = raw.trim().lowercase().replace(Regex("\\s+"), " ")
        return when (normalized) {
            "brief comments", "brief comment" -> "brief comments"
            "last visited", "last visit" -> "last visited"
            "name" -> "name"
            "street address" -> "street address"
            "neighborhood" -> "neighborhood"
            "notes" -> "notes"
            else -> null
        }
    }

    companion object {
        const val KEYS_TAB_NAME: String = "keys"
    }
}

/** Parses starting row from a Sheets A1 range like `Tab!A10:F10` or `'My Tab'!B2:E2`. */
internal fun parseSheetRowFromUpdatedRange(updatedRange: String): Int? {
    val bang = updatedRange.lastIndexOf('!')
    val afterBang = if (bang >= 0) updatedRange.substring(bang + 1) else updatedRange
    val m = Regex("^([A-Za-z]+)(\\d+)").find(afterBang.trim()) ?: return null
    return m.groupValues[2].toIntOrNull()
}

class GeocodingService(
    private val context: Context
) {
    suspend fun geocodeAddress(address: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val latLng = runCatching {
            val geocoder = Geocoder(context)
            val result = geocoder.getFromLocationName(address, 1).orEmpty().firstOrNull()
            result?.latitude?.let { lat -> result.longitude.let { lng -> lat to lng } }
        }.getOrNull()
        latLng
    }
}

class OutreachRepository(
    private val dao: OutreachDao,
    private val configStore: AppConfigStore,
    private val sheetsApi: SheetsApi,
    private val geocoder: GeocodingService
) {
    val households: Flow<List<HouseholdRecord>> = dao.observeHouseholds().map { rows ->
        rows.map {
            HouseholdRecord(
                id = it.id,
                name = it.name,
                streetAddress = it.streetAddress,
                neighborhood = it.neighborhood,
                briefComment = it.briefComment,
                lastVisited = it.lastVisited,
                notes = it.notes,
                source = SourceMetadata(it.sheetName, it.rowNumber),
                raw = RawHouseholdRow(),
                latitude = it.latitude,
                longitude = it.longitude,
                assignedTo = it.assignedTo
            )
        }
    }

    val config: Flow<AppConfig> = configStore.config

    suspend fun setConfig(config: AppConfig) = configStore.update(config)

    suspend fun availableTabs(spreadsheetId: String): List<String> = sheetsApi.listTabs(spreadsheetId)

    suspend fun fetchSpreadsheetTitle(spreadsheetId: String): String? =
        sheetsApi.getSpreadsheetTitle(spreadsheetId)

    suspend fun loadBriefCommentPresets(): List<String> {
        val cfg = configStore.config.first()
        if (cfg.spreadsheetId.isBlank()) return emptyList()
        return sheetsApi.fetchBriefCommentPresets(cfg.spreadsheetId)
    }

    suspend fun isSheetSchemaValid(spreadsheetId: String, selectedTabs: Set<String>): Boolean {
        if (spreadsheetId.isBlank() || selectedTabs.isEmpty()) return false
        val perTabResults = selectedTabs.associateWith { sheetsApi.validateRequiredHeaders(spreadsheetId, it) }
        return perTabResults.values.all { it }
    }

    suspend fun syncFromSheet() {
        val cfg = configStore.config.first()
        if (cfg.spreadsheetId.isBlank() || cfg.selectedTabs.isEmpty()) return
        // #region agent log
        val householdRowCountBefore = dao.householdRowCount()
        agentDebugLog(
            hypothesisId = "A",
            location = "OutreachRepository.syncFromSheet:entry",
            message = "sync start",
            data = JSONObject().apply {
                put("spreadsheetId", cfg.spreadsheetId)
                put("selectedTabs", cfg.selectedTabs.joinToString(","))
                put("householdRowCountBefore", householdRowCountBefore)
            }
        )
        // #endregion
        val entities = mutableListOf<HouseholdEntity>()
        cfg.selectedTabs.forEach { tab ->
            val rows = sheetsApi.fetchRows(cfg.spreadsheetId, tab)
            rows.forEachIndexed { index, row ->
                val parsed = parseSpreadsheetRow(row, SourceMetadata(tab, index + 2))
                val latLng = geocoder.geocodeAddress(parsed.streetAddress)
                entities += HouseholdEntity(
                    id = parsed.id,
                    name = parsed.name,
                    streetAddress = parsed.streetAddress,
                    neighborhood = parsed.neighborhood,
                    briefComment = parsed.briefComment,
                    lastVisited = parsed.lastVisited,
                    notes = parsed.notes,
                    sheetName = tab,
                    rowNumber = parsed.source.rowNumber,
                    latitude = latLng?.first,
                    longitude = latLng?.second,
                    assignedTo = null
                )
            }
        }
        dao.upsertHouseholds(entities)
        // #region agent log
        val householdRowCountAfter = dao.householdRowCount()
        agentDebugLog(
            hypothesisId = "A",
            location = "OutreachRepository.syncFromSheet:afterUpsert",
            message = "sync upsert complete",
            data = JSONObject().apply {
                put("spreadsheetId", cfg.spreadsheetId)
                put("entitiesBuilt", entities.size)
                put("householdRowCountAfter", householdRowCountAfter)
            }
        )
        // #endregion
    }

    suspend fun saveVisitUpdate(update: VisitUpdate) {
        val household = dao.householdById(update.householdId)
        dao.updateVisit(update.householdId, update.briefComment, update.notes, update.lastVisitedIsoDate)
        dao.insertPending(
            PendingSyncEntity(
                householdId = update.householdId,
                briefComment = update.briefComment,
                notes = update.notes,
                lastVisitedIsoDate = update.lastVisitedIsoDate,
                sheetName = update.sheetName ?: household?.sheetName,
                rowNumber = update.rowNumber ?: household?.rowNumber
            )
        )
    }

    /**
     * Adds a new household row to the sheet when online, or queues [PendingAppendEntity] when append fails.
     * Local row uses [HouseholdEntity.rowNumber] `-1` until the sheet row exists.
     */
    suspend fun addHousehold(
        tabName: String,
        name: String,
        streetAddress: String,
        neighborhood: String
    ): String? {
        val n = name.trim()
        val s = streetAddress.trim()
        val nh = neighborhood.trim()
        if (n.isBlank() || s.isBlank()) return null
        val cfg = configStore.config.first()
        if (cfg.spreadsheetId.isBlank()) return null

        val id = createHouseholdId(n, s, nh)
        val latLng = geocoder.geocodeAddress(s)
        val rowInput = SpreadsheetRowInput(
            briefComments = "",
            lastVisited = null,
            name = n,
            streetAddress = s,
            neighborhood = nh,
            notes = null
        )
        val parsed = parseSpreadsheetRow(rowInput, SourceMetadata(tabName, 2))
        val appendResult = runCatching {
            sheetsApi.appendHouseholdRow(cfg.spreadsheetId, tabName, n, s, nh)
        }.getOrNull()
        val rowNum = appendResult?.rowNumber?.takeIf { it >= 1 } ?: -1
        val entity = HouseholdEntity(
            id = id,
            name = parsed.name,
            streetAddress = parsed.streetAddress,
            neighborhood = parsed.neighborhood,
            briefComment = parsed.briefComment,
            lastVisited = parsed.lastVisited,
            notes = parsed.notes,
            sheetName = tabName,
            rowNumber = if (rowNum >= 1) rowNum else -1,
            latitude = latLng?.first,
            longitude = latLng?.second,
            assignedTo = null
        )
        dao.upsertHouseholds(listOf(entity))
        if (rowNum < 1) {
            dao.insertPendingAppend(
                PendingAppendEntity(
                    householdId = id,
                    name = n,
                    streetAddress = s,
                    neighborhood = nh,
                    sheetName = tabName
                )
            )
        }
        return id
    }

    suspend fun assignHousehold(householdId: String, assignee: String?) = dao.assign(householdId, assignee)

    suspend fun flushPendingSync() {
        val cfg = configStore.config.first()
        if (cfg.spreadsheetId.isBlank()) return
        dao.pendingAppends().forEach { pending ->
            val result = runCatching {
                sheetsApi.appendHouseholdRow(
                    cfg.spreadsheetId,
                    pending.sheetName,
                    pending.name,
                    pending.streetAddress,
                    pending.neighborhood
                )
            }.getOrNull()
            if (result != null && result.rowNumber >= 1) {
                dao.updateHouseholdRowNumber(pending.householdId, result.rowNumber)
                dao.deletePendingAppend(pending.householdId)
            }
        }
        dao.pendingSync().forEach { pending ->
            val household = dao.householdById(pending.householdId)
            val rowNumber = when {
                household != null && household.rowNumber >= 1 -> household.rowNumber
                pending.rowNumber != null && pending.rowNumber >= 1 -> pending.rowNumber
                else -> null
            }
            if (rowNumber == null) return@forEach
            sheetsApi.updateVisit(
                cfg.spreadsheetId,
                VisitUpdate(
                    householdId = pending.householdId,
                    briefComment = pending.briefComment,
                    notes = pending.notes,
                    lastVisitedIsoDate = pending.lastVisitedIsoDate,
                    sheetName = pending.sheetName ?: household?.sheetName,
                    rowNumber = rowNumber
                )
            )
            dao.deletePending(pending.id)
        }
    }

    fun pickNextTarget(items: List<HouseholdRecord>): HouseholdRecord? {
        val now = LocalDate.now()
        return items.minByOrNull { item ->
            item.lastVisited?.let { LocalDate.parse(it).toEpochDay() } ?: (now.toEpochDay() - 100000)
        }
    }
}

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = OutreachServiceLocator.repository ?: return Result.retry()
        return runCatching {
            repository.flushPendingSync()
            repository.syncFromSheet()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

class CollaborationRepository(
    private val firestore: FirebaseFirestore
) {
    suspend fun publishPresence(userId: String, tabName: String) {
        firestore.collection("presence").document(userId).set(
            mapOf(
                "tabName" to tabName,
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    suspend fun publishActivity(event: CollaborationEvent) {
        firestore.collection("activity").add(
            mapOf(
                "userId" to event.userId,
                "householdId" to event.householdId,
                "tabName" to event.tabName,
                "type" to event.type,
                "epochMillis" to event.epochMillis
            )
        ).await()
    }
}

object OutreachServiceLocator {
    @Volatile
    var repository: OutreachRepository? = null
        private set

    @Volatile
    var collaborationRepository: CollaborationRepository? = null
        private set

    fun initialize(repository: OutreachRepository, collaborationRepository: CollaborationRepository) {
        this.repository = repository
        this.collaborationRepository = collaborationRepository
    }
}
