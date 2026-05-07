package com.grow.gallery.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.common.DataStoreManager
import com.grow.gallery.core.security.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppLockUiState(
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val hasPinSet: Boolean = false,
    val pinSetupRequired: Boolean = false,
    val changePinStep: ChangePinStep = ChangePinStep.NONE,
    val pinError: String? = null,
    val snackbarMessage: String? = null,
)

enum class ChangePinStep { NONE, ENTER_OLD, ENTER_NEW, CONFIRM_NEW }

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
    private val vaultManager: VaultManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    private var newPinTemp: String = ""

    init {
        _uiState.update { it.copy(isBiometricAvailable = vaultManager.isBiometricAvailable()) }
        viewModelScope.launch {
            combine(
                dataStoreManager.appLockEnabled,
                dataStoreManager.appLockBiometric,
                vaultManager.hasPinSet,
            ) { appLock, biometric, hasPinSet ->
                _uiState.update {
                    it.copy(
                        appLockEnabled = appLock,
                        biometricEnabled = biometric,
                        hasPinSet = hasPinSet,
                    )
                }
            }.collect()
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        if (enabled && !_uiState.value.hasPinSet) {
            // Cannot enable lock without a PIN — surface setup prompt
            _uiState.update { it.copy(pinSetupRequired = true) }
            return
        }
        viewModelScope.launch { dataStoreManager.setAppLockEnabled(enabled) }
    }

    fun dismissPinSetupRequired() {
        _uiState.update { it.copy(pinSetupRequired = false) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStoreManager.setAppLockBiometric(enabled) }
    }

    /** Start Change PIN flow */
    fun startChangePin() {
        val step = if (_uiState.value.hasPinSet) ChangePinStep.ENTER_OLD else ChangePinStep.ENTER_NEW
        _uiState.update { it.copy(changePinStep = step, pinError = null) }
        newPinTemp = ""
    }

    fun cancelChangePin() {
        _uiState.update { it.copy(changePinStep = ChangePinStep.NONE, pinError = null) }
        newPinTemp = ""
    }

    fun submitOldPin(oldPin: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val correct = vaultManager.verifyPin(oldPin)
            if (correct) {
                _uiState.update { it.copy(changePinStep = ChangePinStep.ENTER_NEW, pinError = null) }
            } else {
                _uiState.update { it.copy(pinError = "Incorrect PIN") }
            }
        }
    }

    fun submitNewPin(newPin: String) {
        if (newPin.length < 4) {
            _uiState.update { it.copy(pinError = "PIN must be 4 digits") }
            return
        }
        newPinTemp = newPin
        _uiState.update { it.copy(changePinStep = ChangePinStep.CONFIRM_NEW, pinError = null) }
    }

    fun confirmNewPin(confirmPin: String) {
        if (confirmPin != newPinTemp) {
            _uiState.update { it.copy(pinError = "PINs do not match", changePinStep = ChangePinStep.ENTER_NEW) }
            newPinTemp = ""
            return
        }
        viewModelScope.launch {
            val success = withContext(Dispatchers.Default) { vaultManager.setupPin(newPinTemp) }
            newPinTemp = ""
            if (success) {
                _uiState.update {
                    it.copy(
                        changePinStep = ChangePinStep.NONE,
                        hasPinSet = true,
                        pinError = null,
                        snackbarMessage = "PIN updated successfully",
                    )
                }
            } else {
                _uiState.update { it.copy(pinError = "Failed to save PIN", changePinStep = ChangePinStep.ENTER_NEW) }
            }
        }
    }

    fun onSnackbarShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
