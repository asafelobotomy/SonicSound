package app.sonicsound.youtube

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import app.sonicsound.KeyValueStorage
import app.sonicsound.models.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Authorize YouTube Data API using a Google account on the device.
 *
 * Android TV's Identity IntentSender often returns RESULT_CANCELED (0) after a
 * successful account pick, so the primary path uses AccountManager chooser +
 * GoogleAuthUtil, with Identity authorize as a silent token source.
 */
object YoutubeGoogleAccount {
    private const val TAG = "YoutubeGoogleAccount"
    val YT_SCOPE = Scope(YoutubeOAuth.SCOPE)
    private const val AUTH_SCOPE = "oauth2:${YoutubeOAuth.SCOPE}"
    private const val GOOGLE_ACCOUNT_TYPE = "com.google"

    fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(YT_SCOPE))
            .build()

    /** Intent that lists Google accounts already signed into the TV. */
    fun accountChooserIntent(): Intent =
        AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf(GOOGLE_ACCOUNT_TYPE),
            null,
            null,
            null,
            null,
        )

    fun launchAccountChooser(launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(accountChooserIntent())
    }

    /**
     * Try silent Identity authorize first (no UI). If a token is already granted,
     * return it; otherwise caller should show the account chooser.
     */
    fun trySilentToken(
        activity: Activity,
        onToken: (String) -> Unit,
        onNeedChooser: () -> Unit,
        onError: (String) -> Unit,
    ) {
        Identity.getAuthorizationClient(activity)
            .authorize(authorizationRequest())
            .addOnSuccessListener { result ->
                Log.i(
                    TAG,
                    "silent authorize resolution=${result.hasResolution()} " +
                        "tokenBlank=${result.accessToken.isNullOrBlank()}"
                )
                when {
                    !result.accessToken.isNullOrBlank() ->
                        onToken(result.accessToken!!)
                    result.hasResolution() -> onNeedChooser()
                    else -> {
                        val account = result.toGoogleSignInAccount()?.account
                            ?: GoogleSignIn.getLastSignedInAccount(activity)?.account
                            ?: googleAccounts(activity).firstOrNull()
                        if (account != null) {
                            // Let caller fetch via GoogleAuthUtil on a background thread.
                            onNeedChooser()
                        } else {
                            onNeedChooser()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "silent authorize failed, showing chooser", e)
                onNeedChooser()
            }
    }

    fun googleAccounts(activity: Activity): List<Account> =
        try {
            AccountManager.get(activity).getAccountsByType(GOOGLE_ACCOUNT_TYPE).toList()
        } catch (e: SecurityException) {
            Log.w(TAG, "GET_ACCOUNTS denied", e)
            emptyList()
        }

    fun accountFromChooserResult(data: Intent?): Account? {
        if (data == null) return null
        val name = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) ?: return null
        val type = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE) ?: GOOGLE_ACCOUNT_TYPE
        return Account(name, type)
    }

    /**
     * After account chooser (or Identity resolution). RESULT_CANCELED on TV is
     * common even after a pick — still try data extras, silent authorize, and
     * on-device Google accounts.
     */
    suspend fun completeAfterChooser(
        activity: Activity,
        resultCode: Int,
        data: Intent?,
    ): String {
        Log.i(TAG, "completeAfterChooser resultCode=$resultCode data=${data != null}")

        accountFromChooserResult(data)?.let { return tokenForAccount(activity, it) }

        // Identity IntentSender path may put a result even when code == 0 on TV.
        val fromIntent = runCatching {
            Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(data)
        }.onFailure { Log.w(TAG, "parse intent result", it) }
            .getOrNull()
        fromIntent?.accessToken?.takeIf { it.isNotBlank() }?.let { return it }

        val silent = runCatching { authorizeSuspending(activity) }
            .onFailure { Log.w(TAG, "silent re-authorize", it) }
            .getOrNull()
        silent?.accessToken?.takeIf { it.isNotBlank() }?.let { return it }

        val account = fromIntent?.toGoogleSignInAccount()?.account
            ?: silent?.toGoogleSignInAccount()?.account
            ?: GoogleSignIn.getLastSignedInAccount(activity)?.account
            ?: googleAccounts(activity).singleOrNull()

        if (account != null) return tokenForAccount(activity, account)

        if (resultCode != Activity.RESULT_OK && data == null) {
            throw Exception("Sign-in cancelled")
        }
        throw Exception(missingCloudSetupMessage())
    }

    suspend fun tokenForAccount(activity: Activity, account: Account): String =
        withContext(Dispatchers.IO) {
            try {
                GoogleAuthUtil.getToken(activity, account, AUTH_SCOPE)
            } catch (e: UserRecoverableAuthException) {
                throw RecoverableAuth(e)
            } catch (e: GoogleAuthException) {
                throw Exception(formatError(e))
            } catch (e: ApiException) {
                throw Exception(formatError(e))
            }
        }

    private suspend fun authorizeSuspending(activity: Activity): AuthorizationResult =
        suspendCancellableCoroutine { cont ->
            Identity.getAuthorizationClient(activity)
                .authorize(authorizationRequest())
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }

    fun persistAccessToken(token: String, expiresInSec: Int = 3500) {
        val current = KeyValueStorage.getSettings()
        YoutubeOAuth.persistTokens(
            current.copy(youtubeVideosEnabled = true),
            YoutubeOAuth.Tokens(
                accessToken = token,
                refreshToken = current.youtubeRefreshToken.ifBlank { null },
                expiresInSec = expiresInSec,
            ),
        )
    }

    fun clear(current: Settings = KeyValueStorage.getSettings()) {
        YoutubeOAuth.clearTokens(current)
    }

    fun missingCloudSetupMessage(): String =
        "Could not get a YouTube token for the Google account. In Google Cloud Console: " +
            "enable YouTube Data API v3, create an Android OAuth client for package " +
            "app.sonicsound, and add this app's signing SHA-1 fingerprint."

    fun formatError(e: Throwable): String {
        val api = e as? ApiException
        val raw = (e.message ?: "").trim()
        val vague = raw.isEmpty() ||
            raw.equals("Error", true) ||
            raw.equals("ERROR", true) ||
            raw.equals("Unknown", true)
        return when (api?.statusCode) {
            10 ->
                "Google setup error (10): add package app.sonicsound + SHA-1 as an " +
                    "Android OAuth client in Google Cloud Credentials."
            12501 -> "Sign-in cancelled"
            7 -> "Network error talking to Google"
            else -> if (vague) missingCloudSetupMessage() else raw
        }
    }

    class RecoverableAuth(val causeEx: UserRecoverableAuthException) : Exception(causeEx)
}
