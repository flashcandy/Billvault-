package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)

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

    fun clearAllData() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILENAME = "bill_valid_prefs"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_NOTIF_HOUR = "key_notif_hour"
        private const val KEY_NOTIF_MINUTE = "key_notif_minute"
        private const val KEY_SAF_FOLDER_URI = "key_saf_folder_uri"

        const val THEME_DARK = "DARK"
        const val THEME_LIGHT = "LIGHT"
        const val THEME_SYSTEM = "SYSTEM"
    }
}
