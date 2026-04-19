package org.outreach.app

import android.app.Application
import android.content.Intent
import androidx.work.Configuration
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.maps.MapsInitializer
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.outreach.app.testing.TestRuntime
import org.outreach.core.data.AppConfigStore
import org.outreach.core.data.CollaborationRepository
import org.outreach.core.data.GeocodingService
import org.outreach.core.data.GoogleAccessTokenProvider
import org.outreach.core.data.GoogleSheetsApi
import org.outreach.core.data.OutreachDatabase
import org.outreach.core.data.OutreachRepository
import org.outreach.core.data.OutreachServiceLocator

class OutreachApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        MapsInitializer.initialize(applicationContext)
        if (TestRuntime.skipStartupSideEffects && OutreachServiceLocator.hasTestOverrides()) {
            return
        }
        FirebaseApp.initializeApp(this)

        val database = OutreachDatabase.create(this)
        val configStore = AppConfigStore(this)
        val tokenProvider = PlayServicesTokenProvider(this)
        val sheetsApi = GoogleSheetsApi(tokenProvider)
        val repository = OutreachRepository(
            dao = database.dao(),
            configStore = configStore,
            sheetsApi = sheetsApi,
            geocoder = GeocodingService(this)
        )
        val collaborationRepository = CollaborationRepository(FirebaseFirestore.getInstance())
        OutreachServiceLocator.initialize(repository, collaborationRepository)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}

private class PlayServicesTokenProvider(
    private val app: Application
) : GoogleAccessTokenProvider {
    override suspend fun getAccessToken(vararg scopes: String): String? = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(app)?.account
            ?: return@withContext null
        val scopeExpr = "oauth2:${scopes.joinToString(" ")}"
        runCatching { GoogleAuthUtil.getToken(app, account, scopeExpr) }.fold(
            onSuccess = { it },
            onFailure = { e ->
                if (e is UserRecoverableAuthException) {
                    val recoverIntent = e.intent
                    if (recoverIntent != null) {
                        runCatching {
                            app.startActivity(
                                recoverIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
                null
            },
        )
    }
}
