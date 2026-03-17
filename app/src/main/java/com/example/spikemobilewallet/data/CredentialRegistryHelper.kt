@file:OptIn(androidx.credentials.ExperimentalDigitalCredentialApi::class)

package com.example.spikemobilewallet.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.credentials.registry.digitalcredentials.openid4vp.OpenId4VpRegistry
import androidx.credentials.registry.digitalcredentials.sdjwt.SdJwtClaim
import androidx.credentials.registry.digitalcredentials.sdjwt.SdJwtEntry
import androidx.credentials.registry.provider.RegistryManager
import androidx.credentials.registry.provider.digitalcredentials.DigitalCredentialRegistry
import androidx.credentials.registry.provider.digitalcredentials.VerificationEntryDisplayProperties
import androidx.credentials.registry.provider.digitalcredentials.VerificationFieldDisplayProperties
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "CredentialRegistry"

object CredentialRegistryHelper {

    suspend fun registerAll(context: Context, credentials: List<StoredCredential>) {
        if (credentials.isEmpty()) return

        val registryManager = RegistryManager.create(context)
        val entries = credentials.map { it.toSdJwtEntry() }

        // Build the registry using OpenId4VpRegistry to get the credential bytes
        val registry = OpenId4VpRegistry(
            credentialEntries = entries,
            id = "spike-wallet-registry-v1"
        )

        // Debug: log the entries
        for (entry in entries) {
            Log.d(TAG, "Entry id=${entry.id}, vct=${(entry as SdJwtEntry).verifiableCredentialType}, claims=${entry.claims.size}")
            entry.claims.forEach { claim ->
                Log.d(TAG, "  claim path=${claim.path}, value=${claim.value}, disclosable=${claim.isSelectivelyDisclosable}")
            }
        }

        // Try registering with the default matcher first, then custom
        try {
            // Use the library's OpenId4VpRegistry directly (includes DEFAULT_MATCHER)
            registryManager.registerCredentials(registry)
            Log.i(TAG, "Registered ${entries.size} credential(s) with DEFAULT matcher")
        } catch (e: Exception) {
            Log.e(TAG, "DEFAULT matcher registration failed", e)
        }

        // Also register with the CMWallet openid4vp1_0 matcher under a different ID
        val matcher = context.assets.open("openid4vp1_0.wasm").readBytes()
        Log.d(TAG, "Loaded WASM matcher: ${matcher.size} bytes")
        try {
            registryManager.registerCredentials(object : DigitalCredentialRegistry(
                id = "spike-wallet-registry-v1-alt",
                credentials = registry.credentials,
                matcher = matcher
            ) {})
            Log.i(TAG, "Registered ${entries.size} credential(s) with CMWallet matcher")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register credentials", e)
        }
    }

    private fun StoredCredential.toSdJwtEntry(): SdJwtEntry {
        val claims = parseClaims()

        val displayProperties = VerificationEntryDisplayProperties(
            title = displayName,
            subtitle = issuerDisplayName,
            icon = createPlaceholderIcon()
        )

        return SdJwtEntry(
            verifiableCredentialType = vct,
            claims = claims,
            entryDisplayPropertySet = setOf(displayProperties),
            id = id
        )
    }

    private fun StoredCredential.parseClaims(): List<SdJwtClaim> {
        return try {
            val json = Json.decodeFromString<JsonObject>(claimsJson)
            json.map { (key, value) ->
                SdJwtClaim(
                    path = listOf(key),
                    value = value.jsonPrimitive.content,
                    fieldDisplayPropertySet = setOf(
                        VerificationFieldDisplayProperties(
                            displayName = key,
                            displayValue = value.jsonPrimitive.content
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse claims for credential $id", e)
            emptyList()
        }
    }

    private fun createPlaceholderIcon(): Bitmap {
        val size = 32
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.parseColor("#6750A4")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 8f, 8f, paint)

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("S", size / 2f, size / 2f + 6f, textPaint)
        return bitmap
    }
}
