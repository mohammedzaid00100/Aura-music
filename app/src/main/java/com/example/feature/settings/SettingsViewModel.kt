package com.example.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel : ViewModel() {

    private val _streamingQuality = MutableStateFlow("High (320kbps)")
    val streamingQuality: StateFlow<String> = _streamingQuality.asStateFlow()

    private val _normalizeVolume = MutableStateFlow(true)
    val normalizeVolume: StateFlow<Boolean> = _normalizeVolume.asStateFlow()

    private val _dataSaver = MutableStateFlow(false)
    val dataSaver: StateFlow<Boolean> = _dataSaver.asStateFlow()

    private val _cacheSize = MutableStateFlow("42.8 MB")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    fun setStreamingQuality(quality: String) {
        _streamingQuality.value = quality
    }

    fun toggleNormalizeVolume() {
        _normalizeVolume.value = !_normalizeVolume.value
    }

    fun toggleDataSaver() {
        _dataSaver.value = !_dataSaver.value
    }

    fun clearCache() {
        _cacheSize.value = "0.0 MB"
    }

    companion object {
        fun provideFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel() as T
            }
        }
    }
}
