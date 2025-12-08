package com.afrouzi.longsmssender.utils

object NumberValidator {

    private val IRAN_PHONE_REGEX = Regex("^(\\+98|0098|0)?9\\d{9}$")
    private val INTERNATIONAL_PHONE_REGEX = Regex("^\\+?\\d{10,15}$")

    fun isValidPhoneNumber(input: String): Boolean {
        val normalized = normalizeNumber(input)
        if (normalized.isBlank()) return false
        
        // Basic digit check (after normalization, should be only digits and +)
        if (!normalized.all { it.isDigit() || it == '+' }) return false

        // Check specific formats
        return IRAN_PHONE_REGEX.matches(normalized) || INTERNATIONAL_PHONE_REGEX.matches(normalized)
    }

    fun normalizeNumber(input: String): String {
        // 1. Convert Persian/Arabic digits to English
        var result = input
            .replace('۰', '0').replace('۱', '1').replace('۲', '2').replace('۳', '3').replace('۴', '4')
            .replace('۵', '5').replace('۶', '6').replace('۷', '7').replace('۸', '8').replace('۹', '9')
            .replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3').replace('٤', '4')
            .replace('٥', '5').replace('٦', '6').replace('٧', '7').replace('٨', '8').replace('٩', '9')

        // 2. Remove all non-digit characters except +
        result = result.filter { it.isDigit() || it == '+' }
        
        return result
    }
}
