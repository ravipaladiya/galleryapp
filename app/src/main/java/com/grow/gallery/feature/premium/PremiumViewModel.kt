package com.grow.gallery.feature.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.billing.BillingManager
import com.grow.gallery.core.billing.PremiumPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserMessage(val id: Long, val text: String)

data class PremiumUiState(
    val plans: List<PremiumPlan> = emptyList(),
    val isPremium: Boolean = false,
    val isLoading: Boolean = false,
    val isRestoring: Boolean = false,
    val userMessages: List<UserMessage> = emptyList(),
)

@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val billingManager: BillingManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PremiumUiState(plans = billingManager.availablePlans)
    )
    val uiState: StateFlow<PremiumUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            billingManager.isPremium.collect { isPremium ->
                _uiState.update { it.copy(isPremium = isPremium) }
            }
        }
    }

    fun subscribe(planId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            billingManager.purchasePlan(planId)
                .onFailure { e ->
                    emitMessage(e.message ?: "Purchase failed. Please try again.")
                    _uiState.update { it.copy(isLoading = false) }
                }
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                }
        }
    }

    fun restorePurchase() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true) }
            billingManager.restorePurchases()
                .onFailure { e ->
                    emitMessage(e.message ?: "No purchases found to restore.")
                    _uiState.update { it.copy(isRestoring = false) }
                }
                .onSuccess {
                    emitMessage("Purchases restored successfully.")
                    _uiState.update { it.copy(isRestoring = false) }
                }
        }
    }

    fun messageShown(id: Long) {
        _uiState.update { state ->
            state.copy(userMessages = state.userMessages.filterNot { it.id == id })
        }
    }

    private fun emitMessage(text: String) {
        _uiState.update { state ->
            state.copy(userMessages = state.userMessages + UserMessage(System.currentTimeMillis(), text))
        }
    }
}
