package com.example.core.common

sealed interface Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>
    data class Error(val message: String, val throwable: Throwable? = null) : Resource<Nothing>
    data object Loading : Resource<Nothing>
    data object Idle : Resource<Nothing>
}
