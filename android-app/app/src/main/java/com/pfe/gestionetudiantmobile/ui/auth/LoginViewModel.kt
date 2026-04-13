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
    val authResponse: AuthResponse? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _state = MutableLiveData(LoginUiState())
    val state: LiveData<LoginUiState> = _state

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _state.value = LoginUiState(errorMessage = "Veuillez remplir username et password")
            return
        }

        _state.value = LoginUiState(loading = true)

        viewModelScope.launch {
            when (val result = authRepository.login(username, password)) {
                is ApiResult.Success -> {
                    if (result.data.authenticated && result.data.user != null) {
                        _state.value = LoginUiState(authResponse = result.data)
                    } else {
                        _state.value = LoginUiState(errorMessage = result.data.message)
                    }
                }

                is ApiResult.Error -> {
                    _state.value = LoginUiState(errorMessage = result.message)
                }
            }
        }
    }
}
