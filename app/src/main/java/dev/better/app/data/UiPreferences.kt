package dev.better.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.uiStore by preferencesDataStore("ui_preferences")
class UiPreferences(context: Context) {
    private val store = context.uiStore
    private val themeKey = stringPreferencesKey("theme")
    val theme = store.data.map { it[themeKey] ?: "System" }
    suspend fun setTheme(theme: String) {
        require(theme in listOf("System", "Light", "Dark"))
        store.edit { it[themeKey] = theme }
    }
}
