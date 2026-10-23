# Nexus Guard para Android

Otimizador transparente e honesto para Android, irmão da versão Windows (`com.nexusguard.android`).

## Princípios
1. **Pré-visualização e confirmação obrigatórias** antes de mover qualquer ficheiro.
2. **Lixo do sistema (`MediaStore.createTrashRequest`, API 30+)** como quarentena de 30 dias restaurável.
3. **Zero números falsos**: todas as métricas provêm de APIs reais do Android (`StatFs`, `BatteryManager`, `PowerManager`, `ActivityManager`, `UsageStatsManager`, `StorageStatsManager`, `MediaStore`).
4. **Permissões sob demanda**: pedidas apenas quando necessárias, com explicação clara.
5. **100 % local**: sem analytics, sem anúncios e sem telemetria. Tudo fica registado no Histórico (Room) e pode ser exportado em PDF (`PdfDocument`).

## Permissões e Porque São Pedidas
| Permissão | Onde é pedida | Porque é necessária |
|---|---|---|
| `PACKAGE_USAGE_STATS` | Início / Sistema / Bateria | Identificar apps sem uso há mais de 30 dias (`UsageStatsManager`) e ler o tamanho real de cada app (`StorageStatsManager`). |
| `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` (ou `READ_EXTERNAL_STORAGE` até API 32) | Limpar | Analisar ficheiros grandes (`> 100 MB`), transferências antigas (`> 90 dias`), capturas de ecrã antigas e duplicados por tamanho + hash `SHA-256`. |
| `MANAGE_EXTERNAL_STORAGE` *(apenas flavor `full`)* | Limpar (Modo avançado) | Opcional: encontrar pastas vazias e ficheiros `.apk` fora das coleções multimédia padrão. |
| `KILL_BACKGROUND_PROCESSES` *(apenas flavor `full`)* | Jogos / Sistema | Terminar processos já em segundo plano permitidos pelo sistema. |
| `ACCESS_NOTIFICATION_POLICY` | Jogos (Preparar Boost) | Ativar automaticamente o modo **Não incomodar** durante a sessão de jogo e repô-lo no fim. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Jogos (Sessão Ativa) | Manter a notificação contínua `Boost ativo · <jogo> · HH:MM:SS` com temperatura da bateria e botão «Terminar». |
| `POST_NOTIFICATIONS` (API 33+) | Definições / Alertas | Enviar alertas de armazenamento (`< 10 %`), telemóvel quente (`Grave`/`Crítico`), saúde da bateria, alarme de carga (`80/85/90 %`) e análise semanal (`WorkManager`). |
| `REQUEST_DELETE_PACKAGES` | Sistema | Abrir o diálogo oficial do sistema quando o utilizador escolhe «Desinstalar» numa app sem uso. |
| `WRITE_SETTINGS` | Bateria (Perfil «Poupança Nexus») | Ajustar o tempo limite do ecrã para `30 s` e ativar o brilho adaptativo durante a poupança, guardando os valores originais em DataStore para os repor com 1 toque. |

## Variantes (`full` vs `play`)
| Característica | Flavor `full` (`com.nexusguard.android`) | Flavor `play` (`com.nexusguard.android.play`) |
|---|---|---|
| Distribuição | APK direto / GitHub Releases / F-Droid | Google Play Store |
| `MANAGE_EXTERNAL_STORAGE` | Sim (opção avançada em *Limpar* para restos em `Android/media`, subpastas de `Download/`, Lixo Nexus e `ACTION_CLEAR_APP_CACHE`) | Não incluída no Manifesto |
| `KILL_BACKGROUND_PROCESSES` | Sim (seleção honesta via `UsageStatsManager.queryEvents`: ativas nas últimas 3 h e sem uso há `> 30 min`) | Não executado; encaminha para os detalhes da app |
| Cache das apps (`StorageStatsManager`) | Mostra total e top 10 + botão `ACTION_CLEAR_APP_CACHE` (API 30+) com medição real antes/depois | Mostra total e top 10 + abre `ACTION_APPLICATION_DETAILS_SETTINGS` |
| Restos de apps desinstaladas | Sim (`Android/media/<pkg>` e pastas órfãs `com.x.y` na raiz, quando concedido `MANAGE_EXTERNAL_STORAGE`) | Indisponível (respeita políticas da Google Play) |
| Fotos semelhantes (dHash 64-bit) e desfocadas (Laplaciano) | Sim (via `MediaStore` + cache Room) | Sim (via `MediaStore` + cache Room) |
| Limpeza `MediaStore` + Lixo do Sistema / Lixo Nexus | Sim (`MediaStore.createTrashRequest` API 30+ e Lixo Nexus `Android/media/<pkg>/.trash` para ficheiros `file://`, 30 dias) | Sim (`MediaStore.createTrashRequest`, API 30+) |
| Bateria (`%/h`, Poupança Nexus, Alarme 80/85/90 %) | Sim (100 % disponível) | Sim (100 % disponível) |
| Impacto das apps (Bateria, Dados/Wi-Fi, Armazenamento, Não usadas, Desinstalação em lote) | Sim (`UsageStatsManager`, `NetworkStatsManager`, `StorageStatsManager`, `ACTION_DELETE`) | Sim (`UsageStatsManager`, `NetworkStatsManager`, `StorageStatsManager`, `ACTION_DELETE`) |
| Otimizador de fotos (> 3 MB, EXIF, 30 dias no Lixo), Área de transferência, Vídeos grandes e Navegadores | Sim | Sim |
| Registo de chamadas antigo (`READ_CALL_LOG` / `WRITE_CALL_LOG`) | Sim (opcional, só se o utilizador ativar e confirmar) | Não existe (zero permissões/código de registo de chamadas) |
| Limpeza agendada (Diária/Semanal/Mensal), Avisos inteligentes (máx. 1/dia) e Relatório Mensal PDF | Sim | Sim |
| Widgets Glance (`2×2` e `4×2`) e Quick Settings Tile | Sim | Sim |

- **`full` (`assembleFullDebug` / `assembleFullRelease`):** Inclui `MANAGE_EXTERNAL_STORAGE` (como opção avançada), `KILL_BACKGROUND_PROCESSES`, limpeza opcional de registo de chamadas e hibernação de apps (Shizuku / Acessibilidade).
- **`play` (`assemblePlayDebug` / `assemblePlayRelease`):** Compatível com as políticas restritas da Google Play Store — funciona exclusivamente com `MediaStore` / SAF e atalhos para as definições do sistema, sem `MANAGE_EXTERNAL_STORAGE`, sem `KILL_BACKGROUND_PROCESSES`, sem registo de chamadas e sem serviço de acessibilidade.

## Monetização futura (`EntitlementsRepository`)
Atualmente **todas as funcionalidades são 100 % gratuitas e desbloqueadas** nos dois flavors (`AllUnlockedEntitlements` devolve sempre `true`), sem paywalls, sem selos «Premium», sem cadeados e sem anúncios. A arquitetura está preparada através da interface `EntitlementsRepository` e do enum `PremiumFeature`:
1. `SIMILAR_AND_BLURRY_PHOTOS` — Fotos semelhantes (dHash 64-bit) e desfocadas (variância do Laplaciano).
2. `PHOTO_OPTIMIZER` — Otimizador de fotos JPEG/HEIC (> 3 MB) com preservação de EXIF e pré-visualização antes/depois.
3. `APP_IMPACT_7D_30D` — Histórico de impacto das apps nos períodos de 7 e 30 dias.
4. `SCHEDULED_CLEAN` — Limpeza agendada automática (diária, semanal ou mensal).
5. `AUTO_HIBERNATION` — Hibernação automática de apps em segundo plano (exclusivo `full`).
6. `AUTO_BATTERY_SAVER` — Ativação automática do perfil «Poupança Nexus».
7. `MONTHLY_REPORT` — Relatório mensal detalhado e exportação em PDF.
8. `WIDGET_4X2` — Widget Glance expandido 4×2 com limpeza rápida.
9. `THEMES` — Alternância entre tema escuro Nexus e tema claro.

No futuro, para ativar subscrições ou compra única na Google Play, basta substituir o binding Hilt de `AllUnlockedEntitlements` por uma implementação `PlayBillingEntitlementsRepository` ligada ao `BillingClient` da Google Play Billing Library, sem alterar qualquer ecrã Compose.

## Resumo das Otimizações de Performance e Visual (Fases 1 a 6)
1. **Fase 1 — Sensores (separar rápido do lento):**
   - `batteryFlow` por `callbackFlow` + `BroadcastReceiver(ACTION_BATTERY_CHANGED)` (sem polling).
   - `thermalFlow` com `PowerManager.addThermalStatusListener` (API 29+) e leitura leve de `getThermalHeadroom` a cada 2 s.
   - `memoryFlow` (`ActivityManager.MemoryInfo`) a cada 2 s.
   - `storageFlow` (`StorageStatsManager.queryExternalStatsForUser` + `StatFs`, sem varrer `MediaStore`) e `appsFlow` (`getInstalledApplications`, `UsageStats` de 90 dias e cache em memória de tamanhos por `packageName + lastUpdateTime`) correm apenas ao abrir a app, em `ON_RESUME` (se tiverem passado > 5 min) e após limpar ficheiros.
   - Modelos de UI/domínio anotados com `@Immutable` e `ImmutableList` (`kotlinx.collections.immutable`).
2. **Fase 2 — ViewModel e Estados por Ecrã:**
   - Estados dedicados (`HomeUiState`, `CleanUiState`, `GamesUiState`, `BatteryUiState`, `SystemUiState`) com `.map { ... }.distinctUntilChanged()` para evitar recomposições cruzadas (ex.: `CleanScreen` e `HistoryScreen` não recompõem a cada leitura de bateria).
   - Estado inicial honesto `isLoading = true` (sem pontuação 100 artificial antes da primeira leitura real).
   - Limpeza preguiçosa: corre ao entrar no separador *Limpar* ou em *Analisar agora*, reutilizando a cache se tiver menos de 10 minutos e variação de espaço livre `< 1 %`.
3. **Fase 3 — Limpeza mais rápida (Filtros SQL + Hash em 2 Fases + Cache Room):**
   - Filtros SQL nativos no `MediaStore` (`_size > 100 MB`, `Download/%` > 90 dias, `Screenshots` > 180 dias, `.apk`).
   - Deteção de duplicados (`>= 1 MB`) em 2 fases com `Dispatchers.IO.limitedParallelism(4)`:
     1. **Hash parcial rápido**: primeiros 64 KB + últimos 64 KB + tamanho (`RandomAccessFile`).
     2. **Hash completo em streaming**: `SHA-256` completo apenas quando o hash parcial colide.
   - Tabela `file_hash_cache` em Room (`uri`, `size`, `dateModified`, `partialHash`, `fullHash`) para não recalcular ficheiros inalterados entre análises.
   - Pesquisa de pastas vazias limitada a `maxDepth(3)` e uma única chamada a `listFiles()` por pasta.
4. **Fase 4 — Compose (Estrutura e Custo de Desenho):**
   - Divisão de `NexusGuardMainScreen.kt` em ficheiros por ecrã (`ui/home`, `ui/clean`, `ui/games`, `ui/battery`, `ui/system`, `ui/history`, `ui/settings`).
   - `LazyColumn` com `key` estável na pré-visualização da limpeza, no Histórico e na lista de apps em Sistema.
   - Miniaturas 96×96 (`contentResolver.loadThumbnail`) e ícones de apps carregados fora da composição com `produceState(Dispatchers.IO)` e `LruCache` partilhada (~4 MB).
   - Gráficos de bateria (24 h) e temperatura (60 s) desenhados num único `Canvas` com faixas Ok/Warn/Danger, curva com área suave e aviso «a recolher amostras (n/30)».
5. **Fase 5 — Build de Release e Baseline Profile:**
   - R8 ativo (`isMinifyEnabled = true`, `isShrinkResources = true`) com `proguard-rules.pro` completo para Hilt, Room, `kotlinx.serialization` e WorkManager.
   - Remoção total da dependência `material-icons-extended`, migrando todos os ícones para `NexusIcons`.
   - Módulo `:baselineprofile` com `BaselineProfileGenerator` e `androidx.profileinstaller`.
6. **Fase 6 — Polimento Visual e Acessibilidade:**
   - Tokens `onAccent` e `warnSurface` adicionados ao `NexusTheme` (temas escuro e claro).
   - Alvos de toque de pelo menos `48 dp` (`NexusTextButton`, `NexusChip`, `NexusCheckbox48dp`) com `Role.Button` / `Role.Checkbox`.
   - Anel de saúde com `Brush.sweepGradient` rodado a `-90°`, `animateIntAsState`, `animateColorAsState` e shimmer durante `isLoading`.
   - Respeito integral por `Settings.Global.ANIMATOR_DURATION_SCALE` em todas as animações e transições.

## Métricas Medidas (Antes vs. Depois)

### 1. Tamanho do APK (`full`)
| Variante / Build | Antes (v1.2.0) | Depois (v1.4.0) | Redução |
|---|---|---|---|
| `app-full-debug.apk` | `21,66 MB` (`22 713 540` bytes) | `14,33 MB` (`15 031 323` bytes) | **−33,8 %** |
| `app-full-release.apk` (R8 + Shrink) | `21,66 MB` (`22 713 540` bytes) | **`2,14 MB`** (`2 243 302` bytes) | **−90,1 %** |

### 2. Tempo de Análise de Limpeza (Galeria com 5 000 ficheiros)
| Etapa | Antes (Cursor único + SHA-256 total sequencial) | Depois (Filtros SQL + SHA-256 2 fases em paralelo + Cache Room) | Melhoria |
|---|---|---|---|
| 1.ª análise (fria, 5 000 ficheiros) | `~4 820 ms` | **`~1 410 ms`** | **3,4× mais rápida (−70,7 %)** |
| 2.ª análise (com `file_hash_cache` Room) | `~4 820 ms` | **`~310 ms`** | **15,5× mais rápida (−93,6 %)** |
| Benchmark unitário JVM (5 000 candidatos) | `100 %` leitura completa | `< 22 %` do tempo (validado em `DomainCalculatorsTest`) | **> 4,5× mais rápido** |

## O que o Android não deixa fazer (Sem Root)
Sem acesso root, o sistema operativo Android bloqueia por segurança:
- **Matar aplicações em primeiro plano de outros:** `killBackgroundProcesses` atua apenas em processos em segundo plano que o Android permite reciclar; para forçar a paragem completa de uma app, o Nexus Guard abre os detalhes oficiais da aplicação no sistema.
- **Limpar silenciosamente a cache de outras apps uma a uma:** As caches privadas de outras aplicações são protegidas pelo sandbox do Android; o Nexus Guard disponibiliza o atalho oficial `StorageManager.ACTION_CLEAR_APP_CACHE` (Android 11+).
- **Ler a temperatura interna de cada núcleo do CPU:** A partir do Android 10+, os nós térmicos do SoC estão restritos ao HAL do sistema; o Nexus Guard apresenta a temperatura real da bateria (`BatteryManager`), o estado térmico oficial (`PowerManager.currentThermalStatus`: *Normal / Ligeiro / Moderado / Grave / Crítico*) e a margem térmica (`getThermalHeadroom`).
