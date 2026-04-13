package com.pfe.gestionetudiantmobile.ui.auth

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.pfe.gestionetudiantmobile.BuildConfig
import com.pfe.gestionetudiantmobile.data.api.RetrofitClient
import com.pfe.gestionetudiantmobile.databinding.ActivityLoginBinding
import com.pfe.gestionetudiantmobile.ui.common.RoleHomeRouter
import com.pfe.gestionetudiantmobile.util.ServerConfigStore
import com.pfe.gestionetudiantmobile.util.SessionStore

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()
    private lateinit var sessionStore: SessionStore
    private lateinit var serverConfigStore: ServerConfigStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionStore = SessionStore(this)
        serverConfigStore = ServerConfigStore(this)

        val savedBaseUrl = serverConfigStore.getBaseUrl()
        if (!savedBaseUrl.isNullOrBlank()) {
            RetrofitClient.switchBaseUrl(savedBaseUrl)
        }
        refreshServerInfo()

        sessionStore.getUser()?.let { user ->
            navigateByRole(user.role)
            return
        }

        binding.btnLogin.setOnClickListener {
            submitLogin()
        }
        binding.btnServerConfig.setOnClickListener { openServerConfigDialog() }

        binding.etPassword.setOnEditorActionListener { _, actionId, event ->
            val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                submitLogin()
                true
            } else {
                false
            }
        }

        binding.etUsername.setOnEditorActionListener { _, actionId, event ->
            val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_NEXT || enterPressed) {
                binding.etPassword.requestFocus()
                true
            } else {
                false
            }
        }

        viewModel.state.observe(this) { state ->
            binding.progressLogin.visibility = if (state.loading) android.view.View.VISIBLE else android.view.View.GONE
            binding.btnLogin.isEnabled = !state.loading

            state.errorMessage?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }

            state.authResponse?.let { auth ->
                auth.user?.let { user ->
                    serverConfigStore.saveBaseUrl(RetrofitClient.currentBaseUrl())
                    refreshServerInfo()
                    sessionStore.saveUser(user)
                    navigateByRole(user.role)
                }
            }
        }
    }

    private fun submitLogin() {
        viewModel.login(
            binding.etUsername.text?.toString().orEmpty(),
            binding.etPassword.text?.toString().orEmpty()
        )
    }

    private fun navigateByRole(role: String) {
        val intent = RoleHomeRouter.intentForRole(this, role)
        startActivity(intent)
        finish()
    }

    private fun refreshServerInfo() {
        val lanHint = BuildConfig.LAN_BASE_URL.trim().takeIf { it.isNotBlank() }
        binding.tvServerInfo.text = if (lanHint != null) {
            "Serveur: ${RetrofitClient.currentBaseUrl()} | LAN: $lanHint"
        } else {
            "Serveur: ${RetrofitClient.currentBaseUrl()}"
        }
    }

    private fun openServerConfigDialog() {
        val input = EditText(this).apply {
            setText(RetrofitClient.currentBaseUrl())
            hint = "http://192.168.1.8:8081/"
            setPadding(32, 24, 32, 24)
        }

        AlertDialog.Builder(this)
            .setTitle("Configurer le serveur backend")
            .setMessage("Exemple telephone: http://IP_DU_PC:8081/")
            .setView(input)
            .setNegativeButton("Annuler", null)
            .setNeutralButton("Par defaut") { _, _ ->
                serverConfigStore.clear()
                RetrofitClient.switchBaseUrl(BuildConfig.BASE_URL)
                refreshServerInfo()
                Toast.makeText(this, "Serveur reinitialise.", Toast.LENGTH_LONG).show()
            }
            .setPositiveButton("Enregistrer") { _, _ ->
                val raw = input.text?.toString().orEmpty().trim()
                if (raw.isBlank()) {
                    Toast.makeText(this, "URL serveur invalide.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                serverConfigStore.saveBaseUrl(raw)
                RetrofitClient.switchBaseUrl(raw)
                refreshServerInfo()
                Toast.makeText(this, "Serveur mis a jour.", Toast.LENGTH_LONG).show()
            }
            .show()
    }
}
