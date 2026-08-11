package com.medocr.app.data.repository

import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import com.medocr.app.data.model.AuthState
import kotlinx.coroutines.flow.StateFlow

/** Result of an authorization attempt against the Sheets OAuth scope. */
sealed interface AuthOutcome {
    data class Authorized(val accessToken: String) : AuthOutcome
    data class ResolutionRequired(val intentSenderRequest: IntentSenderRequest) : AuthOutcome
    data class Failed(val message: String) : AuthOutcome
}

interface AuthRepository {
    val authState: StateFlow<AuthState>

    /** Attempts to get a Sheets-scoped access token without any UI; may return [AuthOutcome.ResolutionRequired]. */
    suspend fun tryAuthorize(): AuthOutcome

    /** Call from the launcher's callback after the user completes (or cancels) the consent screen. */
    fun handleAuthorizationResult(data: Intent?): AuthOutcome

    /** Cached or freshly-authorized access token, or null if the user has never connected. */
    suspend fun getAccessToken(): String?

    fun signOut()
}
