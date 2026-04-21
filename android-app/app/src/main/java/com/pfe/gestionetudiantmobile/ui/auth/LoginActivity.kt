package com.pfe.gestionetudiantmobile.ui.auth

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pfe.gestionetudiantmobile.BuildConfig
import com.pfe.gestionetudiantmobile.data.api.RetrofitClient
import com.pfe.gestionetudiantmobile.data.repository.AuthRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityLoginBinding
import com.pfe.gestionetudiantmobile.ui.common.RoleHomeRouter
import com.pfe.gestionetudiantmobile.util.ApiResult
import com.pfe.gestionetudiantmobile.util.MobileApiConfig
import com.pfe.gestionetudiantmobile.util.ServerConfigStore
import com.pfe.gestionetudiantmobile.util.SessionStore
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()
    private val authRepository = AuthRepository()
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
            runCatching {
                RetrofitClient.switchBaseUrl(savedBaseUrl)
            }.onFailure {
                serverConfigStore.clear()
                RetrofitClient.switchBaseUrl(BuildConfig.BASE_URL)
            }
        }
        refreshServerInfo()

        binding.btnLogin.setOnClickListener {
            submitLogin()
        }
        binding.btnServerConfig.setOnClickListener { openServerConfigDialog() }

        binding.etUsername.setOnEditorActionListener { _, actionId, event ->
            handleLoginEditorAction(actionId, event)
        }
        binding.etPassword.setOnEditorActionListener { _, actionId, event ->
            handleLoginEditorAction(actionId, event)
        }
        binding.etUsername.setOnKeyListener { _, keyCode, event ->
            handleEnterKey(keyCode, event)
        }
        binding.etPassword.setOnKeyListener { _, keyCode, event ->
            handleEnterKey(keyCode, event)
        }

        val clearErrorWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = clearErrors()
            override fun afterTextChanged(s: Editable?) = Unit
        }
        binding.etUsername.addTextChangedListener(clearErrorWatcher)
        binding.etPassword.addTextChangedListener(clearErrorWatcher)

        viewModel.state.observe(this) { state ->
            setLoading(state.loading, if (state.authResponse == null) "Connexion en cours..." else "Ouverture de votre espace...")

            state.errorMessage?.let {
                showLoginError(it, state.errorTarget)
            }

            state.authResponse?.let { auth ->
                auth.user?.let { user ->
                    clearErrors()
                    serverConfigStore.saveBaseUrl(RetrofitClient.currentBaseUrl())
                    refreshServerInfo()
                    sessionStore.saveAuthenticatedSession(user, RetrofitClient.currentBaseUrl())
                    navigateByRole(user.role)
                }
            }
        }

        sessionStore.getUser()?.let {
            validateStoredSession()
        }
    }

    private fun validateStoredSession() {
        setLoading(true, "Verification de la session...")

        lifecycleScope.launch {
            when (val result = authRepository.me()) {
                is ApiResult.Success -> {
                    val user = result.data.user
                    if (result.data.authenticated && user != null) {
                        sessionStore.saveAuthenticatedSession(user, RetrofitClient.currentBaseUrl())
                        navigateByRole(user.role)
                    } else {
                        handleExpiredSession()
                    }
                }
                is ApiResult.Error -> handleExpiredSession()
            }
        }
    }

    private fun handleExpiredSession() {
        RetrofitClient.clearSession()
        sessionStore.clear()
        setLoading(false)
        Toast.makeText(this, "Session expiree. Reconnectez-vous.", Toast.LENGTH_LONG).show()
    }

    private fun submitLogin() {
        val username = binding.etUsername.text?.toString().orEmpty().trim()
        val password = binding.etPassword.text?.toString().orEmpty()
        clearErrors()

        if (username.isBlank()) {
            binding.tilUsername.error = "Nom utilisateur obligatoire"
            binding.tvLoginError.text = "Nom utilisateur obligatoire."
            binding.tvLoginError.visibility = View.VISIBLE
            binding.etUsername.requestFocus()
            return
        }

        if (password.isBlank()) {
            binding.tilPassword.error = "Mot de passe obligatoire"
            binding.tvLoginError.text = "Mot de passe obligatoire."
            binding.tvLoginError.visibility = View.VISIBLE
            binding.etPassword.requestFocus()
            return
        }

        hideKeyboard()
        viewModel.login(username, password)
    }

    private fun navigateByRole(role: String) {
        val intent = RoleHomeRouter.intentForRole(this, role)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun refreshServerInfo() {
        val lanHint = BuildConfig.LAN_BASE_URL.trim().takeIf { it.isNotBlank() }
        binding.tvServerInfo.text = if (lanHint != null) {
            "Serveur actif: ${RetrofitClient.currentBaseUrl()}\nLAN: ${MobileApiConfig.normalizeBaseUrl(lanHint)}"
        } else {
            "Serveur actif: ${RetrofitClient.currentBaseUrl()}"
        }
    }

    private fun openServerConfigDialog() {
        val input = EditText(this).apply {
            setText(RetrofitClient.currentBaseUrl())
            hint = "http://192.168.1.8:8081/"
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
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

                runCatching {
                    RetrofitClient.switchBaseUrl(raw)
                    serverConfigStore.saveBaseUrl(RetrofitClient.currentBaseUrl())
                    refreshServerInfo()
                }.onSuccess {
                    Toast.makeText(this, "Serveur mis a jour.", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(
                        this,
                        it.message ?: "URL serveur invalide.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }

    private fun handleLoginEditorAction(actionId: Int, event: KeyEvent?): Boolean {
        val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
        val submitAction = actionId == EditorInfo.IME_ACTION_DONE ||
            actionId == EditorInfo.IME_ACTION_GO ||
            actionId == EditorInfo.IME_ACTION_SEND ||
            enterPressed

        return if (submitAction) {
            submitLogin()
            true
        } else {
            false
        }
    }

    private fun handleEnterKey(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                submitLogin()
            }
            return true
        }
        return false
    }

    private fun setLoading(loading: Boolean, message: String = "Connexion en cours...") {
        binding.layoutLoginStatus.visibility = if (loading) View.VISIBLE else View.GONE
        binding.tvLoadingMessage.text = message
        binding.btnLogin.isEnabled = !loading
        binding.btnServerConfig.isEnabled = !loading
        binding.etUsername.isEnabled = !loading
        binding.etPassword.isEnabled = !loading
        binding.btnLogin.text = if (loading) "Connexion..." else getString(com.pfe.gestionetudiantmobile.R.string.sign_in)
    }

    private fun showLoginError(message: String, target: LoginErrorTarget) {
        binding.tvLoginError.text = message
        binding.tvLoginError.visibility = View.VISIBLE
        when (target) {
            LoginErrorTarget.USERNAME -> binding.tilUsername.error = message
            LoginErrorTarget.PASSWORD -> binding.tilPassword.error = message
            LoginErrorTarget.SERVER,
            LoginErrorTarget.FORM -> Unit
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun clearErrors() {
        binding.tilUsername.error = null
        binding.tilPassword.error = null
        binding.tvLoginError.visibility = View.GONE
    }

    private fun hideKeyboard() {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
}
