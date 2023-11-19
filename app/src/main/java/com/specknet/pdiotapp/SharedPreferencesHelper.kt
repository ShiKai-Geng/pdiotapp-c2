package com.specknet.pdiotapp

import android.content.Context
import android.content.SharedPreferences

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SharedPreferencesHelper(context: Context) {
    private val PREF_NAME = "prefs_users"
    private val KEY_USERS = "users"

    private val gson = Gson()
    private var prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveUser(username: String, password: String) {
        val users = getUsers().toMutableList()

        // Check if the username already exists
        if (users.any { it.username == username }) {
            // Handle the case where the username is already taken
            return
        }

        // Add the new user
        val newUser = User(username, password)
        users.add(newUser)

        // Save the updated user list
        val editor = prefs.edit()
        editor.putString(KEY_USERS, gson.toJson(users))
        editor.apply()
    }

    fun getUsers(): List<User> {
        val usersJson = prefs.getString(KEY_USERS, null)
        return if (!usersJson.isNullOrBlank()) {
            gson.fromJson(usersJson, object : TypeToken<List<User>>() {}.type)
        } else {
            emptyList()
        }
    }

    data class User(val username: String, val password: String)
}
