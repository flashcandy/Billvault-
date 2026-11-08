package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.utils.OpenRouterService

class SettingsManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)

    private val encryptedPreferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // Fallback for local JVM / Robolectric unit test environments where AndroidKeyStore is unavailable
        context.getSharedPreferences(ENCRYPTED_PREFS_FILENAME, Context.MODE_PRIVATE)
    }

    var themeMode: String
        get() = sharedPreferences.getString(KEY_THEME_MODE, THEME_DARK) ?: THEME_DARK
        set(value) = sharedPreferences.edit().putString(KEY_THEME_MODE, value).apply()

    var notificationHour: Int
        get() = sharedPreferences.getInt(KEY_NOTIF_HOUR, 9)
        set(value) = sharedPreferences.edit().putInt(KEY_NOTIF_HOUR, value).apply()

    var notificationMinute: Int
        get() = sharedPreferences.getInt(KEY_NOTIF_MINUTE, 0)
        set(value) = sharedPreferences.edit().putInt(KEY_NOTIF_MINUTE, value).apply()

    var safFolderUri: String?
        get() = sharedPreferences.getString(KEY_SAF_FOLDER_URI, null)
        set(value) = sharedPreferences.edit().putString(KEY_SAF_FOLDER_URI, value).apply()

    var openRouterApiKey: String
        get() = encryptedPreferences.getString(KEY_OPENROUTER_API_KEY, "") ?: ""
        set(value) = encryptedPreferences.edit().putString(KEY_OPENROUTER_API_KEY, value.trim()).apply()

    var selectedAiModel: String
        get() = sharedPreferences.getString(KEY_SELECTED_AI_MODEL, OpenRouterService.DEFAULT_MODEL_ID)
            ?: OpenRouterService.DEFAULT_MODEL_ID
        set(value) = sharedPreferences.edit().putString(KEY_SELECTED_AI_MODEL, value.trim()).apply()

    fun clearAllData() {
        sharedPreferences.edit().clear().apply()
        encryptedPreferences.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILENAME = "bill_valid_prefs"
        private const val ENCRYPTED_PREFS_FILENAME = "bill_valid_secure_prefs"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_NOTIF_HOUR = "key_notif_hour"
        private const val KEY_NOTIF_MINUTE = "key_notif_minute"
        private const val KEY_SAF_FOLDER_URI = "key_saf_folder_uri"
        private const val KEY_OPENROUTER_API_KEY = "key_openrouter_api_key"
        private const val KEY_SELECTED_AI_MODEL = "key_selected_ai_model"

        const val THEME_DARK = "DARK"
        const val THEME_LIGHT = "LIGHT"
        const val THEME_SYSTEM = "SYSTEM"
    }
}
