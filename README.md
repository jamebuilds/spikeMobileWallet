# Spike: Android Digital Credentials Wallet

A spike/proof-of-concept Android wallet app that implements the [Digital Credentials API](https://developer.chrome.com/blog/digital-credentials-api-shipped) to present SD-JWT credentials to websites via Chrome.

## What This Proves

- A native Android app can register SD-JWT credentials with Android's Credential Manager
- Chrome (146+) can discover and request credentials from the wallet via the Digital Credentials API
- The wallet can return SD-JWT `vp_token` responses to verifier websites
- End-to-end flow: Website → Chrome → Credential Selector → Wallet → Response

## Architecture

```
┌──────────────┐     navigator.credentials.get()     ┌──────────────┐
│   Verifier   │ ──────────────────────────────────→  │    Chrome     │
│  (website)   │                                      │   (browser)   │
└──────────────┘                                      └──────┬───────┘
                                                             │
                                                    Credential Manager
                                                    (Google Play Services)
                                                             │
                                                     WASM Matcher runs
                                                     in sandbox to find
                                                     matching credentials
                                                             │
                                                      ┌──────┴───────┐
                                                      │  Credential   │
                                                      │  Selector UI  │
                                                      └──────┬───────┘
                                                             │ user selects
                                                      ┌──────┴───────┐
                                                      │ Spike Wallet  │
                                                      │ (this app)    │
                                                      │               │
                                                      │ GetCredential │
                                                      │ Activity      │
                                                      └──────────────┘
```

## Key Implementation Details

### 1. Credential Storage (Room Database)

Credentials are stored in a Room database with the raw SD-JWT string and parsed metadata:

```kotlin
@Entity(tableName = "credentials")
data class StoredCredential(
    @PrimaryKey val id: String,
    val rawSdJwt: String,           // Raw SD-JWT as received from issuer
    val vct: String,                // Verifiable Credential Type
    val issuer: String,
    val claimsJson: String,         // Parsed claims for display
    val displayName: String,
    val holderKeyAlias: String      // Android Keystore alias
)
```

### 2. Credential Registration with Credential Manager

Register credentials using `RegistryManager` + `OpenId4VpRegistry` so Chrome can discover them:

```kotlin
val registryManager = RegistryManager.create(context)

val entries = credentials.map { cred ->
    SdJwtEntry(
        verifiableCredentialType = cred.vct,
        claims = cred.parsedClaims.map { (key, value) ->
            SdJwtClaim(
                path = listOf(key),
                value = value,
                fieldDisplayPropertySet = setOf(
                    VerificationFieldDisplayProperties(displayName = key, displayValue = value)
                )
            )
        },
        entryDisplayPropertySet = setOf(
            VerificationEntryDisplayProperties(title = cred.displayName, subtitle = cred.issuer, icon = bitmap)
        ),
        id = cred.id
    )
}

val registry = OpenId4VpRegistry(credentialEntries = entries, id = "wallet-registry-v1")
```

**Important:** The default WASM matcher from `OpenId4VpRegistry` may not work with all Chrome versions. We also register with the `openid4vp1_0.wasm` matcher (from the [CMWallet reference](https://github.com/digitalcredentialsdev/CMWallet)) as a fallback:

```kotlin
// Register with custom matcher
registryManager.registerCredentials(object : DigitalCredentialRegistry(
    id = registry.id,
    credentials = registry.credentials,
    matcher = context.assets.open("openid4vp1_0.wasm").readBytes()
) {})
```

### 3. AndroidManifest Intent Filter

The wallet needs an activity with the `GET_CREDENTIAL` intent filter:

```xml
<activity
    android:name=".GetCredentialActivity"
    android:exported="true"
    android:theme="@android:style/Theme.Translucent.NoTitleBar">
    <intent-filter>
        <action android:name="androidx.credentials.registry.provider.action.GET_CREDENTIAL" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

### 4. Handling Credential Requests (GetCredentialActivity)

When the user selects a credential, `GetCredentialActivity` is launched:

```kotlin
val request = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)

// Get selected credential ID (use selectedCredentialSet, not selectedEntryId)
val selectedId = request.selectedEntryId
    ?: request.selectedCredentialSet?.credentials?.firstOrNull()?.credentialId

// Look up credential and return the SD-JWT
val credential = db.credentialDao().getById(selectedId)
val responseJson = buildJsonObject {
    put("vp_token", credential.rawSdJwt)
}.toString()

PendingIntentHandler.setGetCredentialResponse(
    resultData,
    GetCredentialResponse(DigitalCredential(responseJson))
)
```

### 5. Holder Key Management

Keys are generated in Android Keystore (hardware-backed when available):

```kotlin
val keyGenSpec = KeyGenParameterSpec.Builder(alias, PURPOSE_SIGN or PURPOSE_VERIFY)
    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
    .setDigests(KeyProperties.DIGEST_SHA256)
    .build()

KeyPairGenerator.getInstance(KEY_ALGORITHM_EC, "AndroidKeyStore")
    .apply { initialize(keyGenSpec) }
    .generateKeyPair()
```

## Verifier-Side: How to Request Credentials

### Chrome 146+ API Format

```javascript
const credential = await navigator.credentials.get({
    digital: {
        requests: [{                              // "requests" not "providers"
            protocol: "openid4vp-v1-unsigned",
            data: {                               // object, not JSON string
                response_type: "vp_token",
                nonce: crypto.randomUUID(),
                dcql_query: {                     // DCQL, not presentation_definition
                    credentials: [{
                        id: "cred_0",
                        format: "dc+sd-jwt",      // not "vc+sd-jwt"
                        meta: {
                            vct_values: ["AccredifyEmployeePass"]
                        },
                        claims: [
                            { path: ["employeeId"] },
                            { path: ["firstName"] },
                            { path: ["lastName"] }
                        ]
                    }]
                }
            }
        }]
    },
    mediation: "required"
});

// credential.data contains: { vp_token: "eyJhbG..." }
```

### Critical Gotchas

| What | Correct | Wrong |
|------|---------|-------|
| API field | `requests` | `providers` |
| Data type | JS object | JSON string |
| Query format | `dcql_query` | `presentation_definition` |
| SD-JWT format | `dc+sd-jwt` | `vc+sd-jwt` |
| Claim paths | `["claimName"]` | `["$.claimName"]` |
| Protocol | `openid4vp-v1-unsigned` | `openid4vp` |
| vct filter | `meta.vct_values: [...]` | `filter.pattern` |
| Selected ID | `selectedCredentialSet` | `selectedEntryId` (returns null) |

### Matching Rules

The WASM matcher requires:
- **Requested claims must exist** in the registered credential. If the request asks for `family_name` but the credential only has `firstName`, it won't match.
- **vct must match** the `vct_values` in the request's `meta` field.
- **Format must be `dc+sd-jwt`** for SD-JWT credentials.

## Dependencies

```kotlin
// Credential Manager
implementation("androidx.credentials:credentials:1.5.0")

// Credential Manager Registry (for wallet registration)
implementation("androidx.credentials.registry:registry-digitalcredentials-sdjwtvc:1.0.0-alpha04")
implementation("androidx.credentials.registry:registry-digitalcredentials-openid:1.0.0-alpha04")
implementation("androidx.credentials.registry:registry-provider:1.0.0-alpha04")
implementation("androidx.credentials.registry:registry-provider-play-services:1.0.0-alpha04")

// Room (credential storage)
implementation("androidx.room:room-runtime:2.8.4")
implementation("androidx.room:room-ktx:2.8.4")
ksp("androidx.room:room-compiler:2.8.4")

// SD-JWT parsing
implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
```

## Emulator Testing Setup

1. **Chrome 141+** required — update via Play Store on the emulator
2. **Enable DC API flag**: `chrome://flags/#web-identity-digital-credentials` → Enabled
3. **Port forwarding** (resets on reinstall/restart):
   ```sh
   adb reverse tcp:8000 tcp:8000   # your web server
   adb reverse tcp:5173 tcp:5173   # vite dev server (if applicable)
   ```
4. **Launch wallet app first** before testing — credentials register on app startup

## Test Verifier

A simple test verifier page is included at `test-verifier/index.html`. Serve it and forward the port:

```sh
cd test-verifier && python3 -m http.server 8080
adb reverse tcp:8080 tcp:8080
```

Then open `http://localhost:8080` in Chrome on the emulator.

## What's NOT Implemented (for production)

- [ ] Biometric consent before credential release
- [ ] Key Binding JWT signing (proves holder key possession)
- [ ] Selective disclosure (returning only requested claims, not the full SD-JWT)
- [ ] OpenID4VCI credential issuance from a real issuer API
- [ ] Proper OpenID4VP response format with `presentation_submission`
- [ ] Verifier identity verification (checking origin/signatures)
- [ ] Credential expiry handling
- [ ] Encrypted database (SQLCipher)
- [ ] Proper error handling and user-facing error messages

## References

- [Chrome Digital Credentials API blog post](https://developer.chrome.com/blog/digital-credentials-api-shipped)
- [Android Credential Holder API](https://developer.android.com/identity/digital-credentials/credential-holder)
- [CMWallet reference implementation](https://github.com/digitalcredentialsdev/CMWallet)
- [Digital Credentials playground](https://demo.digitalcredentials.dev)
- [OpenID4VP 1.0 spec](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [SD-JWT VC spec](https://datatracker.ietf.org/doc/draft-ietf-oauth-sd-jwt-vc/)
- [W3C Digital Credentials spec](https://w3c-ccg.github.io/digital-credentials/)
