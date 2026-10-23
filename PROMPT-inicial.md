Cria a app descrita em AGENTS.md, na pasta android/. Usa o modo Planning: primeiro mostra-me o plano (Implementation Plan) e só avança depois de eu aprovar.

Faz uma fase de cada vez. No fim de cada fase, compila (./gradlew assembleDebug), corre os testes, abre a app no emulador, tira screenshots dos ecrãs novos e mostra-me um walkthrough antes de passar à seguinte.

## Fases
1. Projeto, tema, marca (ícones, splash), navegação e onboarding com os princípios.
2. Início + Saúde + Sistema (sensores reais, gráfico ao vivo).
3. Limpar + Lixo do sistema + Histórico (Room).
4. Jogos (boost, serviço em primeiro plano, resumo).
5. Notificações e agendamento (WorkManager), Definições e PDF.
6. Testes e documentação:
   - testes unitários para a pontuação de saúde, a classificação de ficheiros (APK, duplicados, antigos) e o mapeamento dos estados térmicos;
   - um teste de UI do fluxo «Analisar → Corrigir»;
   - `android/README.md` com a explicação de cada permissão e porque é pedida, e uma secção «O que o Android não deixa fazer».

Começa pela fase 1.