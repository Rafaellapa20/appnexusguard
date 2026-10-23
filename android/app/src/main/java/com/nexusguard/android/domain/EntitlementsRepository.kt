package com.nexusguard.android.domain

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fase 9: Candidatas a funcionalidades premium no futuro.
 * Agora não há cobrança: todas as funcionalidades estão 100 % desbloqueadas nos dois flavors (`full` e `play`),
 * sem paywalls, cadeados, selos «Premium» nem anúncios.
 */
enum class PremiumFeature(val labelPt: String) {
    SIMILAR_AND_BLURRY_PHOTOS("Fotos semelhantes e desfocadas"),
    PHOTO_OPTIMIZER("Otimizador de fotos"),
    APP_IMPACT_7D_30D("Impacto das apps a 7 e 30 dias"),
    SCHEDULED_CLEAN("Limpeza agendada"),
    AUTO_HIBERNATION("Hibernação automática"),
    AUTO_BATTERY_SAVER("Perfil de poupança automático"),
    MONTHLY_REPORT("Relatório mensal"),
    WIDGET_4X2("Widget 4×2"),
    THEMES("Temas")
}

interface EntitlementsRepository {
    fun isPremiumFeatureEnabled(feature: PremiumFeature): Boolean
}

/**
 * Implementação atual: devolve sempre `true` para todas as funcionalidades.
 */
@Singleton
class AllUnlockedEntitlements @Inject constructor() : EntitlementsRepository {
    override fun isPremiumFeatureEnabled(feature: PremiumFeature): Boolean = true
}
