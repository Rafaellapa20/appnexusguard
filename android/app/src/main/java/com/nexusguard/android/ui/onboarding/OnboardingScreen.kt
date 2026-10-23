package com.nexusguard.android.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexusguard.android.ui.components.GaugeShieldLogo
import com.nexusguard.android.ui.components.NexusBrandWordmark
import com.nexusguard.android.ui.components.NexusCard
import com.nexusguard.android.ui.components.NexusFlavorPill
import com.nexusguard.android.ui.components.NexusPrimaryGradientButton
import com.nexusguard.android.ui.theme.NexusIcons
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme

data class OnboardingPrinciple(
    val number: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color
)

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit
) {
    val colors = NexusTheme.colors
    val principles = listOf(
        OnboardingPrinciple(
            number = "01",
            title = "Pré-visualização e confirmação sempre",
            description = "Nunca apaga fotos, documentos ou conversas sem pré-visualização obrigatória e a tua confirmação explícita.",
            icon = NexusIcons.Visibility,
            accentColor = colors.accent2
        ),
        OnboardingPrinciple(
            number = "02",
            title = "Lixo do sistema como quarentena",
            description = "Tudo o que apagas vai para o Lixo do sistema (MediaStore.createTrashRequest, Android 11+), funcionando como quarentena que podes restaurar.",
            icon = Icons.Outlined.Delete,
            accentColor = colors.ok
        ),
        OnboardingPrinciple(
            number = "03",
            title = "Zero números falsos",
            description = "Nunca promete números inventados (FPS, «+40 % de velocidade» ou «RAM libertada» fictícia). Mostra apenas o que é medido de verdade pelo Android.",
            icon = NexusIcons.Speed,
            accentColor = colors.accent
        ),
        OnboardingPrinciple(
            number = "04",
            title = "Permissões só quando necessárias",
            description = "Pede cada permissão apenas no momento em que é precisa, com uma explicação clara. A app funciona em modo reduzido sem ela.",
            icon = Icons.Outlined.Lock,
            accentColor = colors.warn
        ),
        OnboardingPrinciple(
            number = "05",
            title = "Histórico completo e 100 % local",
            description = "Tudo o que altera fica registado no Histórico. Nada sai do teu telemóvel: sem analytics, sem anúncios e sem permissão de Internet.",
            icon = NexusIcons.History,
            accentColor = colors.ok
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Cabeçalho da marca
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GaugeShieldLogo(size = 44.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        NexusBrandWordmark(fontSizeSp = 24)
                        Text(
                            text = "Irmão da versão Windows · Android",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textDim
                        )
                    }
                }
                NexusFlavorPill()
            }

            // Introdução
            NexusCard(
                backgroundColor = colors.bgRaised,
                borderColor = colors.borderStrong,
                contentPadding = PaddingValues(16.dp)
            ) {
                Text(
                    text = "Os nossos princípios",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "O Nexus Guard foi desenhado para cuidar do teu telemóvel com transparência técnica total e respeito pelos limites reais do sistema Android.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textDim
                )
            }

            // Lista de princípios
            principles.forEach { principle ->
                NexusCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceAlt)
                                .border(1.dp, principle.accentColor.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = principle.icon,
                                contentDescription = null,
                                tint = principle.accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = principle.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.text,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = principle.number,
                                    style = NexusMonoTextStyles.metricSmall,
                                    color = principle.accentColor
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = principle.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textDim
                            )
                        }
                    }
                }
            }

            // Limites honestos do Android
            NexusCard(
                backgroundColor = colors.surfaceAlt,
                borderColor = colors.borderStrong,
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = "Transparência: o que o Android sem root não permite",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.warn
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Não mata apps em primeiro plano de outros nem limpa as respetivas caches uma a uma silenciosamente.\n" +
                        "• Não lê sensores bloqueados de temperatura do CPU: apresenta a temperatura real da bateria, o estado térmico oficial (PowerManager) e a margem térmica.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim
                )
            }
        }

        // Barra inferior com botão principal em gradiente
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgRaised)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            NexusPrimaryGradientButton(
                text = "Aceitar princípios e continuar",
                leadingIcon = Icons.Outlined.CheckCircle,
                onClick = onFinishOnboarding,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
