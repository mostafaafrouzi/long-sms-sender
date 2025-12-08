package com.afrouzi.longsmssender.data.model

sealed class SmsResult {
    data object Success : SmsResult()
    data class Error(val message: String) : SmsResult()
}

