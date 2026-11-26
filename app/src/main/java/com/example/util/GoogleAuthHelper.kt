package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

sealed interface GoogleSignInOutcome {
    data class Success(val user: FirebaseUser) : GoogleSignInOutcome
    data class Cancelled(val hint: String? = null) : GoogleSignInOutcome
    data class Failure(val message: String) : GoogleSignInOutcome
}

object GoogleAuthHelper {
    private const val TAG = "Auth"

    fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        addAuthStateListener(listener)
        awaitClose { removeAuthStateListener(listener) }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private fun resolveDefaultWebClientId(context: Context): String {
        val resId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        return if (resId != 0) context.getString(resId).trim() else ""
    }

    @Suppress("DEPRECATION")
    fun getAppSigningSha1(context: Context): String? {
        return try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = packageInfo.signingInfo
                if (signingInfo == null) null
                else if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
                else signingInfo.signingCertificateHistory
            } else {
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
                packageInfo.signatures
            }
            val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return null
            val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
            digest.joinToString(":") { byte -> "%02X".format(byte) }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read APK SHA-1 fingerprint", e)
            null
        }
    }

    private suspend fun exchangeGoogleCredential(credential: Credential): GoogleSignInOutcome {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleSignInOutcome.Failure("Kredensial yang dipilih bukan akun Google.")
        }
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
        val authResult = Firebase.auth.signInWithCredential(firebaseCredential).await()
        val user = authResult.user ?: Firebase.auth.currentUser
        return if (user != null) {
            GoogleSignInOutcome.Success(user)
        } else {
            GoogleSignInOutcome.Failure("Autentikasi Google berhasil tetapi sesi Firebase belum terbentuk. Silakan coba lagi.")
        }
    }

    private fun isConfigurationOrPlayServicesError(raw: String): Boolean {
        val lower = raw.lowercase()
        return lower.contains("28444") ||
            lower.contains("28436") ||
            lower.contains("developer") ||
            lower.contains("10:") ||
            lower.contains("sha") ||
            lower.contains("blocked") ||
            lower.contains("cannot find a matching credential") ||
            lower.contains("reauth") ||
            lower.contains("misconfigured")
    }

    private fun formatSignInError(context: Context, rawError: String): String {
        val cleanRaw = rawError.trim()
        val lower = cleanRaw.lowercase()
        val sha1 = getAppSigningSha1(context)

        return when {
            lower.contains("28444") ||
                lower.contains("developer console") ||
                lower.contains("10:") ||
                lower.contains("developer_error") ||
                lower.contains("misconfigured") -> {
                buildString {
                    append("Gagal verifikasi aplikasi di Google (SHA-1 APK tidak cocok dengan Firebase).")
                    if (!sha1.isNullOrBlank()) {
                        append("\nSHA-1 APK ini: $sha1")
                    }
                    append("\nPastikan file debug.keystore.base64 atau GitHub Secret DEBUG_KEYSTORE_BASE64 aktif saat build APK.")
                }
            }

            lower.contains("28436") || lower.contains("temporarily blocked") -> {
                "Login Google diblokir sementara oleh Google Play Services karena beberapa kali dibatalkan. Tunggu 1–2 menit lalu ketuk 'Masuk dengan Google' lagi."
            }

            lower.contains("network") || lower.contains("timeout") || lower.contains("7:") -> {
                "Tidak dapat terhubung ke server Google. Periksa koneksi internet Anda lalu coba lagi."
            }

            lower.contains("cannot find a matching credential") || lower.contains("no credential") -> {
                buildString {
                    append("Akun Google belum terbaca oleh layanan Google Play di HP ini.")
                    if (!sha1.isNullOrBlank()) {
                        append("\nSHA-1 APK: $sha1")
                    }
                    append("\nPastikan akun Google sudah aktif di Pengaturan HP dan Google Play Services sudah diperbarui.")
                }
            }

            cleanRaw.isNotBlank() -> {
                buildString {
                    append("Gagal masuk dengan Google: $cleanRaw")
                    if (!sha1.isNullOrBlank()) {
                        append("\n(SHA-1 APK: $sha1)")
                    }
                }
            }

            else -> "Gagal masuk dengan Google. Pastikan akun Google aktif di perangkat dan koneksi internet stabil, lalu coba lagi."
        }
    }

    suspend fun attemptAutoSignIn(context: Context): GoogleSignInOutcome {
        Firebase.auth.currentUser?.let { existingUser ->
            return GoogleSignInOutcome.Success(existingUser)
        }
        val serverClientId = resolveDefaultWebClientId(context)
        if (serverClientId.isBlank()) {
            return GoogleSignInOutcome.Failure(
                "Konfigurasi Google Sign-In tidak ditemukan (default_web_client_id kosong)."
            )
        }
        val activityContext = context.findActivity() ?: context
        val credentialManager = CredentialManager.create(activityContext)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context = activityContext, request = request)
            exchangeGoogleCredential(result.credential)
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoCredentialException) {
            GoogleSignInOutcome.Cancelled()
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Silent Google auto sign-in cancelled: ${e.message}", e)
            GoogleSignInOutcome.Cancelled()
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Silent Google auto sign-in skipped: ${e.message}")
            GoogleSignInOutcome.Cancelled()
        } catch (e: Exception) {
            Log.w(TAG, "Silent Google auto sign-in error: ${e.message}")
            GoogleSignInOutcome.Cancelled()
        }
    }

    suspend fun onGoogleSignInClicked(context: Context): GoogleSignInOutcome {
        Firebase.auth.currentUser?.let { existingUser ->
            return GoogleSignInOutcome.Success(existingUser)
        }
        val serverClientId = resolveDefaultWebClientId(context)
        if (serverClientId.isBlank()) {
            return GoogleSignInOutcome.Failure(
                "Konfigurasi Google Sign-In tidak tersedia (default_web_client_id kosong)."
            )
        }
        val activityContext = context.findActivity() ?: context
        val credentialManager = CredentialManager.create(activityContext)
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(
            serverClientId = serverClientId
        ).build()

        val primaryRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = activityContext,
                request = primaryRequest
            )
            exchangeGoogleCredential(result.credential)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoCredentialException) {
            Log.w(TAG, "GetSignInWithGoogleOption returned NoCredentialException; retrying with GetGoogleIdOption", e)
            tryFallbackGoogleIdOption(activityContext, credentialManager, serverClientId)
        } catch (e: GetCredentialCancellationException) {
            val rawDetail = (e.errorMessage?.toString() ?: e.message ?: "").trim()
            Log.w(TAG, "Google Sign-In cancelled or dismissed: $rawDetail", e)
            if (isConfigurationOrPlayServicesError(rawDetail)) {
                GoogleSignInOutcome.Failure(formatSignInError(activityContext, rawDetail))
            } else {
                val hint = buildString {
                    append("Pemilihan akun Google belum selesai. Jika Anda baru menambahkan email Google di HP, ketuk \"Masuk dengan Google\" sekali lagi lalu pilih email tersebut.")
                    if (rawDetail.isNotBlank() && !rawDetail.equals("activity is cancelled by the user.", ignoreCase = true)) {
                        append("\nInfo: $rawDetail")
                    }
                }
                GoogleSignInOutcome.Cancelled(hint = hint)
            }
        } catch (e: GetCredentialException) {
            val rawDetail = (e.errorMessage?.toString() ?: e.localizedMessage ?: e.message ?: "").trim()
            Log.e(TAG, "Google Sign-In CredentialManager error: $rawDetail", e)
            GoogleSignInOutcome.Failure(formatSignInError(activityContext, rawDetail))
        } catch (e: Exception) {
            val rawDetail = (e.localizedMessage ?: e.message ?: "").trim()
            Log.e(TAG, "Firebase Google sign-in error: $rawDetail", e)
            GoogleSignInOutcome.Failure(formatSignInError(activityContext, rawDetail))
        }
    }

    private suspend fun tryFallbackGoogleIdOption(
        activityContext: Context,
        credentialManager: CredentialManager,
        serverClientId: String
    ): GoogleSignInOutcome {
        val fallbackOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()
        val fallbackRequest = GetCredentialRequest.Builder()
            .addCredentialOption(fallbackOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = activityContext,
                request = fallbackRequest
            )
            exchangeGoogleCredential(result.credential)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            val rawDetail = (e.errorMessage?.toString() ?: e.message ?: "").trim()
            Log.w(TAG, "Fallback Google Sign-In cancelled or dismissed: $rawDetail", e)
            if (isConfigurationOrPlayServicesError(rawDetail)) {
                GoogleSignInOutcome.Failure(formatSignInError(activityContext, rawDetail))
            } else {
                GoogleSignInOutcome.Cancelled(
                    hint = "Pemilihan akun Google belum selesai. Ketuk \"Masuk dengan Google\" sekali lagi lalu pilih email Anda."
                )
            }
        } catch (e: GetCredentialException) {
            val rawDetail = (e.errorMessage?.toString() ?: e.localizedMessage ?: e.message ?: "").trim()
            Log.e(TAG, "Fallback Google Sign-In error: $rawDetail", e)
            GoogleSignInOutcome.Failure(formatSignInError(activityContext, rawDetail))
        } catch (e: Exception) {
            val rawDetail = (e.localizedMessage ?: e.message ?: "").trim()
            Log.e(TAG, "Fallback Firebase Google sign-in error: $rawDetail", e)
            GoogleSignInOutcome.Failure(formatSignInError(activityContext, rawDetail))
        }
    }

    suspend fun signOutGoogle(context: Context) {
        withContext(NonCancellable) {
            try {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed clearing credential state", e)
            }
            Firebase.auth.signOut()
        }
    }
}
