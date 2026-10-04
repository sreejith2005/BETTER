package dev.better.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine

private val Context.uiStore by preferencesDataStore("ui_preferences")
class UiPreferences(context: Context, private val db: BetterDatabase? = null) {
    private val store = context.uiStore
    private val themeKey = stringPreferencesKey("theme")
    private val legacyTheme = store.data.map { it[themeKey] ?: "System" }
    val theme = db?.let { combine(legacyTheme, it.attention().watchSettings()) { legacy, settings -> settings?.theme ?: legacy } } ?: legacyTheme
    suspend fun setTheme(theme: String) {
        require(theme in listOf("System", "Light", "Dark"))
        store.edit { it[themeKey] = theme }
        db?.let { database ->
            val settings = AttentionRepository(database, dev.better.app.platform.AndroidClock()).settings()
            database.attention().save(settings.copy(theme = theme))
        }
    }
}
