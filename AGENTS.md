# Nexus Guard Android — regras do projeto

Este projeto é o **Nexus Guard para Android**: um otimizador do próprio telemóvel, irmão da versão Windows. Segue sempre estas regras.

## Stack
- Kotlin, Jetpack Compose com Material 3 (sem XML de layouts), minSdk 26, targetSdk 35.
- Arquitetura MVVM + UDF: ViewModel + StateFlow, Hilt, Room (histórico), DataStore (definições), WorkManager (tarefas agendadas) e Navigation Compose.
- Interface em português de Portugal. Nada sai do telemóvel: sem analytics nem anúncios, e permissão de INTERNET só se for mesmo necessária.
- Pasta do projeto: `android/` na raiz do repositório, com o package `com.nexusguard.android`.

## Princípios (os mesmos da versão Windows; põe-nos no onboarding)
- Nunca apaga fotos, documentos ou conversas sem pré-visualização e confirmação.
- O que se apaga vai para o **Lixo do sistema** (`MediaStore.createTrashRequest`, API 30+), que funciona como quarentena e se pode restaurar.
- **Nunca promete números falsos** (FPS, «+40 % de velocidade», «RAM libertada» inventada). Mostra só o que é medido.
- Pede cada permissão no momento em que é precisa, com uma explicação. A app funciona em modo reduzido sem ela.
- Tudo o que altera fica no Histórico.

## Sê honesto com os limites do Android (muito importante)
Sem root, o Android não deixa:
- matar apps de outros;
- limpar a cache de outras apps uma a uma;
- ler a temperatura do CPU.

Não finjas que fazes estas coisas. Implementa o que é real:
- **Saúde do sistema:** pontuação de 0 a 100, a partir do armazenamento livre, do estado térmico, da saúde e temperatura da bateria, das apps sem uso há mais de 30 dias e da memória disponível (`ActivityManager.MemoryInfo`).
- **Temperaturas ao vivo:**
  - temperatura e saúde da bateria (`BatteryManager`, `ACTION_BATTERY_CHANGED`);
  - estado térmico (`PowerManager.currentThermalStatus`, API 29+), com nomes PT: Normal / Ligeiro / Moderado / Grave / Crítico;
  - `getThermalHeadroom` (API 30+), apresentado como «margem térmica».
  - Se algum valor faltar, diz porquê. Nunca mostres um zero.
- **Limpeza** (com pré-visualização obrigatória e filtros por categoria):
  - APK já instalados na pasta Transferências;
  - ficheiros grandes (mais de 100 MB);
  - transferências antigas (mais de 90 dias);
  - duplicados, por tamanho e hash;
  - pastas vazias;
  - capturas de ecrã antigas.
  Tudo via MediaStore / SAF. Pede MANAGE_EXTERNAL_STORAGE só para categorias que o exijam, como opção «avançada» com explicação. Para as caches das outras apps: botão que abre `StorageManager.ACTION_CLEAR_APP_CACHE` (API 30+, o sistema pede confirmação). Sem isso, abre as definições da app.
- **Armazenamento:** espaço usado por categoria e as apps ordenadas por tamanho (`StorageStatsManager`, com `PACKAGE_USAGE_STATS`).
- **Apps em fundo:**
  - lista das apps mais ativas e das que não são usadas há muito tempo (`UsageStatsManager`);
  - ações «Parar» (abre os detalhes da app para Forçar paragem), «Desinstalar» e «Restringir bateria»;
  - `killBackgroundProcesses` só nas apps já em fundo, e dito como é.
- **Modo jogo (boost):**
  - lista dos jogos instalados (`ApplicationInfo.CATEGORY_GAME` e a lista de jogos conhecidos);
  - antes de lançar: liga o Não incomodar (com acesso à política de notificações), para os processos em fundo que for permitido, mostra o estado térmico e abre o jogo;
  - durante o jogo: serviço em primeiro plano com uma notificação contínua «Boost ativo · <jogo> · 00:42:10», com temperatura da bateria, estado térmico e o botão «Terminar»;
  - no fim (deteta a saída via UsageStats ou pelo botão): repõe o Não incomodar e mostra um resumo com o tempo jogado, a temperatura máxima da bateria e o pior estado térmico;
  - nas versões que o suportem, integra com `GameManager` (API 31+) só para ler o modo de jogo atual.
- **Analisar e corrigir com 1 clique:** análise com progresso e passo em texto, e depois uma lista de correções propostas. «Corrigir tudo» só executa o que não exige confirmação do sistema; o resto fica como «ação necessária», com atalho direto.
- **Notificações** (WorkManager + canais separados): análise semanal, «armazenamento abaixo de 10 %», «telemóvel quente» (estado térmico Grave ou superior), «bateria degradada» e fim das tarefas. Respeita POST_NOTIFICATIONS (API 33+).
- **Histórico:** Room, com tipo de ação, data, bytes libertados e «Restaurar» quando possível (Lixo do sistema). Exportável em PDF (`PdfDocument`, sem bibliotecas).

## Ecrãs (navegação inferior com 5 separadores)
1. **Início:**
   - anel grande com a Saúde (0–100, cor por faixa: ≥80 B.Ok, ≥60 B.Warn, abaixo B.Danger);
   - botão principal «Analisar agora» em gradiente;
   - 4 cartões: Armazenamento, Temperatura, Bateria e Apps sem uso;
   - as últimas 3 ações.
2. **Limpar:** as categorias com o tamanho de cada uma, a pré-visualização em lista com miniaturas, e a barra fixa «Mover para o Lixo · 2,3 GB».
3. **Jogos:**
   - grelha de jogos com ícone e o botão «Boost e jogar»;
   - folha inferior «Preparar boost», com o que vai mudar;
   - ecrã de sessão ativa com cronómetro e gráfico térmico ao vivo;
   - diálogo de resumo no fim.
4. **Sistema:** temperaturas ao vivo (gráfico de 60 s), detalhe da bateria, armazenamento e apps em fundo.
5. **Histórico**, com Definições acessíveis pelo ícone ⚙ no topo: tema, notificações, agendamento, permissões concedidas e avisos de licença.

## Visual (igual à versão Windows)
Tema escuro por omissão (o claro é opcional), com estes tokens num `NexusTheme`:
- Bg #0C0F16, BgRaised #11151E, Surface #161B26, SurfaceAlt #1C2230, Border #262E3E, BorderStrong #354057
- Text #E8ECF4, TextDim #98A2B8, TextMuted #6A7488
- Accent #7C5CF6, Accent2 #22D3EE, Ok #34D399, Warn #FBBF24, Danger #F87171
- Gradiente principal: Accent → Accent2. Usa-o só no botão principal, no anel e nos destaques, nunca em fundos inteiros.

Regras gerais:
- cantos de 12–16 dp nos cartões e de 10 dp nos botões;
- números em fonte mono (JetBrains Mono, incluída em res/font);
- texto em Roboto Flex ou na fonte do sistema;
- alvos de toque de pelo menos 48 dp;
- suporte a edge-to-edge e às cores dinâmicas desligado (a marca manda).

## Marca «Gauge Shield» (mesmo logo da versão Windows)
Ícone da app, em viewBox 100×100:
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#7C5CF6"/><stop offset="1" stop-color="#22D3EE"/></linearGradient></defs><path d="M50 5 L88 17 V47 C88 72 71 88 50 96 C29 88 12 72 12 47 V17 Z" fill="url(#g)"/><path d="M27 62 A24 24 0 0 1 68 34" fill="none" stroke="#FFFFFF" stroke-width="8" stroke-linecap="round"/><line x1="50" y1="56" x2="64" y2="40" stroke="#FFFFFF" stroke-width="7" stroke-linecap="round"/><circle cx="50" cy="56" r="7" fill="#FFFFFF"/></svg>

- Converte-o em VectorDrawable e cria um **ícone adaptativo**:
  - fundo #0C0F16;
  - primeiro plano: o escudo em gradiente com o velocímetro branco, dentro da zona segura de 66 dp;
  - camada monocromática (Android 13+): só o escudo e o velocímetro, sem gradiente.
- Splash screen (API `core-splashscreen`): o ícone sobre #0C0F16.
- Ícone pequeno de notificação: só o velocímetro (arco + ponteiro), branco e transparente.
- No topo do Início, «Nexus» em Text e «Guard» em Accent2.


Atenção à Google Play: MANAGE_EXTERNAL_STORAGE e killBackgroundProcesses têm regras próprias. Deixa-os atrás de um flavor `full`, com um flavor `play` que funciona sem eles, e documenta a diferença.
