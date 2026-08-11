package com.medocr.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.medocr.app.data.model.Provider
import com.medocr.app.data.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "medocr_settings")

/** Non-sensitive user preferences — theme, provider choice, and Sheet target. */
@Singleton
class SettingsDataStore @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PROVIDER = stringPreferencesKey("provider")
        val SHEET_ID = stringPreferencesKey("sheet_id")
        val SHEET_NAME = stringPreferencesKey("sheet_name")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { ThemeMode.fromId(it[Keys.THEME_MODE]) }
    val provider: Flow<Provider> = context.dataStore.data.map { Provider.fromId(it[Keys.PROVIDER]) }
    val sheetId: Flow<String> = context.dataStore.data.map { it[Keys.SHEET_ID] ?: "" }
    val sheetName: Flow<String> = context.dataStore.data.map { it[Keys.SHEET_NAME] ?: "Sheet1" }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.id }
    }

    suspend fun setProvider(provider: Provider) {
        context.dataStore.edit { it[Keys.PROVIDER] = provider.id }
    }

    suspend fun setSheetId(value: String) {
        context.dataStore.edit { it[Keys.SHEET_ID] = value }
    }

    suspend fun setSheetName(value: String) {
        context.dataStore.edit { it[Keys.SHEET_NAME] = value }
    }
}
