package com.trackure.app.core.network

internal object SupabaseConfiguration {
    fun clean(value: String): String = value.trim().trim('"', '\'').trim()
    fun normalizeUrl(value: String): String = clean(value).trimEnd('/').removeSuffix("/rest/v1").trimEnd('/')
    fun isConfigured(url: String, key: String): Boolean =
        normalizeUrl(url).startsWith("https://") && clean(key).isNotBlank()
}
