package com.pashe.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.pashe.app.auth.AuthManager
import com.pashe.app.data.ChildRepository
import com.pashe.app.data.LocalStore
import com.pashe.app.data.ParentDeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled dependency container; the app is small enough not to need a DI framework. */
object Graph {
    lateinit var context: Context
        private set

    /** Region the Cloud Functions are deployed to; keep in sync with functions/src/config.ts. */
    const val FUNCTIONS_REGION = "asia-south1"

    const val PRIVACY_POLICY_URL = "https://example.com/pashe/privacy" // TODO: replace before release

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val store by lazy { LocalStore(context) }
    val auth by lazy { AuthManager() }
    val childRepo by lazy { ChildRepository() }
    val parentRepo by lazy { ParentDeviceRepository(context, store) }

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    /** False when the app was built without google-services.json. */
    fun firebaseConfigured(): Boolean = FirebaseApp.getApps(context).isNotEmpty()
}
