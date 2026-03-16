package com.example.spikemobilewallet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [StoredCredential::class], version = 1)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun credentialDao(): CredentialDao

    companion object {
        @Volatile
        private var INSTANCE: WalletDatabase? = null

        fun getInstance(context: Context): WalletDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WalletDatabase::class.java,
                    "wallet.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
