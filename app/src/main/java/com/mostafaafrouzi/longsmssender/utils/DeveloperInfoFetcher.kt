package com.mostafaafrouzi.longsmssender.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.HttpURLConnection
import java.io.BufferedReader
import java.io.InputStreamReader

object DeveloperInfoFetcher {
    
    suspend fun fetchDeveloperInfo(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val language = LocaleManager.getCurrentLanguage(context)
            val url = if (language == "fa") {
                "https://afrouzi.ir"
            } else {
                "https://afrouzi.ir/en"
            }
            
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line).append("\n")
                }
                reader.close()
                connection.disconnect()
                
                // Extract developer info from HTML
                val html = response.toString()
                val extractedInfo = extractDeveloperInfo(html, language)
                if (extractedInfo.isNotEmpty() && extractedInfo != getDefaultInfo(language)) {
                    extractedInfo
                } else {
                    getDefaultInfo(language)
                }
            } else {
                getDefaultInfo(language)
            }
        } catch (e: Exception) {
            // Return default info on error
            val language = LocaleManager.getCurrentLanguage(context)
            getDefaultInfo(language)
        }
    }
    
    private fun extractDeveloperInfo(html: String, language: String): String {
        try {
            // Try to extract from meta description
            val metaDescriptionRegex = """<meta\s+name=["']description["']\s+content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
            val metaMatch = metaDescriptionRegex.find(html)
            if (metaMatch != null) {
                val description = metaMatch.groupValues[1]
                if (description.length > 20) {
                    return description
                }
            }
            
            // Try to extract from og:description
            val ogDescriptionRegex = """<meta\s+property=["']og:description["']\s+content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
            val ogMatch = ogDescriptionRegex.find(html)
            if (ogMatch != null) {
                val description = ogMatch.groupValues[1]
                if (description.length > 20) {
                    return description
                }
            }
            
            // Try to extract from main content or article
            val contentRegex = Regex("""<(main|article|section)[^>]*>(.{0,1000})</(main|article|section)>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val contentMatch = contentRegex.find(html)
            if (contentMatch != null) {
                val content = contentMatch.groupValues[2]
                // Remove HTML tags and clean up
                val cleanContent = content
                    .replace(Regex("""<script[^>]*>.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
                    .replace(Regex("""<style[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                
                if (cleanContent.length > 50) {
                    return cleanContent.take(300)
                }
            }
            
            // Try to extract from paragraph tags
            val paragraphRegex = """<p[^>]*>([^<]+)</p>""".toRegex(RegexOption.IGNORE_CASE)
            val paragraphs = paragraphRegex.findAll(html).map { it.groupValues[1].trim() }.filter { it.length > 30 }
            if (paragraphs.any()) {
                return paragraphs.take(2).joinToString("\n\n")
            }
        } catch (e: Exception) {
            // Fall through to default
        }
        
        return getDefaultInfo(language)
    }
    
    private fun getDefaultInfo(language: String): String {
        return if (language == "fa") {
            "طراح و توسعه‌دهنده وب، اپلیکیشن و سیستم‌های نرم‌افزاری"
        } else {
            "Web &amp; Software Developer"
        }
    }
}
