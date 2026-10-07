package com.pashe.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.pashe.app.domain.AppMode
import com.pashe.app.ui.PasheNavHost
import com.pashe.app.ui.theme.PasheTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Bengali is the default even on phones set to English; parent mode is always Bengali.
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty || (Graph.store.appMode == AppMode.PARENT && locales[0]?.language != "bn")) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("bn"))
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PasheTheme {
                if (!Graph.firebaseConfigured()) {
                    Text(
                        "Firebase is not configured. Add app/google-services.json and rebuild (see README).",
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                    )
                } else {
                    PasheNavHost(openParentId = intent.getStringExtra(EXTRA_PARENT_ID))
                }
            }
        }
    }

    companion object {
        const val EXTRA_PARENT_ID = "parentId"
    }
}
