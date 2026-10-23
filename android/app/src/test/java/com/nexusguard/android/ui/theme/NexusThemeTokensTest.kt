package com.nexusguard.android.ui.theme

import androidx.compose.ui.graphics.Color
import com.nexusguard.android.ui.navigation.NexusTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusThemeTokensTest {

    @Test
    fun `dark theme tokens match Nexus Guard Windows palette`() {
        val tokens = DarkNexusColors
        assertTrue(tokens.isDark)
        assertEquals(Color(0xFF0C0F16), tokens.bg)
        assertEquals(Color(0xFF11151E), tokens.bgRaised)
        assertEquals(Color(0xFF161B26), tokens.surface)
        assertEquals(Color(0xFF1C2230), tokens.surfaceAlt)
        assertEquals(Color(0xFF262E3E), tokens.border)
        assertEquals(Color(0xFF354057), tokens.borderStrong)
        assertEquals(Color(0xFFE8ECF4), tokens.text)
        assertEquals(Color(0xFF98A2B8), tokens.textDim)
        assertEquals(Color(0xFF6A7488), tokens.textMuted)
        assertEquals(Color(0xFF7C5CF6), tokens.accent)
        assertEquals(Color(0xFF22D3EE), tokens.accent2)
        assertEquals(Color(0xFF34D399), tokens.ok)
        assertEquals(Color(0xFFFBBF24), tokens.warn)
        assertEquals(Color(0xFFF87171), tokens.danger)
    }

    @Test
    fun `bottom navigation has Portuguese tabs including Apps impact`() {
        val tabs = NexusTab.entries
        assertEquals(6, tabs.size)
        assertEquals(listOf("Início", "Limpar", "Apps", "Jogos", "Bateria", "Sistema"), tabs.map { it.label })
    }
}
