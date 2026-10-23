# Nexus Guard para Android

Otimizador transparente e honesto para Android, irmão da versão Windows (`com.nexusguard.android`).

## Princípios
1. **Pré-visualização e confirmação obrigatórias** antes de mover qualquer ficheiro.
2. **Lixo do sistema (`MediaStore.createTrashRequest`, API 30+)** como quarentena de 30 dias restaurável.
3. **Zero números falsos**: todas as métricas provêm de APIs reais do Android (`StatFs`, `BatteryManager`, `PowerManager`, `ActivityManager`, `UsageStatsManager`, `StorageStatsManager`, `MediaStore`).
4. **Permissões sob demanda**: pedidas apenas quando necessárias, com explicação clara.
5. **100 % local**: sem analytics, sem anúncios e sem permissão de `INTERNET`. Tudo fica registado no Histórico (Room) e pode ser exportado em PDF (`PdfDocument`).

## Permissões e Porque São Pedidas
| Permissão | Onde é pedida | Porque é necessária |
|---|---|---|
| `PACKAGE_USAGE_STATS` | Início / Sistema / Bateria | Identificar apps sem uso há mais de 30 dias (`UsageStatsManager`) e ler o tamanho real de cada app (`StorageStatsManager`). |
| `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` (ou `READ_EXTERNAL_STORAGE` até API 32) | Limpar | Analisar ficheiros grandes (`> 100 MB`), transferências antigas (`> 90 dias`), capturas de ecrã antigas e duplicados por tamanho + hash `SHA-256`. |
| `MANAGE_EXTERNAL_STORAGE` *(apenas flavor `full`)* | Limpar (Modo avançado) | Opcional: encontrar pastas vazias e ficheiros `.apk` fora das coleções multimédia padrão. |
| `KILL_BACKGROUND_PROCESSES` *(apenas flavor `full`)* | Jogos / Sistema | Terminar processos já em segundo plano permitidos pelo sistema. |
| `ACCESS_NOTIFICATION_POLICY` | Jogos (Preparar Boost) | Ativar automaticamente o modo **Não incomodar** durante a sessão de jogo e repô-lo no fim. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Jogos (Sessão Ativa) | Manter a notificação contínua `Boost ativo · <jogo> · HH:MM:SS` com temperatura da bateria e botão «Terminar». |
| `POST_NOTIFICATIONS` (API 33+) | Definições / Alertas | Enviar alertas de armazenamento (`< 10 %`), telemóvel quente (`Grave`/`Crítico`), saúde da bateria e análise semanal (`WorkManager`). |
| `REQUEST_DELETE_PACKAGES` | Sistema | Abrir o diálogo oficial do sistema quando o utilizador escolhe «Desinstalar» numa app sem uso. |

## Variantes (`full` vs `play`)
- **`full` (`assembleFullDebug` / `assembleFullRelease`):** Inclui `MANAGE_EXTERNAL_STORAGE` (como opção avançada) e `KILL_BACKGROUND_PROCESSES`.
- **`play` (`assemblePlayDebug` / `assemblePlayRelease`):** Compatível com as políticas restritas da Google Play Store — funciona exclusivamente com `MediaStore` / SAF e atalhos para as definições do sistema.

## O que o Android não deixa fazer (Sem Root)
Sem acesso root, o sistema operativo Android bloqueia por segurança:
- **Matar aplicações em primeiro plano de outros:** `killBackgroundProcesses` atua apenas em processos em segundo plano que o Android permite reciclar; para forçar a paragem completa de uma app, o Nexus Guard abre os detalhes oficiais da aplicação no sistema.
- **Limpar silenciosamente a cache de outras apps uma a uma:** As caches privadas de outras aplicações são protegidas pelo sandbox do Android; o Nexus Guard disponibiliza o atalho oficial `StorageManager.ACTION_CLEAR_APP_CACHE` (Android 11+).
- **Ler a temperatura interna de cada núcleo do CPU:** A partir do Android 10+, os nós térmicos do SoC estão restritos ao HAL do sistema; o Nexus Guard apresenta a temperatura real da bateria (`BatteryManager`), o estado térmico oficial (`PowerManager.currentThermalStatus`: *Normal / Ligeiro / Moderado / Grave / Crítico*) e a margem térmica (`getThermalHeadroom`).
