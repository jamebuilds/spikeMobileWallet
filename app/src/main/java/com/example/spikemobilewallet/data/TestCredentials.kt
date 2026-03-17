package com.example.spikemobilewallet.data

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Generates a self-signed test SD-JWT credential for development.
 * NOT cryptographically valid — just structurally correct for testing
 * the wallet UI and Credential Manager registration flow.
 */
object TestCredentials {

    fun createTestAgeCredential(holderKeyAlias: String): StoredCredential {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis() / 1000

        // Build the issuer JWT payload
        val payload = buildJsonObject {
            put("iss", "https://test-issuer.example.com")
            put("iat", now)
            put("exp", now + 365 * 24 * 3600) // 1 year
            put("vct", "IdentityCredential")
            put("_sd_alg", "sha-256")
            // In a real SD-JWT, _sd would contain hashes of the disclosures
            put("_sd", JsonArray(listOf(JsonPrimitive("placeholder_hash_1"), JsonPrimitive("placeholder_hash_2"))))
        }

        // Build disclosures
        val disclosures = listOf(
            makeDisclosure("given_name", "John"),
            makeDisclosure("family_name", "Doe"),
            makeDisclosure("age_over_21", "true"),
            makeDisclosure("age_over_18", "true"),
        )

        // Assemble the SD-JWT: <header>.<payload>.<sig>~<disc1>~<disc2>~...~
        val header = base64url("""{"alg":"ES256","typ":"vc+sd-jwt"}""")
        val payloadEncoded = base64url(payload.toString())
        val signature = base64url("test-signature-not-valid") // placeholder
        val issuerJwt = "$header.$payloadEncoded.$signature"

        val rawSdJwt = issuerJwt + "~" + disclosures.joinToString("~") { it.first } + "~"

        val claims = buildJsonObject {
            put("given_name", "John")
            put("family_name", "Doe")
            put("age_over_21", true)
            put("age_over_18", true)
        }

        return StoredCredential(
            id = id,
            rawSdJwt = rawSdJwt,
            vct = "IdentityCredential",
            issuer = "https://test-issuer.example.com",
            issuedAt = now,
            expiresAt = now + 365 * 24 * 3600,
            claimsJson = claims.toString(),
            displayName = "Test Age Credential",
            issuerDisplayName = "Test Issuer",
            holderKeyAlias = holderKeyAlias
        )
    }

    fun createTestEmployeeCredential(holderKeyAlias: String): StoredCredential {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis() / 1000

        val payload = buildJsonObject {
            put("iss", "https://accredify.example.com")
            put("iat", now)
            put("exp", now + 365 * 24 * 3600)
            put("vct", "AccredifyEmployeePass")
            put("_sd_alg", "sha-256")
            put("_sd", JsonArray(listOf(
                JsonPrimitive("placeholder_hash_1"),
                JsonPrimitive("placeholder_hash_2"),
                JsonPrimitive("placeholder_hash_3"),
                JsonPrimitive("placeholder_hash_4"),
                JsonPrimitive("placeholder_hash_5")
            )))
        }

        val disclosures = listOf(
            makeDisclosure("employeeId", "EMP-2024-001"),
            makeDisclosure("firstName", "John"),
            makeDisclosure("lastName", "Doe"),
            makeDisclosure("dateOfBirth", "1990-01-15"),
            makeDisclosure("nric", "S1234567A"),
        )

        val header = base64url("""{"alg":"ES256","typ":"vc+sd-jwt"}""")
        val payloadEncoded = base64url(payload.toString())
        val signature = base64url("test-signature-not-valid")
        val issuerJwt = "$header.$payloadEncoded.$signature"

        val rawSdJwt = issuerJwt + "~" + disclosures.joinToString("~") { it.first } + "~"

        val claims = buildJsonObject {
            put("employeeId", "EMP-2024-001")
            put("firstName", "John")
            put("lastName", "Doe")
            put("dateOfBirth", "1990-01-15")
            put("nric", "S1234567A")
        }

        return StoredCredential(
            id = id,
            rawSdJwt = rawSdJwt,
            vct = "AccredifyEmployeePass",
            issuer = "https://accredify.example.com",
            issuedAt = now,
            expiresAt = now + 365 * 24 * 3600,
            claimsJson = claims.toString(),
            displayName = "Accredify Employee Pass",
            issuerDisplayName = "Accredify",
            holderKeyAlias = holderKeyAlias
        )
    }

    /** Returns Pair(base64url-encoded-disclosure, raw-json) */
    private fun makeDisclosure(claimName: String, claimValue: String): Pair<String, String> {
        val salt = UUID.randomUUID().toString().take(8)
        val json = """["$salt","$claimName","$claimValue"]"""
        return base64url(json) to json
    }

    private fun base64url(input: String): String =
        Base64.encodeToString(
            input.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
}
