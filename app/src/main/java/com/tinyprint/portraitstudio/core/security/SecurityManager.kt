package com.tinyprint.portraitstudio.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurityManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(key: String) {
        sharedPreferences.edit().putString("gemini_api_key", key).apply()
    }

    fun getApiKey(): String? {
        return sharedPreferences.getString("gemini_api_key", null)
    }

    fun deleteApiKey() {
        sharedPreferences.edit().remove("gemini_api_key").apply()
    }

    fun saveLastPrinterAddress(address: String?) {
        if (address == null) {
            sharedPreferences.edit().remove("last_printer_address").apply()
        } else {
            sharedPreferences.edit().putString("last_printer_address", address).apply()
        }
    }

    fun getLastPrinterAddress(): String? {
        return sharedPreferences.getString("last_printer_address", null)
    }

    fun saveCustomStyles(stylesJson: String) {
        sharedPreferences.edit().putString("custom_styles", stylesJson).apply()
    }

    fun getCustomStyles(): String? {
        return sharedPreferences.getString("custom_styles", null)
    }
}
