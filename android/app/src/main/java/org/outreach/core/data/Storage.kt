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
import org.outreach.app.BuildConfig
import org.outreach.core.debug.AgentDebugLogger
import org.outreach.core.model.AppConfig
import org.outreach.core.model.CollaborationEvent
import org.outreach.core.model.HouseholdRecord
import org.outreach.core.model.RawHouseholdRow
import org.outreach.core.model.SourceMetadata
import org.outreach.core.model.SpreadsheetRowInput
import org.outreach.core.model.VisitUpdate
import org.outreach.core.model.parseSpreadsheetRow
import org.json.JSONArray
import org.json.JSONObject
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

@Dao
interface OutreachDao {
    @Query("SELECT * FROM households")
    fun observeHouseholds(): Flow<List<HouseholdEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHouseholds(items: List<HouseholdEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPending(sync: PendingSyncEntity)

    @Query("SELECT * FROM pending_sync ORDER BY id ASC")
    suspend fun pendingSync(): List<PendingSyncEntity>

    @Query("DELETE FROM pending_sync WHERE id = :id")
    suspend fun deletePending(id: Long)

    @Query("UPDATE households SET briefComment = :briefComment, notes = :notes, lastVisited = :lastVisited WHERE id = :id")
    suspend fun updateVisit(id: String, briefComment: String, notes: String?, lastVisited: String)

    @Query("UPDATE households SET assignedTo = :assignee WHERE id = :id")
    suspend fun assign(id: String, assignee: String?)

    @Query("SELECT * FROM households WHERE id = :id LIMIT 1")
    suspend fun householdById(id: String): HouseholdEntity?
}

@Database(entities = [HouseholdEntity::class, PendingSyncEntity::class], version = 2)
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
    private val tabsKey = stringPreferencesKey("selected_tabs")

    val config: Flow<AppConfig> = dataStore.data.map { prefs ->
        AppConfig(
            spreadsheetId = prefs[spreadsheetKey].orEmpty(),
            selectedTabs = prefs[tabsKey].orEmpty().split(",").filter { it.isNotBlank() }.toSet()
        )
    }

    suspend fun update(config: AppConfig) {
        dataStore.edit { prefs ->
            prefs[spreadsheetKey] = config.spreadsheetId
            prefs[tabsKey] = config.selectedTabs.joinToString(",")
        }
    }
}

interface SheetsApi {
    suspend fun listTabs(spreadsheetId: String): List<String>
    suspend fun fetchRows(spreadsheetId: String, tabName: String): List<SpreadsheetRowInput>
    suspend fun updateVisit(spreadsheetId: String, update: VisitUpdate)
    suspend fun validateRequiredHeaders(spreadsheetId: String, tabName: String): Boolean
    /** Allowed brief-comment labels from the `keys` tab [Name] column; empty if tab/column missing. */
    suspend fun fetchBriefCommentPresets(spreadsheetId: String): List<String>
}

class StubSheetsApi : SheetsApi {
    override suspend fun listTabs(spreadsheetId: String): List<String> = listOf("60618", "60657")
    override suspend fun fetchRows(spreadsheetId: String, tabName: String): List<SpreadsheetRowInput> = emptyList()
    override suspend fun updateVisit(spreadsheetId: String, update: VisitUpdate) = Unit
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
        // #region agent log
        if (BuildConfig.DEBUG) {
            AgentDebugLogger.log(
                runId = "sheet-debug",
                hypothesisId = "S3",
                location = "Storage.kt:validateRequiredHeaders",
                message = "Validated tab headers",
                data = mapOf(
                    "spreadsheetIdSuffix" to spreadsheetId.takeLast(6),
                    "tabName" to tabName,
                    "headersCount" to headers.size,
                    "missingHeaders" to missing,
                    "isValid" to valid
                )
            )
        }
        // #endregion
        return valid
    }

    private suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject? {
        val token = tokenProvider.getAccessToken(
            "https://www.googleapis.com/auth/spreadsheets",
            "https://www.googleapis.com/auth/drive.file"
        ) ?: run {
            // #region agent log
            if (BuildConfig.DEBUG) {
                AgentDebugLogger.log(
                    runId = "sheet-debug",
                    hypothesisId = "S2",
                    location = "Storage.kt:request",
                    message = "Missing Google access token",
                    data = mapOf(
                        "method" to method,
                        "path" to path.substringBefore("?")
                    )
                )
            }
            // #endregion
            return null
        }
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
                val errorPayload = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }.getOrDefault("")
                // #region agent log
                if (BuildConfig.DEBUG) {
                    AgentDebugLogger.log(
                        runId = "sheet-debug",
                        hypothesisId = "S4",
                        location = "Storage.kt:request",
                        message = "Sheets API request failed",
                        data = mapOf(
                            "method" to method,
                            "path" to path.substringBefore("?"),
                            "code" to code,
                            "errorSnippet" to errorPayload.take(300)
                        )
                    )
                }
                // #endregion
                return@withContext null
            }
            // #region agent log
            if (BuildConfig.DEBUG && path.contains("/values/")) {
                AgentDebugLogger.log(
                    runId = "sheet-debug",
                    hypothesisId = "S1",
                    location = "Storage.kt:request",
                    message = "Sheets API values request succeeded",
                    data = mapOf(
                        "method" to method,
                        "path" to path.substringBefore("?"),
                        "code" to code,
                        "payloadSize" to payload.length
                    )
                )
            }
            // #endregion
            if (payload.isBlank()) null else JSONObject(payload)
        }
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

class GeocodingService(
    private val context: Context
) {
    suspend fun geocodeAddress(address: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val latLng = runCatching {
            val geocoder = Geocoder(context)
            val result = geocoder.getFromLocationName(address, 1).orEmpty().firstOrNull()
            result?.latitude?.let { lat -> result.longitude.let { lng -> lat to lng } }
        }.getOrNull()
        if (BuildConfig.DEBUG) {
            // #region agent log
            AgentDebugLogger.log(
                runId = "run14",
                hypothesisId = "H54",
                location = "Storage.kt:GeocodingService.geocodeAddress",
                message = "Geocoding attempted for household address",
                data = mapOf(
                    "addressPrefix" to address.take(48),
                    "resolved" to (latLng != null)
                )
            )
            // #endregion
        }
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

    suspend fun loadBriefCommentPresets(): List<String> {
        val cfg = configStore.config.first()
        if (cfg.spreadsheetId.isBlank()) return emptyList()
        return sheetsApi.fetchBriefCommentPresets(cfg.spreadsheetId)
    }

    suspend fun isSheetSchemaValid(spreadsheetId: String, selectedTabs: Set<String>): Boolean {
        // #region agent log
        if (BuildConfig.DEBUG) {
            AgentDebugLogger.log(
                runId = "sheet-debug",
                hypothesisId = "S1",
                location = "Storage.kt:isSheetSchemaValid",
                message = "Starting schema validation",
                data = mapOf(
                    "spreadsheetIdSuffix" to spreadsheetId.takeLast(6),
                    "tabsCount" to selectedTabs.size,
                    "tabs" to selectedTabs.toList()
                )
            )
        }
        // #endregion
        if (spreadsheetId.isBlank() || selectedTabs.isEmpty()) return false
        val perTabResults = selectedTabs.associateWith { sheetsApi.validateRequiredHeaders(spreadsheetId, it) }
        val valid = perTabResults.values.all { it }
        // #region agent log
        if (BuildConfig.DEBUG) {
            AgentDebugLogger.log(
                runId = "sheet-debug",
                hypothesisId = "S5",
                location = "Storage.kt:isSheetSchemaValid",
                message = "Finished schema validation",
                data = mapOf(
                    "perTabResults" to perTabResults,
                    "isValid" to valid
                )
            )
        }
        // #endregion
        return valid
    }

    suspend fun syncFromSheet() {
        val cfg = configStore.config.first()
        // #region agent log
        if (BuildConfig.DEBUG) {
            AgentDebugLogger.log(
                runId = "run1",
                hypothesisId = "H1",
                location = "Storage.kt:syncFromSheet:configSnapshot",
                message = "Sync started with config snapshot",
                data = mapOf(
                    "spreadsheetIdLength" to cfg.spreadsheetId.length,
                    "spreadsheetIdPrefix" to cfg.spreadsheetId.take(24),
                    "spreadsheetIdHasSlash" to cfg.spreadsheetId.contains("/"),
                    "tabsCount" to cfg.selectedTabs.size,
                    "tabs" to cfg.selectedTabs.toList()
                )
            )
        }
        // #endregion
        if (cfg.spreadsheetId.isBlank() || cfg.selectedTabs.isEmpty()) return
        val entities = mutableListOf<HouseholdEntity>()
        cfg.selectedTabs.forEach { tab ->
            val rows = sheetsApi.fetchRows(cfg.spreadsheetId, tab)
            var geocodedCount = 0
            var ungeocodedCount = 0
            // #region agent log
            if (BuildConfig.DEBUG) {
                AgentDebugLogger.log(
                    runId = "run1",
                    hypothesisId = "H4",
                    location = "Storage.kt:syncFromSheet:tabResult",
                    message = "Fetched rows for selected tab",
                    data = mapOf(
                        "tab" to tab,
                        "rowsCount" to rows.size
                    )
                )
            }
            // #endregion
            rows.forEachIndexed { index, row ->
                val parsed = parseSpreadsheetRow(row, SourceMetadata(tab, index + 2))
                val latLng = geocoder.geocodeAddress(parsed.streetAddress)
                if (latLng == null) ungeocodedCount++ else geocodedCount++
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
            if (BuildConfig.DEBUG) {
                // #region agent log
                AgentDebugLogger.log(
                    runId = "run13",
                    hypothesisId = "H51",
                    location = "Storage.kt:syncFromSheet:geocodeSummary",
                    message = "Tab geocode summary computed",
                    data = mapOf(
                        "tab" to tab,
                        "rowsCount" to rows.size,
                        "geocodedCount" to geocodedCount,
                        "ungeocodedCount" to ungeocodedCount
                    )
                )
                // #endregion
            }
        }
        dao.upsertHouseholds(entities)
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

    suspend fun assignHousehold(householdId: String, assignee: String?) = dao.assign(householdId, assignee)

    suspend fun flushPendingSync() {
        val cfg = configStore.config.first()
        if (cfg.spreadsheetId.isBlank()) return
        dao.pendingSync().forEach { pending ->
            sheetsApi.updateVisit(
                cfg.spreadsheetId,
                VisitUpdate(
                    householdId = pending.householdId,
                    briefComment = pending.briefComment,
                    notes = pending.notes,
                    lastVisitedIsoDate = pending.lastVisitedIsoDate,
                    sheetName = pending.sheetName,
                    rowNumber = pending.rowNumber
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
            repository.syncFromSheet()
            repository.flushPendingSync()
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
