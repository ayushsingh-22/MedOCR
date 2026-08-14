package com.medocr.app.data.repository

import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.medocr.app.data.model.AuthState
import com.medocr.app.util.await
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"

/**
 * Wraps Google Identity's Authorization API to get an OAuth access token scoped
 * to Sheets — no separate sign-in step, matching the web app's single
 * "Connect Account" button. Google Play Services resolves the OAuth client by
 * this app's package name + signing certificate, so no client ID needs to be
 * embedded here — see the Android README for the one-time Cloud Console setup
 * (register an "Android" OAuth client with this app's SHA-1 fingerprint).
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : AuthRepository {

    companion object {
        // Access tokens are short-lived. Refresh a little before the typical 1h expiry.
        private const val TOKEN_MAX_AGE_MS = 50 * 60 * 1000L
    }

    private val authorizationClient by lazy { Identity.getAuthorizationClient(context) }
    private var cachedAccessToken: String? = null
    private var cachedAtMs: Long = 0L

    private val _authState = MutableStateFlow(AuthState.UNKNOWN)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private fun buildRequest(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SHEETS_SCOPE)))
            .build()

    override suspend fun tryAuthorize(): AuthOutcome = try {
        val result = authorizationClient.authorize(buildRequest()).await()
        processResult(result)
    } catch (e: Exception) {
        clearCachedToken()
        _authState.value = AuthState.DISCONNECTED
        AuthOutcome.Failed(friendlyMessage(e))
    }

    override fun handleAuthorizationResult(data: Intent?): AuthOutcome = try {
        val result = authorizationClient.getAuthorizationResultFromIntent(data)
        processResult(result)
    } catch (e: Exception) {
        clearCachedToken()
        _authState.value = AuthState.DISCONNECTED
        AuthOutcome.Failed(friendlyMessage(e))
    }

    /**
     * Play Services reports config problems as bare status codes (e.g. "10:").
     * On a fresh test device this is almost always the Cloud Console OAuth
     * client not being registered yet — surface that instead of a raw code.
     */
    private fun friendlyMessage(e: Exception): String {
        val apiException = e as? ApiException ?: return e.message ?: "Authorization failed."
        return when (apiException.statusCode) {
            CommonStatusCodes.DEVELOPER_ERROR ->
                "Google Sheets isn't set up for this app build yet. Register an \"Android\" " +
                    "OAuth client for this package name + SHA-1 in Google Cloud Console " +
                    "(see android/README.md) — everything else in the app works without this."
            CommonStatusCodes.NETWORK_ERROR -> "No internet connection — check your network and try again."
            CommonStatusCodes.CANCELED -> "Google sign-in was cancelled."
            else -> apiException.message ?: "Authorization failed (code ${apiException.statusCode})."
        }
    }

    private fun processResult(result: AuthorizationResult): AuthOutcome {
        val token = result.accessToken
        val pendingIntent = result.pendingIntent
        return when {
            token != null -> {
                cachedAccessToken = token
                cachedAtMs = System.currentTimeMillis()
                _authState.value = AuthState.CONNECTED
                AuthOutcome.Authorized(token)
            }
            pendingIntent != null -> {
                val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                AuthOutcome.ResolutionRequired(request)
            }
            else -> {
                clearCachedToken()
                _authState.value = AuthState.DISCONNECTED
                AuthOutcome.Failed("Google did not return an access token or a consent screen.")
            }
        }
    }

    override suspend fun getAccessToken(forceRefresh: Boolean): String? {
        val cacheIsFresh = (System.currentTimeMillis() - cachedAtMs) < TOKEN_MAX_AGE_MS
        if (!forceRefresh && cacheIsFresh) {
            cachedAccessToken?.let { return it }
        }
        return (tryAuthorize() as? AuthOutcome.Authorized)?.accessToken
    }

    override fun signOut() {
        clearCachedToken()
        _authState.value = AuthState.DISCONNECTED
    }

    private fun clearCachedToken() {
        cachedAccessToken = null
        cachedAtMs = 0L
    }
}
