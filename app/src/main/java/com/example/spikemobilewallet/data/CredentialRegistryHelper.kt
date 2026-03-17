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

        val registry = OpenId4VpRegistry(
            credentialEntries = entries,
            id = "spike-wallet-registry-v1"
        )

        try {
            registryManager.registerCredentials(registry)
            Log.i(TAG, "Registered ${entries.size} credential(s) with CredentialManager")
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
            color = Color.parseColor("#6750A4") // Material You purple
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
