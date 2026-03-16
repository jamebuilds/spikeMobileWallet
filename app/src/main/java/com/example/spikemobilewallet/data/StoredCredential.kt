package com.example.spikemobilewallet.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credentials")
data class StoredCredential(
    @PrimaryKey
    val id: String,

    // The raw SD-JWT string exactly as received from the issuer
    val rawSdJwt: String,

    // Parsed metadata for UI display + registry matching
    val vct: String,              // Verifiable Credential Type, e.g. "IdentityCredential"
    val issuer: String,           // from JWT "iss" claim
    val issuedAt: Long,           // from JWT "iat" (epoch seconds)
    val expiresAt: Long?,         // from JWT "exp" (epoch seconds), nullable

    // Decoded claims stored as JSON string: {"given_name": "John", "age_over_21": true}
    val claimsJson: String,

    // Display
    val displayName: String,      // e.g. "Age Credential", "Driver's License"
    val issuerDisplayName: String, // e.g. "Test Issuer", "CA DMV"

    // Android Keystore alias for the holder's private key (used for Key Binding JWT)
    val holderKeyAlias: String
)
