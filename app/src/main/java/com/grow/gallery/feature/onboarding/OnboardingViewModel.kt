package com.grow.gallery.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
) : ViewModel() {

    private val _navigateToHome = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToHome: SharedFlow<Unit> = _navigateToHome.asSharedFlow()

    fun completeOnboarding() {
        // Use viewModelScope so the DataStore write is NOT cancelled when the composable
        // leaves composition (which happens when onFinish() pops the onboarding backstack entry).
        viewModelScope.launch {
            dataStoreManager.setOnboardingDone(true)
            _navigateToHome.emit(Unit)
        }
    }
}
