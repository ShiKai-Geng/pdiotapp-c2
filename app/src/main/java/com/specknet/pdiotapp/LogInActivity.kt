package com.specknet.pdiotapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity


class LoginActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var prefsHelper: SharedPreferencesHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)

        usernameEditText = findViewById(R.id.editTextUsername)
        passwordEditText = findViewById(R.id.editTextPassword)
        loginButton = findViewById(R.id.buttonLogin)
        registerButton = findViewById(R.id.buttonRegister)
        prefsHelper = SharedPreferencesHelper(this)

        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()
            val users = prefsHelper.getUsers()

            // Check if entered credentials match any registered user
            val matchedUser = users.find { it.username == username && it.password == password }

            if (matchedUser != null) {
                Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()
                // Start the MainActivity
                val intent = Intent(this, MainActivity::class.java)
                // Pass any necessary data
                intent.putExtra("username", username)
                startActivity(intent)
                finish() // Close the login activity
            } else {
                Toast.makeText(this, "Invalid Credentials", Toast.LENGTH_SHORT).show()
            }
        }

        registerButton.setOnClickListener {
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()
            prefsHelper.saveUser(username, password)
            Toast.makeText(this, "User Registered", Toast.LENGTH_SHORT).show()
        }
    }
}
