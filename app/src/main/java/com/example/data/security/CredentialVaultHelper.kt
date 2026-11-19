package com.example.data.security

import android.content.Context
import android.os.Build
import android.view.autofill.AutofillManager
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException

data class CredentialSystemStatus(
    val credentialManagerSupported: Boolean,
    val passkeysSupported: Boolean,
    val autofillFrameworkSupported: Boolean,
    val autofillServiceEnabled: Boolean,
    val providerSummary: String,
)

object CredentialVaultHelper {

    fun inspectSystemCredentialStatus(context: Context): CredentialSystemStatus {
        val autofillSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        var autofillEnabled = false
        if (autofillSupported) {
            val afm = context.getSystemService(AutofillManager::class.java)
            autofillEnabled = afm?.isEnabled == true && afm.hasEnabledAutofillServices()
        }
        val passkeysSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        val summary = when {
            autofillEnabled -> "System Autofill & Credential Manager active (compatible with Google Password Manager & passkeys)."
            autofillSupported -> "Android Autofill ready; enable a system password service (e.g. Google Password Manager) in Android Settings."
            else -> "Basic browser sign-in only (Android 8.0+ required for system Autofill)."
        }
        return CredentialSystemStatus(
            credentialManagerSupported = true,
            passkeysSupported = passkeysSupported,
            autofillFrameworkSupported = autofillSupported,
            autofillServiceEnabled = autofillEnabled,
            providerSummary = summary,
        )
    }

    fun buildGetCredentialRequest(passkeyChallengeJson: String? = null): GetCredentialRequest {
        val options = mutableListOf<CredentialOption>(GetPasswordOption())
        if (!passkeyChallengeJson.isNullOrBlank()) {
            options.add(GetPublicKeyCredentialOption(requestJson = passkeyChallengeJson))
        }
        return GetCredentialRequest(options)
    }

    suspend fun checkSavedCredentialPrompt(
        activityContext: Context,
        passkeyChallengeJson: String? = null,
    ): String {
        return try {
            val manager = CredentialManager.create(activityContext)
            val request = buildGetCredentialRequest(passkeyChallengeJson)
            val result = manager.getCredential(activityContext, request)
            "Credential returned cleanly from system provider (${result.credential.type})."
        } catch (_: GetCredentialCancellationException) {
            "Authentication prompt cancelled by user."
        } catch (_: NoCredentialException) {
            "Credential Manager & Passkey pipeline ready (no matching saved credential returned)."
        } catch (err: Exception) {
            "Credential Manager check completed (${err.javaClass.simpleName}: ${err.localizedMessage ?: "no active provider"})."
        }
    }
}
