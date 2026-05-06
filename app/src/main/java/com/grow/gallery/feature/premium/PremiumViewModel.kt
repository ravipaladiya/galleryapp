package com.grow.gallery.feature.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.billing.BillingManager
import com.grow.gallery.core.billing.PremiumPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PremiumUiState(
    val plans: List<PremiumPlan> = emptyList(),
    val isPremium: Boolean = false,
    val isLoading: Boolean = false,
    val isRestoring: Boolean = false,
)

@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val billingManager: BillingManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PremiumUiState(plans = billingManager.availablePlans)
    )
    val uiState: StateFlow<PremiumUiState> = _uiState.asStateFlow()

    // Channel-based error events so the same message re-shows on repeated taps
    private val _errorEvents = Channel<String>(Channel.BUFFERED)
    val errorEvents: Flow<String> = _errorEvents.receiveAsFlow()

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
                    _uiState.update { it.copy(isLoading = false) }
                    _errorEvents.send(e.message ?: "Subscription failed")
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
                    _uiState.update { it.copy(isRestoring = false) }
                    _errorEvents.send(e.message ?: "Restore failed")
                }
                .onSuccess {
                    _uiState.update { it.copy(isRestoring = false) }
                }
        }
    }
}
