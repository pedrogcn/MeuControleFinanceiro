# Meu Controle Financeiro

Aplicativo Android nativo, **100% offline**, para controlar salário, receitas, contas mensais, dívidas, parcelas e saldo disponível. Kotlin + Jetpack Compose (Material 3) + Room/SQLite. Sem login, sem servidor, sem anúncios, sem rastreadores e **sem permissão de internet** (ela é removida explicitamente no manifesto).

---

## Status de verificação (leia primeiro)

| Item | Situação |
|---|---|
| Regras financeiras (`domain/`) e backup (`backup/`) | **Compiladas e testadas**: 32 testes unitários passando, incluindo os 12 cenários obrigatórios |
| Camada de dados (Room, DAO, repositório) | Checagem de tipos feita contra *stubs* das anotações do Room; **ainda não compilada com o Room real** |
| Interface Compose, notificações, PDF | Escrita e revisada, **ainda não compilada** |
| APK | **Não gerado** no ambiente de desenvolvimento: o Android SDK não estava instalado e a rede bloqueava `dl.google.com`, `maven.google.com` e o Maven Central |

Ou seja: o primeiro `assembleDebug` em uma máquina com Android SDK é a verificação final. Se aparecer algum erro de compilação, ele estará na camada de interface — envie a mensagem de erro para correção.

---

## Requisitos para compilar

- **JDK 17** (ou o JDK embutido no Android Studio)
- **Android SDK** com *Platform 35* e *Build-Tools 35* (o Android Studio instala sozinho)
- Internet **apenas na primeira compilação**, para baixar Gradle e bibliotecas. O app em si nunca usa internet.
- Celular com **Android 8.0 (API 26)** ou superior

## Opção 1 — Android Studio (mais simples)

1. Instale o [Android Studio](https://developer.android.com/studio) (Ladybug ou mais recente).
2. *File → Open* e selecione a pasta `MeuControleFinanceiro`.
3. Aguarde a sincronização do Gradle (primeira vez: alguns minutos).
4. Menu *Build → Build App Bundle(s) / APK(s) → Build APK(s)*.
5. O APK fica em: `app/build/outputs/apk/debug/app-debug.apk`

## Opção 2 — Linha de comando

```bash
cd MeuControleFinanceiro
# informe onde está o SDK (ou crie local.properties com sdk.dir=/caminho/do/sdk)
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew testDebugUnitTest     # testes unitários
./gradlew assembleDebug         # gera o APK
ls app/build/outputs/apk/debug/app-debug.apk
```

No Windows use `gradlew.bat` no lugar de `./gradlew`.

## Opção 3 — GitHub Actions (sem instalar nada)

1. Crie um repositório no GitHub e envie esta pasta.
2. O workflow `.github/workflows/build-apk.yml` roda os testes e compila o APK automaticamente a cada *push*.
3. Na aba *Actions*, abra a execução e baixe o artefato **MeuControleFinanceiro-debug-apk**.

## Instalar no celular

- **Por cabo (ADB):** `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- **Sem cabo:** copie o `app-debug.apk` para o celular, abra pelo gerenciador de arquivos e permita "instalar apps desconhecidos" para esse gerenciador quando o Android pedir.

O APK de depuração usa o pacote `br.com.meucontrole.financeiro.debug` e é assinado com a chave de depuração do computador que o compilou. Para atualizar o app sem perder dados, compile sempre na mesma máquina (ou exporte um backup antes de reinstalar).

---

## Uso

1. **Mais → Configurações → Saldo inicial:** informe quanto você tem hoje em conta/dinheiro. É a base do "caixa estimado".
2. **Mais → Salário e receitas:** cadastre o salário **líquido** (o bruto é opcional e só informativo). Ao receber, toque no ✓ e confirme o valor real.
3. **Contas:** cadastre água, luz, aluguel etc. com recorrência. As ocorrências de cada mês são geradas automaticamente, sem duplicar.
4. **Dívidas → Nova dívida:**
   - *Dividir valor*: informe total contratado, entrada e quantidade de parcelas. A diferença de centavos vai para a última parcela.
   - *Uma a uma*: informe cada parcela com valor e vencimento próprios.
5. **Pagar:** toque no ✓ ao lado de qualquer parcela ou conta — o valor restante já vem preenchido. Dá para pagar parcial, adiantado, com juros/multa ou desconto.
6. **Início:** resumo do mês com previsto, pago e pendente; **Quanto posso gastar?** mostra o saldo realizado e o projetado.

### Ações sobre pagamentos

| Ação | Onde |
|---|---|
| Pagamento parcial | Informe um valor menor que o restante |
| Pagamento acima do previsto | O app pergunta: abater nas próximas parcelas, registrar como juros/multa, ou limitar ao saldo. Nunca cria saldo negativo escondido |
| Corrigir lançamento | Ícone ✎ no histórico de pagamentos (fica registrado no histórico de alterações) |
| Estornar | Ícone ↻ no histórico, com motivo obrigatório. O pagamento continua visível, riscado |
| Quitar por acordo | Detalhe da dívida → ⋮ → Quitar por acordo. A diferença vira desconto explícito |
| Renegociar | Detalhe da dívida → ⋮ → Renegociar. A dívida antiga fica como "Renegociada" com todo o histórico |

---

## Como os cálculos funcionam

- Todo valor é guardado em **centavos (`Long`)**. Não há `Double`/`Float` em cálculos financeiros.
- **Devido de uma parcela/conta** = previsto + encargos cobrados − descontos concedidos.
  **Restante** = máx(0, devido − pago). Pagamentos estornados não entram em nenhuma soma.
- **Saldo devedor** = soma dos restantes das parcelas de dívidas **ativas**. Dívidas quitadas ou renegociadas contam zero.
- **Entrada**: saldo a parcelar = total contratado − entrada. A entrada não é contada como pagamento de parcela.
- **Saldo realizado** (período) = receitas efetivamente recebidas − despesas efetivamente pagas.
- **Caixa estimado** = saldo inicial + tudo recebido − tudo pago desde a data do saldo inicial.
- **Saldo projetado** = caixa estimado + receitas ainda previstas no período − contas pendentes − parcelas pendentes (incluindo vencidas). Como só o *restante* de cada item entra, uma despesa paga nunca é subtraída duas vezes.
- Contas fixas e parcelas de dívidas são entidades separadas: não há dupla contagem.
- Meses curtos: vencimento no dia 31 cai no último dia do mês (28/29 em fevereiro, 30 em abril...).
- **Quinzenal**: dois recebimentos por mês, no dia escolhido e 15 dias depois/antes (ex.: 5 e 20).
- O app **não inventa juros**: a taxa é apenas registrada como texto. Encargos só entram quando você informa.
- O saldo projetado é uma estimativa a partir do que foi registrado; ele **não é** necessariamente igual ao saldo real no banco.

Dashboard, relatórios, calendário e lembretes usam o mesmo motor (`FinanceEngine`), então os números são sempre iguais entre as telas.

## Backup

- **Exportar**: JSON versionado (`format`, `version`, `checksum` SHA-256, `data`). Após gravar, o app relê o arquivo e valida.
- **Importar**: o arquivo é validado (formato, versão, checksum, campos, valores negativos, datas, referências entre registros, duplicidades) **antes** de qualquer alteração. Depois você escolhe:
  - *Mesclar*: adiciona só o que é novo; duplicados (detectados por chaves naturais) são ignorados e listados.
  - *Substituir*: troca tudo, em uma única transação.
- Exporta também um **CSV de dívidas e pagamentos** (separador `;`, abre direto no Excel).
- O PIN não vai no backup. O backup em nuvem automático do Android está desativado de propósito.

> ⚠️ Os dados existem só no aparelho. Perda, formatação ou desinstalação apagam tudo que não foi exportado. Exporte backups regularmente e guarde fora do celular.

## Lembretes

Notificações locais 1 dia antes e no dia do vencimento (configurável), via WorkManager, uma vez por dia no horário escolhido. No Android 13+ é preciso permitir notificações (Configurações do app). O Android pode **atrasar** a verificação por economia de bateria/modo soneca; para mais precisão, desative a otimização de bateria para o app.

## Segurança

- PIN opcional (4–8 dígitos), guardado como hash PBKDF2 com sal — nunca em texto. Bloqueia ao abrir e após 2 minutos em segundo plano. Se esquecer o PIN, é preciso limpar os dados do app e restaurar um backup.
- Operações financeiras em transações Room; pagamentos têm identificador único de operação (`requestId`) com índice único, o que impede gravação duplicada por duplo toque ou reprocessamento.
- Confirmação antes de excluir dívidas, contas, receitas e antes de restaurar.

---

## Estrutura do projeto

```
app/src/main/java/br/com/meucontrole/financeiro/
├── domain/          Regras puras (sem Android) — testáveis
│   ├── model/       Modelos, Money (centavos), categorias
│   ├── calc/        FinanceEngine, Settlements, InstallmentPlanner, RecurrenceEngine, Reports
│   └── usecase/     DebtFactory, PaymentPlanner, DebtStatusRules, PaymentEditRules
├── data/            Room (entidades, DAO, banco), mapeamentos, FinanceRepository, SecurityStore
├── backup/          BackupCodec (JSON + checksum), BackupMerger, RestoreBackupUseCase
├── notifications/   ReminderRules, ReminderWorker (WorkManager), NotificationHelper
├── presentation/    MainViewModel, navegação, tema, componentes e telas Compose
└── util/            Exportação de arquivos (SAF) e PDF (PdfDocument nativo)
app/src/test/        Testes unitários (JUnit 4)
scripts/run-domain-tests.sh   Roda os testes do domínio sem Android SDK
```

## Testes

```bash
./gradlew testDebugUnitTest            # com Android SDK
./scripts/run-domain-tests.sh          # sem Android SDK (usa o Kotlin embutido no Gradle)
```

Cenários cobertos (`RequiredScenariosTest` + `CalculationsTest`):

1. R$ 1.200 em 12×R$ 100, três pagas → saldo R$ 900
2. R$ 500 com pagamento parcial de R$ 150 → saldo R$ 350, sem encargos
3. R$ 1.000 com entrada de R$ 200 → R$ 800 a parcelar, entrada não contada duas vezes
4. Parcela de R$ 100 com R$ 40 pagos → restante R$ 60
5. Parcela paga não soma no saldo em aberto; dívida quitada sai do saldo
6. Despesa paga não é subtraída de novo do projetado
7. Salário bruto e líquido não são somados
8. Contas recorrentes não duplicam (inclusive dia 31 em meses curtos)
9. Backup inválido (JSON quebrado, outro app, checksum alterado, versão futura) não altera nada
10. Estorno recalcula saldos e reabre a dívida
11. Renegociação preserva parcelas e pagamentos anteriores, sem dupla contagem
12. Dashboard reflete o lançamento imediatamente

Extras: formatação/leitura de R$, divisão com centavos, parcelas manuais diferentes, pagamento duplicado (mesmo `requestId`), excedente abatido nas próximas, excedente além da dívida inteira, encargos e descontos, acordo e estorno do acordo, correção de lançamento, pagamento antecipado, recorrência quinzenal/semanal/anual (29/02), backup ida e volta, referência quebrada, mesclagem com duplicados, relatório = dashboard, histórico acumulado.

## Limitações conhecidas

- Sem sincronização entre aparelhos (por design: offline).
- Gráficos simples desenhados com Canvas (sem bibliotecas externas).
- Ao abater excedente nas próximas parcelas, são criados lançamentos separados por parcela; para desfazer, estorne cada um.
- Editar a recorrência de uma conta remove ocorrências futuras sem pagamento que deixarem de existir na nova agenda; ocorrências passadas e pagas não mudam.
