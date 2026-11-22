package com.example.spotifly.data.cloud

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.spotifly.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

/** UI state contains no tokens. Firebase retains the authenticated session. */
data class AccountState(
    val uid: String? = null,
    val email: String? = null,
    val busy: Boolean = false,
    val error: String? = null
)

class GoogleAccountManager private constructor(context: Context) {
    private val credentials = CredentialManager.create(context.applicationContext)
    private val auth = FirebaseAuth.getInstance()
    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        auth.addAuthStateListener { current ->
            _state.update { it.copy(uid = current.currentUser?.uid, email = current.currentUser?.email) }
        }
    }

    suspend fun signIn(activity: Activity) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        try {
            val option = GetSignInWithGoogleOption.Builder(
                activity.getString(R.string.default_web_client_id)
            ).build()
            val result = credentials.getCredential(
                context = activity,
                request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            )
            val credential = result.credential
            require(credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
            auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        } catch (_: GetCredentialCancellationException) {
            // The account picker is optional. Dismissing it leaves the local library available.
        } catch (_: NoCredentialException) {
            _state.update { it.copy(error = "No Google account is available. Add a Google account in your phone's Settings, then try again.") }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _state.update { it.copy(error = "Couldn't sign in. Check your connection and Google account, then try again.") }
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    suspend fun signOut() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        auth.signOut()
        try {
            credentials.clearCredentialState(ClearCredentialStateRequest())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _state.update { it.copy(error = "Signed out. Google account selection could not be reset.") }
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    companion object {
        @Volatile private var instance: GoogleAccountManager? = null
        fun getInstance(context: Context): GoogleAccountManager = instance ?: synchronized(this) {
            instance ?: GoogleAccountManager(context.applicationContext).also { instance = it }
        }
    }
}
