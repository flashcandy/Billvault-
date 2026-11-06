package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig

class SettingsManager(context: Context) {

    private val sharedPreferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (t: Throwable) {
        // Fallback to standard SharedPreferences if EncryptedSharedPreferences/Android Keystore fails on JVM unit tests
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
    }

    var aiProvider: String
        get() = sharedPreferences.getString(KEY_AI_PROVIDER, PROVIDER_GEMINI) ?: PROVIDER_GEMINI
        set(value) = sharedPreferences.edit().putString(KEY_AI_PROVIDER, value).apply()

    var geminiApiKey: String
        get() {
            val key = sharedPreferences.getString(KEY_GEMINI_API_KEY, "") ?: ""
            if (key.isNotBlank()) return key
            // Fallback to BuildConfig if available
            return try {
                BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String ?: ""
            } catch (e: Exception) {
                ""
            }
        }
        set(value) = sharedPreferences.edit().putString(KEY_GEMINI_API_KEY, value).apply()

    var openRouterApiKey: String
        get() = sharedPreferences.getString(KEY_OPENROUTER_API_KEY, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_OPENROUTER_API_KEY, value).apply()

    var geminiModelName: String
        get() = sharedPreferences.getString(KEY_GEMINI_MODEL, "gemini-2.0-flash") ?: "gemini-2.0-flash"
        set(value) = sharedPreferences.edit().putString(KEY_GEMINI_MODEL, value).apply()

    var openRouterModelName: String
        get() = sharedPreferences.getString(KEY_OPENROUTER_MODEL, "google/gemini-2.0-flash-001") ?: "google/gemini-2.0-flash-001"
        set(value) = sharedPreferences.edit().putString(KEY_OPENROUTER_MODEL, value).apply()

    var themeMode: String
        get() = sharedPreferences.getString(KEY_THEME_MODE, THEME_DARK) ?: THEME_DARK
        set(value) = sharedPreferences.edit().putString(KEY_THEME_MODE, value).apply()

    var notificationHour: Int
        get() = sharedPreferences.getInt(KEY_NOTIF_HOUR, 9)
        set(value) = sharedPreferences.edit().putInt(KEY_NOTIF_HOUR, value).apply()

    var notificationMinute: Int
        get() = sharedPreferences.getInt(KEY_NOTIF_MINUTE, 0)
        set(value) = sharedPreferences.edit().putInt(KEY_NOTIF_MINUTE, value).apply()

    fun clearAllData() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILENAME = "bill_valid_encrypted_prefs"
        private const val KEY_AI_PROVIDER = "key_ai_provider"
        private const val KEY_GEMINI_API_KEY = "key_gemini_api_key"
        private const val KEY_OPENROUTER_API_KEY = "key_openrouter_api_key"
        private const val KEY_GEMINI_MODEL = "key_gemini_model"
        private const val KEY_OPENROUTER_MODEL = "key_openrouter_model"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_NOTIF_HOUR = "key_notif_hour"
        private const val KEY_NOTIF_MINUTE = "key_notif_minute"

        const val PROVIDER_GEMINI = "GEMINI"
        const val PROVIDER_OPENROUTER = "OPENROUTER"

        const val THEME_DARK = "DARK"
        const val THEME_LIGHT = "LIGHT"
        const val THEME_SYSTEM = "SYSTEM"
    }
}
