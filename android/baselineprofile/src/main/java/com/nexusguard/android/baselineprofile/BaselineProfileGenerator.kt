package com.nexusguard.android.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fase 5: Gerador de Baseline Profile que percorre o onboarding, o Início, o Limpar, os Jogos e o Sistema.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = "com.nexusguard.android",
            includeInStartupProfile = true
        ) {
            pressHome()
            startActivityAndWait()

            // 1. Onboarding (se estiver visível na primeira execução)
            val onboardingBtn = device.wait(
                Until.findObject(By.textContains("Aceitar princípios")),
                2_000
            )
            onboardingBtn?.click()
            device.waitForIdle()

            // 2. Ecrã Início
            device.wait(Until.findObject(By.text("Início")), 2_000)

            // 3. Separador Limpar
            device.findObject(By.text("Limpar"))?.click()
            device.waitForIdle()

            // 4. Separador Jogos
            device.findObject(By.text("Jogos"))?.click()
            device.waitForIdle()

            // 5. Separador Sistema
            device.findObject(By.text("Sistema"))?.click()
            device.waitForIdle()
        }
    }
}
