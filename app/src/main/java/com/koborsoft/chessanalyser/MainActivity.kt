package com.koborsoft.chessanalyser

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.koborsoft.chessanalyser.ui.GameScreen
import java.util.Locale

/** Az app matt-kék színvilága (Chess Analyser arculat). */
private val BlueLightScheme = lightColorScheme(
    primary = Color(0xFF44607C),
    secondary = Color(0xFF5C7A99),
    tertiary = Color(0xFF7C96B0),
    primaryContainer = Color(0xFFD9E2EC),
    surface = Color(0xFFF4F7FA),
    background = Color(0xFFF4F7FA),
)

private val BlueDarkScheme = darkColorScheme(
    primary = Color(0xFF9DB8D2),
    secondary = Color(0xFF8AA5BF),
    tertiary = Color(0xFF7C96B0),
    primaryContainer = Color(0xFF33475C),
)

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    /** Kézi nyelvválasztásnál a kiválasztott nyelvvel indul az Activity. */
    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, "") ?: ""
        if (lang.isEmpty()) {
            super.attachBaseContext(newBase)
        } else {
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(Locale(lang))
            super.attachBaseContext(newBase.createConfigurationContext(config))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lang = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, "") ?: ""
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) BlueDarkScheme else BlueLightScheme,
            ) {
                GameScreen(
                    viewModel = viewModel,
                    onSharePgn = ::sharePgn,
                    currentLanguage = lang,
                    onLanguageChange = ::setLanguage,
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.autoSave()
    }

    private fun setLanguage(tag: String) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, tag).apply()
        recreate()
    }

    private fun sharePgn(pgn: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, pgn)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_pgn)))
    }

    private companion object {
        const val PREFS = "sakk"
        const val KEY_LANG = "cfg_lang"
    }
}
