package com.autobuy.feature.recorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobuy.core.accessibility.module.TouchEventRecorder
import com.autobuy.core.data.model.RecipeStep
import com.autobuy.core.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecorderViewModel @Inject constructor(
    private val touchEventRecorder: TouchEventRecorder,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    val isRecording: StateFlow<Boolean> = touchEventRecorder.isRecording
    val recordedSteps: StateFlow<List<RecipeStep>> = touchEventRecorder.recordedSteps

    val shopId = MutableStateFlow("custom_shop")
    val shopName = MutableStateFlow("신규 테스트 쇼핑몰")
    val packageName = MutableStateFlow("browser")

    private val _saveSuccessEvent = MutableSharedFlow<Unit>()
    val saveSuccessEvent: SharedFlow<Unit> = _saveSuccessEvent

    fun toggleRecording() {
        if (isRecording.value) {
            val recipe = touchEventRecorder.stopRecording()
            viewModelScope.launch {
                profileRepository.saveProfile(recipe, isBuiltin = false)
                _saveSuccessEvent.emit(Unit)
            }
        } else {
            touchEventRecorder.startRecording(
                shopId = shopId.value.ifBlank { "custom_shop" },
                shopName = shopName.value.ifBlank { "신규 테스트 쇼핑몰" },
                packageName = packageName.value.ifBlank { "browser" }
            )
        }
    }
}
