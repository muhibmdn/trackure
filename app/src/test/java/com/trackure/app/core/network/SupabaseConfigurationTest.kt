package com.trackure.app.core.network

import org.junit.Assert.*
import org.junit.Test

class SupabaseConfigurationTest {
    @Test fun normalizesProjectUrlAndBothRestSuffixes() {
        listOf("https://project.supabase.co", "https://project.supabase.co/",
            "https://project.supabase.co/rest/v1", "https://project.supabase.co/rest/v1/",
            "  \" https://project.supabase.co/rest/v1/ \"  ", " 'https://project.supabase.co/' ")
            .forEach { assertEquals("https://project.supabase.co", SupabaseConfiguration.normalizeUrl(it)) }
    }

    @Test fun acceptsPublishableAndLegacyKeysWithoutJwtValidation() {
        listOf("sb_publishable_test", "eyJtest.jwt.signature", "  ' sb_publishable_test '  ")
            .forEach { assertTrue(SupabaseConfiguration.isConfigured("https://project.supabase.co/rest/v1/", it)) }
        assertTrue(SupabaseConfiguration.isConfigured("https://project.supabase.co", "any-nonblank-key"))
        assertFalse(SupabaseConfiguration.isConfigured("http://project.supabase.co", "sb_publishable_test"))
        assertFalse(SupabaseConfiguration.isConfigured("", "sb_publishable_test"))
        assertFalse(SupabaseConfiguration.isConfigured("https://project.supabase.co", " \" \" "))
    }
}
