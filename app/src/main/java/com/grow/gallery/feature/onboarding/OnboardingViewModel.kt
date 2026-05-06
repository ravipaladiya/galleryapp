package com.grow.gallery.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.common.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
) : ViewModel() {

    fun completeOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            // Await DataStore flush before navigating — avoids re-showing onboarding on slow devices
            dataStoreManager.setOnboardingDone(true)
            onDone()
        }
    }
}
