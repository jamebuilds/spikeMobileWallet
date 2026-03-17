package com.example.spikemobilewallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.spikemobilewallet.data.CredentialRegistryHelper
import com.example.spikemobilewallet.data.HolderKeyManager
import com.example.spikemobilewallet.data.StoredCredential
import com.example.spikemobilewallet.data.TestCredentials
import com.example.spikemobilewallet.data.WalletDatabase
import com.example.spikemobilewallet.ui.theme.SpikeMobileWalletTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class MainActivity : ComponentActivity() {

    private val db by lazy { WalletDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Seed test credential if DB is empty, then register with Credential Manager
        lifecycleScope.launch {
            val existing = db.credentialDao().getAll().first()
            if (existing.isEmpty()) {
                val holderKeyAlias = HolderKeyManager.generateHolderKey()
                val testCred = TestCredentials.createTestAgeCredential(holderKeyAlias)
                db.credentialDao().insert(testCred)
            }

            // Register all credentials so Chrome can discover this wallet
            val allCredentials = db.credentialDao().getAll().first()
            CredentialRegistryHelper.registerAll(this@MainActivity, allCredentials)
        }

        setContent {
            SpikeMobileWalletTheme {
                val credentials by db.credentialDao().getAll().collectAsState(initial = emptyList())

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CredentialListScreen(
                        credentials = credentials,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun CredentialListScreen(credentials: List<StoredCredential>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Spike Wallet",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${credentials.size} credential(s)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (credentials.isEmpty()) {
            Text("No credentials yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(credentials) { cred ->
                    CredentialCard(cred)
                }
            }
        }
    }
}

@Composable
fun CredentialCard(credential: StoredCredential, modifier: Modifier = Modifier) {
    val claims = try {
        Json.decodeFromString<JsonObject>(credential.claimsJson)
    } catch (e: Exception) {
        null
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = credential.displayName,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Issued by: ${credential.issuerDisplayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Type: ${credential.vct}",
                style = MaterialTheme.typography.bodySmall
            )

            if (claims != null) {
                Spacer(modifier = Modifier.height(8.dp))
                claims.forEach { (key, value) ->
                    Text(
                        text = "$key: $value",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
