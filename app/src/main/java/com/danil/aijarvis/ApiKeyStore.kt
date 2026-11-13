package com.danil.aijarvis

import android.content.Context

object ApiKeyStore {
    private const val PREFS = "settings"
    private const val KEY = "gemini_api_key"

    fun get(context: Context): String =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "").orEmpty()

    fun set(context: Context, value: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, value.trim())
            .apply()
    }
}