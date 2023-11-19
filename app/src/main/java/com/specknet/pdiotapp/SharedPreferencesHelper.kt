package com.specknet.pdiotapp

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesHelper(context: Context) {
    private val PREF_NAME = "prefs_user"
    private val KEY_USERNAME = "username"
    private val KEY_PASSWORD = "password"

    private var prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveUser(username: String, password: String) {
        val editor = prefs.edit()
        editor.putString(KEY_USERNAME, username)
        editor.putString(KEY_PASSWORD, password)
        editor.apply()
    }

    fun getUser(): Pair<String?, String?> {
        val username = prefs.getString(KEY_USERNAME, null)
        val password = prefs.getString(KEY_PASSWORD, null)
        return Pair(username, password)
    }
}