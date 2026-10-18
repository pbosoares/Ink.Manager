# Marcae simples - validacao incremental

Data: 18/10/2026. Checkout Ink.Manager, branch main. Remoto origin aponta para https://github.com/pbosoares/Ink.Manager.git. HEAD e origin/main em 53c28a45852e7c0ed70b38de56d078a738f75772. Working tree compartilhado contem alteracoes desta entrega e de outros responsaveis; nada foi commitado ou publicado nesta etapa.

## Build e testes da API

- Java 25, compilacao atualizada aprovada (81 fontes), log target/marcae-compile-updated.log.
- H2: BUILD SUCCESS; 42 testes, 0 falhas, 0 erros, 1 skip exclusivo de migracao PostgreSQL. Inclui 27 testes de integracao HTTP e os asserts de recebimentos liberados pelo Backend. Log target/marcae-h2-final.log.
- PostgreSQL 18.4: `mvnw verify` BUILD SUCCESS; 42 testes, 0 falhas/erros/skips e JAR empacotado. Log target/marcae-postgres-final.log. Banco dedicado marcae_validation_tests no cluster local conhecido.
- A primeira execucao H2 encontrou diferenca de nanossegundos entre resposta e retry de recebimento. Lead normalizou `confirmadoEm` a microssegundos antes de salvar; H2 e PostgreSQL passaram sem afrouxar a igualdade do teste.

## Navegador e API real

- Playwright completo: 4/4 aprovados; desktop 1366px e mobile 390px passaram, assim como o contrato API e o fluxo SaaS/regressao existente. Log target/marcae-browser-final-full.log.
- Reserva publica percorreu `service-form`, link, selecao, consulta de horarios, nome/telefone, confirmacao minima e snapshot de preco/duracao, sem Authorization na rota publica.
- No teste desktop, a primeira resposta da reserva foi descartada depois de a API real gravar a reserva. A recarga reutilizou a mesma chave UUID e payload; a API respondeu idempotentemente e exibiu a confirmacao sem duplicar. A consulta de slots preservou horario ainda livre.
- Atendimento historico sintetico foi criado pela API no banco exclusivo de navegador, nao por SQL. A UI confirmou valor efetivo R$80,25 e exibiu os totais semanais/mensais. O resumo e filtrado por data de confirmacao.
- Screenshots: target/marcae-public-1366.png, target/marcae-confirmation-1366.png, target/marcae-receipts-1366.png e equivalentes `-390.png`.
- Sintaxe JS: `node --check` em marcae-browser.spec.cjs e marcae-contract.spec.cjs; `git diff --check` nos arquivos deste escopo. Frontend registrou `node --check` dos cinco JS e `git diff --check` em static.

## Comandos de validacao

PowerShell no diretorio `ink-manager-api`, com Java 25 e Maven local em `C:\Users\Acer\.m2\repository`:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
# H2
.\mvnw.cmd -B '-Dmaven.repo.local=C:\Users\Acer\.m2\repository' '-Djavax.net.ssl.trustStoreType=Windows-ROOT' '-Djavax.net.ssl.trustStore=NONE' test
# PostgreSQL exclusivo
$env:INK_TEST_DB_URL='jdbc:postgresql://127.0.0.1:55439/marcae_validation_tests'
$env:INK_TEST_DB_DRIVER='org.postgresql.Driver'
$env:INK_TEST_DB_USER='ink_validation'
$env:INK_TEST_DB_PASSWORD='validation-only'
.\mvnw.cmd -B '-Dmaven.repo.local=C:\Users\Acer\.m2\repository' '-Djavax.net.ssl.trustStoreType=Windows-ROOT' '-Djavax.net.ssl.trustStore=NONE' verify
# Navegador/API real em banco exclusivo
$env:DB_URL='jdbc:postgresql://127.0.0.1:55439/marcae_validation_browser'
$env:DB_USERNAME='ink_validation'
$env:DB_PASSWORD='validation-only'
node node_modules/playwright/cli.js test
```

## Playwright apos liberar analytics.js (18/10/2026)

- `SecurityConfig` atualizado pelo Tech Lead agora permite o GET estatico de `/analytics.js`; o JAR foi recompilado/empacotado com `mvnw package -DskipTests` antes do navegador. Esse comando compilou as 89 fontes principais e 5 fontes de teste, mas pulou a execucao dos testes Maven.
- Suite Playwright no PostgreSQL descartavel `marcae_validation_browser`: desktop 1366px passou, mobile 390px passou e contrato publico passou. Ambos os fluxos UI cobriram reserva com email/aceite, relatorio mensal, regras de agendamento, confirmacao de recebimento e totais. Os traces confirmam `/analytics.js` HTTP 200. Screenshots do dashboard real: `target/marcae-dashboard-1366.png` e `target/marcae-dashboard-390.png`.
- O teste legado de cadastro/equipe falhou na suite completa e tambem na repeticao isolada. No primeiro ciclo a lista de profissionais nao refletiu o `POST /profissionais` (201) porque GETs da recarga da pagina terminaram sem resposta. Na repeticao isolada, a criacao do profissional passou, mas apos a recepcao salvar a terceira marcacao o owner continuou exibindo 2 linhas; o assert de 3 linhas em `e2e/studio.spec.cjs:139` expirou apos 25 s. O trace da pagina owner mostra GET `/marcacoes` e `/profissionais` com status HAR `-1` durante polling, sem `request.failure().errorText` disponivel. A causa raiz continua inconclusiva; mantivemos a contagem esperada e nao afrouxamos o aceite.
- Resultado agregado da suite depois dos seletores E2E corrigidos: 3/4 passaram, 1 falhou. Reexecucao isolada de `e2e/studio.spec.cjs`: 0/1 passou. Nenhum teste foi executado em paralelo.
- O preview 8082 nao foi iniciado. Os testes usaram somente o banco local dedicado; sem gateway, pagamento ou email real.

Comandos desta rodada (PowerShell em `ink-manager-api`):

```powershell
# Empacotar a SecurityConfig atual; testes Maven deliberadamente pulados antes do Playwright
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
Remove-Item Env:DB_URL,Env:DB_USERNAME,Env:DB_PASSWORD,Env:INK_TEST_DB_URL,Env:INK_TEST_DB_DRIVER,Env:INK_TEST_DB_USER,Env:INK_TEST_DB_PASSWORD -ErrorAction SilentlyContinue
.\mvnw.cmd -B '-Dmaven.repo.local=C:\Users\Acer\.m2\repository' '-Djavax.net.ssl.trustStoreType=Windows-ROOT' '-Djavax.net.ssl.trustStore=NONE' -DskipTests package
# Playwright completo, depois repeticao isolada do teste legado
$env:DB_URL='jdbc:postgresql://127.0.0.1:55439/marcae_validation_browser'
$env:DB_USERNAME='ink_validation'
$env:DB_PASSWORD='validation-only'
Remove-Item Env:INK_TEST_DB_URL,Env:INK_TEST_DB_DRIVER,Env:INK_TEST_DB_USER,Env:INK_TEST_DB_PASSWORD -ErrorAction SilentlyContinue
node node_modules/playwright/cli.js test
node node_modules/playwright/cli.js test e2e/studio.spec.cjs
```

## Reteste apos ajuste serial do polling da equipe (18/10/2026)

- Empacotamento atualizado: `mvnw package -DskipTests` passou; `team.js` atual foi copiado para o JAR. Maven compilou sem executar testes, conforme solicitado.
- Playwright completo, serial (`workers: 1`), no banco local dedicado `marcae_validation_browser`: **4/4 passaram** em 1,4 min. Desktop 1366px e mobile 390px passaram, assim como contrato publico e regressao de cadastro/equipe.
- O teste legado passou pela criacao de profissional e pelo agendamento da recepcao; a agenda do owner refletiu as 3 linhas esperadas. A falha de polling observada nas rodadas anteriores nao se reproduziu com `static/team.js` serial, retry unico e refresh em foco/visibilidade. Os asserts permaneceram intactos.
- `/analytics.js` foi servido com HTTP 200. Fluxos de reserva com email/consentimento, relatorio, configuracao, presenca exibida, recebimentos e totais passaram no desktop e mobile. Screenshots atuais do dashboard: `target/marcae-dashboard-1366.png` e `target/marcae-dashboard-390.png`.
- `node --check` passou em `static/team.js` e nos tres specs E2E; `git diff --check` passou. O Git apenas avisou que normalizara LF para CRLF em `team.js` quando voltar a grava-lo.
- Nao foi iniciado preview em 8082; os testes usaram apenas o PostgreSQL browser dedicado. Sem testes executados em paralelo, DB real, gateway, pagamentos ou emails reais.

Comandos executados nesta rodada (PowerShell em `ink-manager-api`):

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
# Build do JAR atual sem executar testes Maven
Remove-Item Env:DB_URL,Env:DB_USERNAME,Env:DB_PASSWORD,Env:INK_TEST_DB_URL,Env:INK_TEST_DB_DRIVER,Env:INK_TEST_DB_USER,Env:INK_TEST_DB_PASSWORD -ErrorAction SilentlyContinue
.\mvnw.cmd -B '-Dmaven.repo.local=C:\Users\Acer\.m2\repository' '-Djavax.net.ssl.trustStoreType=Windows-ROOT' '-Djavax.net.ssl.trustStore=NONE' -DskipTests package
# Suite completa Playwright em banco dedicado
$env:DB_URL='jdbc:postgresql://127.0.0.1:55439/marcae_validation_browser'
$env:DB_USERNAME='ink_validation'
$env:DB_PASSWORD='validation-only'
Remove-Item Env:INK_TEST_DB_URL,Env:INK_TEST_DB_DRIVER,Env:INK_TEST_DB_USER,Env:INK_TEST_DB_PASSWORD -ErrorAction SilentlyContinue
node node_modules/playwright/cli.js test
```

## Investigacao de foco e aborts

Traces iniciais registraram GETs do owner abortados apos `AbortSignal.timeout(20000)` enquanto chamadas APIRequestContext recebiam 200. Headless multi-context foi atualizado para trazer cada pagina a frente e desabilitar throttling de paginas ocultas; o teste agora verifica `visibilityState` e registra failureText e apenas path/errorText das requests, sem guardar cabecalhos ou JWT. Tambem espera o estado de catalogo que a UI ja apresenta antes de criar servico. A suite repetida passou integralmente (incluindo reload, retry da reserva e fluxo legado), portanto nao ficou falha de foco reproduzivel. Os traces primeiros mostraram tambem corrida de teste ao editar catalogo ainda carregando; foi eliminada esperando status de carregamento existente, sem aumentar AbortSignal ou reduzir asserts.

## Git, deploy e limites

- Ramo `main`, remoto `origin`, upstream `origin/main`; HEAD e upstream coincidem. Esta etapa nao criou commit, nao publicou e nao executou deploy.
- No checkout, `.github/workflows` contem apenas `ci.yml`, configurado para teste em push/PR; nao ha workflow de deploy versionado. Isso nao confirma eventual configuracao externa de hosting.
- Nenhum teste usou o outro projeto Marcae ou dados reais. Nenhum email real, cobranca ou chamada de producao.
- E2E cria estabelecimentos, clientes, servicos, reservas e recebimentos sint?ticos somente no banco PostgreSQL local `marcae_validation_browser`. Cluster `target/validation-postgres` foi encontrado em execucao; nao foi parado, limpo nem teve registros pessoais excluidos.
- Contratos V11/V12 ainda nao aparecem em `docs/MARCAE-CONTRATOS.md` nesta verificacao. Metricas, espera, acoes tokenizadas e retentativas especificas dessa etapa ficam pendentes da definicao dos endpoints; nao foram inferidos.
- Confirmacao/estorno administrativo de recebimentos e demais limites de produto constam nos contratos. Nenhuma campanha, pagamento ou integracao comercial foi acionada.

## Diagnostico da regressao de polling (18/10/2026)

Leitura do `test-results/studio-cadastro-cliente-re-e45e7--persistencia-e-nova-sessao/trace.zip` (sem exportar headers): o reload do owner iniciou o bootstrap normalmente. `GET /auth/config`, `/clientes`, `/marcacoes`, `/profissionais` e `/auth/me` responderam HTTP 200 em aproximadamente 20–100 ms no HAR. Vinte segundos depois, o polling da equipe (`static/team.js`, `setInterval` a cada 20 s; `Promise.all` de `api('/marcacoes')` e `api('/profissionais')`) iniciou os dois GETs na página owner em 20:38:16.614Z/20:38:16.615Z. Ambos encerraram cerca de 20,009 s depois, por volta de 20:38:36.623Z/20:38:36.624Z, com status HAR -1. O limite coincide com `AbortSignal.timeout(20000)` em `static/app.js`; a UI coloca no `#page-error` “Não foi possível conectar ao negócio. Confira a conexão e tente novamente.” O trace HAR não preserva o `request.failure().errorText` exato, portanto não permite afirmar `net::ERR_ABORTED` como texto coletado nesta execução.

Para comparação, o `GET /marcacoes` via APIRequestContext no cleanup iniciou em 20:38:25.292Z, sobreposto aos requests da página, e terminou HTTP 200 após 10.055 s (aprox. 20:38:35.347Z). Assim, o endpoint não respondeu rapidamente nesse intervalo, embora a solicitação de API tenha concluído antes do timeout do browser. Requests equivalentes do bootstrap/reload da página haviam respondido em 40–275 ms. O sintoma fica localizado no ciclo de polling da página em foreground/background durante o fluxo de duas sessões; o HAR sozinho não determina por que o backend/browser demorou, e a causa raiz permanece inconclusiva.

A ação que antecede a verificação é a gravação da reserva pela página de funcionário; o teste traz o owner à frente e espera a terceira linha da agenda (`e2e/studio.spec.cjs`, etapa após `staffPage` salvar). O owner ainda mostra duas linhas porque a atualização pelo polling falhou; a suíte preserva o assert de três registros. Nenhum código de produção ou teste foi alterado nesta investigação.

## Revalidacao V11 apos atualizacao dos testes (18/10/2026)

O resultado H2 anterior de 52 testes, 7 falhas, 0 erros e 1 skip nao e aceite e foi preservado em `target/marcae-h2-before-retest/`; nao deve ser confundido com os logs V10 de 42 testes aprovados acima.

A nova execucao H2 foi interrompida antes do Surefire: 89 fontes de producao compilaram, mas `testCompile` falhou em `src/test/java/ink/manager/api/StudioIntegrationTest.java:474:119`. A assercao chama `dinheiro(String, JsonNode)` com `JdbcTemplate.queryForObject(..., BigDecimal.class, ...)`, que retorna `BigDecimal`; o compilador reporta limites incompatíveis `java.math.BigDecimal` e `tools.jackson.databind.JsonNode`. Nenhum teste desta revisao executou, portanto H2 nao esta validado.

Neste ciclo PostgreSQL, Playwright e preview nao foram iniciados; os passos permanecem bloqueados pela falha de compilacao H2. Nenhum arquivo `src/main/**`, `static/**` ou teste foi alterado pelo operador.

## Revalidacao apos correcao da assercao SQL e E2E atualizado (18/10/2026)

- H2 em memoria: `mvnw test` passou; 53 testes, 0 falhas, 0 erros, 1 skip (migracao exclusiva de PostgreSQL). Build compilou e executou os testes contra `jdbc:h2:mem:studio`.
- PostgreSQL 18.4 local dedicado `marcae_validation_tests`: `mvnw verify` passou; 53 testes, 0 falhas, 0 erros, 0 skips. Migrações aplicadas ate V11 e JAR reempacotado com os arquivos estaticos atuais.
- Playwright completo no banco local isolado `marcae_validation_browser`: 2/4 passaram (contrato publico e regressao de cadastro/equipe); os fluxos desktop e mobile falharam. Nao houve screenshots de aceite porque ambos pararam antes do fluxo publico/recebimentos.
- Diagnostico estatico confirmado no trace: `GET /analytics.js` retornou HTTP 401 nos dois navegadores. O arquivo existe em `target/classes/static/analytics.js`, e o HTML o carrega; a lista publica em `SecurityConfig` libera `app.js`, `team.js`, `billing.js`, `simple.js`, mas nao `analytics.js`. Como consequencia, o modulo de relatorio/configuracao/presenca nao executa; no mobile `#report-data` permanece oculto. Responsavel: Tech Lead (`src/main`).
- O trace do desktop tambem mostrou GETs iniciais de `/auth/me`, `/clientes`, `/profissionais` e `/marcacoes` respondendo 200 em cerca de 100–150 ms; chamadas posteriores de polling levaram cerca de 10 s e responderam 200. A falha final do helper `waitForWorkspace` tentou clicar em um botao de retry que ja nao estava visivel e atingiu timeout de 150 s. Ajustamos o helper para clicar somente se o retry estiver visivel e registrar status HTTP sem cabecalhos; a causa dessa corrida fica inconclusiva ate nova rodada.
- Atualizamos apenas `e2e/**`: reserva de navegador agora fornece email/aceite; teste de contrato aceita os novos campos `antecedenciaMinimaMinutos` e `versaoAvisoPrivacidade`; seletor do teste legado foi restrito ao botao de navegacao. A rodada seguinte e necessaria para validar os fluxos desktop/mobile depois de liberar `/analytics.js`.
- A mensagem interna ao Tech Lead nao foi enviada: o caminho Orca autorizado informado pelo usuario (`C:\\Users\\Acer\\AppData\\Local\\Programs\\orca\\resources\\bin\\orca.exe`) nao existe/nao e executavel neste ambiente. O skill `orca-cli` instrui parar nesse erro e nao tentar outro executavel.
- O preview em 8082 permaneceu parado. Nenhum teste acionou gateway, pagamento ou e-mail real; nenhum DB foi limpo. O outro checkout Marcae nao foi acessado.

Comandos executados nesta rodada:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
# H2 em memoria, sem variaveis de DB persistente
$env:INK_TEST_DB_URL='jdbc:h2:mem:studio;DB_CLOSE_DELAY=-1'
$env:INK_TEST_DB_DRIVER='org.h2.Driver'
$env:INK_TEST_DB_USER='sa'
$env:INK_TEST_DB_PASSWORD=''
Remove-Item Env:DB_URL,Env:DB_USERNAME,Env:DB_PASSWORD -ErrorAction SilentlyContinue
.\mvnw.cmd -B '-Dmaven.repo.local=C:\Users\Acer\.m2\repository' '-Djavax.net.ssl.trustStoreType=Windows-ROOT' '-Djavax.net.ssl.trustStore=NONE' test
# PostgreSQL dedicado
$env:INK_TEST_DB_URL='jdbc:postgresql://127.0.0.1:55439/marcae_validation_tests'
$env:INK_TEST_DB_DRIVER='org.postgresql.Driver'
$env:INK_TEST_DB_USER='ink_validation'
$env:INK_TEST_DB_PASSWORD='validation-only'
Remove-Item Env:DB_URL,Env:DB_USERNAME,Env:DB_PASSWORD -ErrorAction SilentlyContinue
.\mvnw.cmd -B '-Dmaven.repo.local=C:\Users\Acer\.m2\repository' '-Djavax.net.ssl.trustStoreType=Windows-ROOT' '-Djavax.net.ssl.trustStore=NONE' verify
# Playwright, DB_URL=jdbc:postgresql://127.0.0.1:55439/marcae_validation_browser
$env:DB_URL='jdbc:postgresql://127.0.0.1:55439/marcae_validation_browser'
$env:DB_USERNAME='ink_validation'
$env:DB_PASSWORD='validation-only'
Remove-Item Env:INK_TEST_DB_URL,Env:INK_TEST_DB_DRIVER,Env:INK_TEST_DB_USER,Env:INK_TEST_DB_PASSWORD -ErrorAction SilentlyContinue
node node_modules/playwright/cli.js test
```
