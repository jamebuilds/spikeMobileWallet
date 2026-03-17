@file:OptIn(androidx.credentials.ExperimentalDigitalCredentialApi::class)

package com.example.spikemobilewallet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetDigitalCredentialOption
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.registry.provider.selectedCredentialSet
import androidx.credentials.registry.provider.selectedEntryId
import com.example.spikemobilewallet.data.StoredCredential
import com.example.spikemobilewallet.data.WalletDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TAG = "GetCredentialActivity"

class GetCredentialActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val request = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)

            if (request == null) {
                Log.e(TAG, "No credential request found in intent")
                finishWithError("No credential request")
                return
            }

            // Try both APIs to get the selected credential ID
            val selectedId = request.selectedEntryId
                ?: request.selectedCredentialSet?.credentials?.firstOrNull()?.credentialId

            Log.i(TAG, "Selected credential ID: $selectedId")
            Log.i(TAG, "selectedEntryId: ${request.selectedEntryId}")
            Log.i(TAG, "selectedCredentialSet: ${request.selectedCredentialSet}")
            Log.i(TAG, "sourceBundle keys: ${request.sourceBundle?.keySet()}")
            request.sourceBundle?.keySet()?.forEach { key ->
                Log.i(TAG, "  bundle[$key] = ${request.sourceBundle?.get(key)}")
            }

            val credential = selectedId?.let {
                runBlocking {
                    WalletDatabase.getInstance(this@GetCredentialActivity)
                        .credentialDao()
                        .getById(it)
                }
            }

            if (credential == null) {
                Log.e(TAG, "Credential not found for ID: $selectedId")
                finishWithError("Credential not found")
                return
            }

            // Process the digital credential request
            request.credentialOptions.forEach { option ->
                if (option is GetDigitalCredentialOption) {
                    Log.i(TAG, "Got DC request: ${option.requestJson}")
                    handleDigitalCredentialRequest(option.requestJson, credential)
                    return
                }
            }

            finishWithError("No digital credential option found")

        } catch (e: Exception) {
            Log.e(TAG, "Error processing credential request", e)
            finishWithError(e.message ?: "Unknown error")
        }
    }

    private fun handleDigitalCredentialRequest(requestJson: String, credential: StoredCredential) {
        val responseJson = buildJsonObject {
            put("vp_token", credential.rawSdJwt)
        }.toString()

        returnResponse(responseJson)
    }

    private fun returnResponse(responseJson: String) {
        Log.i(TAG, "Returning credential response")
        val resultData = Intent()
        PendingIntentHandler.setGetCredentialResponse(
            resultData,
            GetCredentialResponse(DigitalCredential(responseJson))
        )
        setResult(RESULT_OK, resultData)
        finish()
    }

    private fun finishWithError(message: String) {
        val resultData = Intent()
        PendingIntentHandler.setGetCredentialException(
            resultData,
            GetCredentialUnknownException(message)
        )
        setResult(RESULT_OK, resultData)
        finish()
    }
}
