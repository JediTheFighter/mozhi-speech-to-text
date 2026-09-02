package com.mozhi.core.common

sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
    data class Success<T>(val value: T) : LoadState<T>
    data class Error(val message: String, val cause: Throwable? = null) : LoadState<Nothing>
}
