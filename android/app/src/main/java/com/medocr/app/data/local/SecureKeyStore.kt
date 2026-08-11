package com.medocr.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.medocr.app.data.model.Provider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores user-entered API keys encrypted at rest (AES256-GCM via Jetpack Security)
 * instead of plain DataStore — these are secrets, unlike theme/provider prefs.
 */
@Singleton
class SecureKeyStore @Inject constructor(@ApplicationContext context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "medocr_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun keyFor(provider: Provider) = "api_key_${provider.id}"

    private val _keys = MutableStateFlow(loadAll())
    val keys: StateFlow<Map<Provider, String>> = _keys.asStateFlow()

    private fun loadAll(): Map<Provider, String> =
        Provider.entries.associateWith { prefs.getString(keyFor(it), "") ?: "" }

    fun getKey(provider: Provider): String = _keys.value[provider].orEmpty()

    fun setKey(provider: Provider, value: String) {
        prefs.edit().putString(keyFor(provider), value).apply()
        _keys.value = _keys.value.toMutableMap().apply { this[provider] = value }
    }
}
