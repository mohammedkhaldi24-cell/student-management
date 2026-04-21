package com.pfe.gestionetudiantmobile.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfe.gestionetudiantmobile.data.model.AuthResponse
import com.pfe.gestionetudiantmobile.data.repository.AuthRepository
import com.pfe.gestionetudiantmobile.util.ApiResult
import kotlinx.coroutines.launch

data class LoginUiState(
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val errorTarget: LoginErrorTarget = LoginErrorTarget.FORM,
    val authResponse: AuthResponse? = null
)

enum class LoginErrorTarget {
    USERNAME,
    PASSWORD,
    SERVER,
    FORM
}

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _state = MutableLiveData(LoginUiState())
    val state: LiveData<LoginUiState> = _state

    fun login(username: String, password: String) {
        if (_state.value?.loading == true) return

        if (username.isBlank() || password.isBlank()) {
            _state.value = LoginUiState(
                errorMessage = "Renseignez votre nom utilisateur et votre mot de passe.",
                errorTarget = LoginErrorTarget.FORM
            )
            return
        }

        _state.value = LoginUiState(loading = true)

        viewModelScope.launch {
            when (val result = authRepository.login(username, password)) {
                is ApiResult.Success -> {
                    if (result.data.authenticated && result.data.user != null) {
                        _state.value = LoginUiState(authResponse = result.data)
                    } else {
                        _state.value = LoginUiState(
                            errorMessage = friendlyError(result.data.message),
                            errorTarget = targetFor(result.data.message)
                        )
                    }
                }

                is ApiResult.Error -> {
                    _state.value = LoginUiState(
                        errorMessage = friendlyError(result.message),
                        errorTarget = targetFor(result.message)
                    )
                }
            }
        }
    }

    private fun friendlyError(message: String): String {
        val normalized = message.lowercase()
        return when {
            "identifiants" in normalized ||
                "bad credentials" in normalized ||
                "invalid" in normalized -> "Nom utilisateur ou mot de passe incorrect."
            "desactive" in normalized -> "Ce compte est desactive. Contactez l'administration."
            "404" in normalized -> "API mobile introuvable. Verifiez le serveur et le port backend."
            "405" in normalized -> "Endpoint login incorrect. L'app doit appeler POST /api/mobile/auth/login."
            "connexion impossible" in normalized -> message
            "serveur introuvable" in normalized -> message
            "timeout" in normalized ||
                "ne repond pas" in normalized -> "Le serveur ne repond pas. Reessayez ou verifiez le reseau."
            else -> message.ifBlank { "Connexion impossible. Reessayez." }
        }
    }

    private fun targetFor(message: String): LoginErrorTarget {
        val normalized = message.lowercase()
        return when {
            "identifiants" in normalized ||
                "mot de passe" in normalized ||
                "password" in normalized ||
                "bad credentials" in normalized -> LoginErrorTarget.PASSWORD
            "username" in normalized ||
                "nom utilisateur" in normalized -> LoginErrorTarget.USERNAME
            "serveur" in normalized ||
                "api" in normalized ||
                "404" in normalized ||
                "405" in normalized ||
                "connexion" in normalized -> LoginErrorTarget.SERVER
            else -> LoginErrorTarget.FORM
        }
    }
}
