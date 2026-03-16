package com.example.spikemobilewallet.data

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Parses an SD-JWT string into its components.
 *
 * SD-JWT format: <issuer-JWT>~<disclosure1>~<disclosure2>~...~
 * Each disclosure is a base64url-encoded JSON array: ["salt", "claim_name", "claim_value"]
 */
object SdJwtHelper {

    data class ParsedSdJwt(
        val issuerJwt: String,
        val disclosures: List<Disclosure>,
        val payload: JsonObject
    )

    data class Disclosure(
        val raw: String,           // the base64url-encoded string
        val salt: String,
        val claimName: String,
        val claimValue: String     // kept as raw JSON string
    )

    fun parse(rawSdJwt: String): ParsedSdJwt {
        val parts = rawSdJwt.split("~").filter { it.isNotEmpty() }
        val issuerJwt = parts.first()
        val payload = decodeJwtPayload(issuerJwt)

        val disclosures = parts.drop(1).mapNotNull { encoded ->
            try {
                val decoded = String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
                val jsonArray = Json.parseToJsonElement(decoded).jsonArray
                if (jsonArray.size >= 3) {
                    Disclosure(
                        raw = encoded,
                        salt = jsonArray[0].jsonPrimitive.content,
                        claimName = jsonArray[1].jsonPrimitive.content,
                        claimValue = jsonArray[2].toString().removeSurrounding("\"")
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }

        return ParsedSdJwt(issuerJwt, disclosures, payload)
    }

    fun decodeJwtPayload(jwt: String): JsonObject {
        val payloadPart = jwt.split(".").getOrNull(1) ?: return JsonObject(emptyMap())
        val decoded = String(Base64.decode(payloadPart, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        return Json.parseToJsonElement(decoded).jsonObject
    }

    fun extractIssuer(payload: JsonObject): String =
        payload["iss"]?.jsonPrimitive?.content ?: "Unknown"

    fun extractIssuedAt(payload: JsonObject): Long =
        payload["iat"]?.jsonPrimitive?.long ?: 0L

    fun extractExpiresAt(payload: JsonObject): Long? =
        payload["exp"]?.jsonPrimitive?.long

    fun extractVct(payload: JsonObject): String =
        payload["vct"]?.jsonPrimitive?.content ?: "Unknown"
}
