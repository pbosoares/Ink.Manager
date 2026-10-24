# Validacao final incremental Marcae — 20/10/2026

## Revisao validada

Checkout `main` sobre HEAD `dac73d062a1b9d21238cdaa424402ac06f6e309d`, com alteracoes locais integradas de Backend, Front End, Engenharia e Tech Lead. O commit ainda nao foi criado. O pacote `target/api-0.0.1-SNAPSHOT.jar` foi montado a partir deste codigo para o Playwright. A suite Java foi executada depois das correcoes de integracao de Jackson 3 e da coluna `version` na V14; nenhuma alteracao de codigo ocorreu entre os testes H2, PostgreSQL e navegador.

## Resultados

- **H2 / Java 25.0.3:** `mvnw.cmd -q test`, `MAVEN_H2_EXIT=0`; 61 testes, 0 falhas, 0 erros, 1 teste ignorado (teste de upgrade PostgreSQL, condicionado ao driver). Log: `target/validacao-h2-2026-10-20.log`.
- **PostgreSQL 18.4 / Java 25.0.3:** mesma suite, `MAVEN_POSTGRES_EXIT=0`; 61 testes, 0 falhas, 0 erros, 0 ignorados. Testes executados em schema exclusivo `marcae_validation_1d11620d1a5b`, sem apagar ou alterar tabelas existentes. O schema temporario foi mantido para nao remover dados do banco. Log: `target/validacao-postgresql-2026-10-20.log`.
- **Playwright / Chromium headless:** `e2e/marcae-browser.spec.cjs`, 2 passaram — desktop 1366x900 e mobile 390x900. Os cenarios cobrem catalogo, reserva publica sem autenticacao, retry idempotente apos resposta perdida, confirmacao/recebimento na agenda e telas de recebimentos. Capturas: `target/marcae-dashboard-1366.png` e `target/marcae-dashboard-390.png`. Playwright encerrou apos desligar somente o servidor de teste que ele mesmo iniciou.
- **JavaScript e diff:** `node --check` passou para `agendar.js`, `simple.js`, `waitlist.js` e `acao-email.js`; `git diff --check` passou, com avisos informativos de conversao LF/CRLF em arquivos existentes.
- **Build:** `mvnw.cmd -q -DskipTests package` concluiu e produziu o JAR usado no navegador.

A suite de integracao cobre os contratos da lista de espera, isolamento, idempotencia, cancelamento/oferta, concorrencia/aceite e outbox. Os testes unitarios cobrem lembretes, links de acao, retries limitados e envio desabilitado. O browser E2E nao fez envio real de email.

## Limites e operacao

`MAIL_ENABLED=false` permanece o padrao; nao foram configuradas credenciais do Resend, dominio remetente ou DNS, e nenhum email real foi enviado. SMTP continua com semantica at-least-once: uma falha de confirmacao local depois de aceite do provedor pode causar duplicidade. Os cenarios de lista de espera foram exercitados pela API integrada; a captura Playwright atual cobre a jornada geral de agendamento/recebimentos, nao uma interacao visual ponta a ponta da lista de espera.

Os avisos de Mockito/JDK e do Flyway sobre H2 2.4 foram apenas warnings. O schema de validacao PostgreSQL permanece no banco local, isolado dos schemas de dados existentes. Logs, JAR, capturas e relatorios Surefire ficam sob `target/` e nao devem ser adicionados ao Git.

## Push

Destino preparado: `origin` (`https://github.com/pbosoares/Ink.Manager.git`), branch `main`. O workflow `.github/workflows/ci.yml` executa testes no push para `main`; nao ha workflow de deploy neste checkout. Commit/push ainda pendentes da revisao final do diff e da selecao explicita de arquivos, excluindo dados/logs/segredos e arquivos temporarios de coordenacao Orca.

## Checkpoint adicional de validacao - 21/10/2026

Esta rodada foi executada em copia isolada `target/stripe-validation-copy`, sem usar o `target` original, sem tocar no PostgreSQL da porta 5432 e preservando o processo local da porta 8080. O codigo Java/testes da copia foi comparado ao checkout antes da suite; 161 arquivos sob `src/**`, configuracao Maven e wrapper tiveram `MISSING=0` e `HASH_MISMATCH=0`. JDK confirmado: Temurin 25.0.3+9.

- **PostgreSQL isolado 18.4 / porta 55439:** `mvnw.cmd -q test`, exit 0; `StudioIntegrationTest`: 45 testes, 0 falhas, 0 erros, 0 ignorados. Banco `marcae_validation_stripe`, loopback 127.0.0.1, usuario local `ink_validation`; sem acesso ao servico 5432. Log: `target/stripe-validation-copy/target/validacao-postgresql-jdk25-2026-10-21.log`.
- **Empacotamento:** `mvnw.cmd -q -DskipTests package`, exit 0; JAR ficou somente na copia isolada.
- **Playwright regular, serial, Chromium headless, API real no PostgreSQL isolado e `MAIL_ENABLED=false`:** 3/4 passaram. Passaram `marcae-browser.spec.cjs` desktop e mobile e `marcae-contract.spec.cjs`. Falhou `studio.spec.cjs`, `cadastro, cliente recorrente, edicao, persistencia e nova sessao`, em `waitForWorkspace` apos criar a primeira marcacao; `#main-action` permaneceu desabilitado. Log: `target/stripe-validation-copy/target/playwright-2026-10-21.log`; trace: `target/stripe-validation-copy/test-results/studio-cadastro-cliente-re-e45e7--persistencia-e-nova-sessao/trace.zip`.
- **Diagnostico do trace:** apos `POST /marcacoes` iniciado pelo submit de `#save-editor` (201 em ~372 ms), o refresh da agenda disparou GET `/clientes`, `/marcacoes`, `/profissionais` e `/auth/me`; os quatro terminaram sem resposta (`status -1`, `net::ERR_ABORTED`) apos aproximadamente 20,0 s. O retry pelo botao da tela repetiu GET `/clientes`, `/marcacoes`, `/profissionais` e `/auth/me`, tambem sem resposta apos aproximadamente 20,0 s. O evento de UI iniciador do retry e o botao `#page-error button` (“Tentar novamente”), acionado pelo helper `waitForWorkspace`. A duracao coincide com o timeout de fetch do cliente, mas o trace nao prova a causa subjacente no Chromium/servidor. Nenhum header ou token foi incluido neste relatorio.
- **Limites:** a suite Playwright foi interrompida pelo primeiro teste com falha; nao houve segunda tentativa. O E2E de lista/oferta nao foi executado porque nao existe SMTP sink falso local nas portas 1025/8025; nenhum email externo foi enviado. Nenhum teste/build rodou em paralelo.
- **Setup:** a primeira tentativa PostgreSQL falhou antes da execucao dos testes porque o processo Maven filho usou Java 21 contra classes compiladas para Java 25. Corrigida a configuracao do ambiente do processo para Temurin 25, a suite completa passou; esse erro nao e falha de produto.

## Checkpoint de infraestrutura fornecido pelo titular (22/10/2026)

O titular confirmou por SSH que `/opt/marcae/.current-release` aponta para `08a6c1d3f96219d8392d870708d665022c7aeb70`; containers API/proxy/PostgreSQL ativos, PostgreSQL healthy, HTTPS raiz 200 e `/actuator/health` 200/UP. Esta evidencia identifica o commit implantado e substitui a anotacao anterior de versao desconhecida. Nao prova que o checkout local atual corresponde ao release.

SMTP do Resend foi autenticado no container com EHLO, STARTTLS validado e AUTH LOGIN (235). MAIL_ENABLED permanece false. Nenhum comando MAIL FROM, RCPT TO ou DATA foi enviado; nao houve email. Resultado prova autenticacao, nao entrega/recebimento. Nao repetir segredo nem habilitar envio. O teste controlado de email exige autorizacao especifica do destinatario.

Nao houve alteracao de servicos nem novo deploy. Pendencias funcionais seguem: falha E2E autenticada sem causa raiz, jornada visual lista/oferta com sink loopback, matriz final da mesma revisao, revisao de diff, backup/restauracao isolada e rollback. Stripe segue independente e aguardando verificacao por email da chave temporaria; nenhuma cobranca ou assinatura live.

## Checkpoint de engenharia - 22/10/2026 (somente leitura)

### Revisao do E2E autenticado

Trace existente em `target/stripe-validation-copy/test-results/studio-cadastro-cliente-re-e45e7--persistencia-e-nova-sessao/trace.zip` confirma: respostas anteriores aos GETs de workspace receberam HTTP 200 e headers JSON, mas uma carga levou aproximadamente 10,1-10,2 s. No lote posterior iniciado apos `POST /marcacoes` (201 em ~372 ms), GET `/clientes`, `/marcacoes`, `/profissionais` e `/auth/me` terminaram sem resposta: status `-1`, nenhum header de resposta no trace e `net::ERR_ABORTED` aos ~20,0 s. O retry da interface repetiu os quatro abortos no mesmo limite. O trace registra ainda chamadas autenticadas via APIRequestContext com resposta rapida em outra fase; isso nao corresponde ao mesmo lote nem prova a causa do abort.

Nao foi encontrado log de stdout/stderr da aplicacao associado ao Playwright; `target/playwright-2026-10-21.log` contem o runner e a falha, nao access log do Spring/servidor. Assim, a latencia de respostas 200 anteriores e um sinal objetivo para investigacao Backend, mas nao ha evidencia suficiente para atribuir o lote abortado a API, banco, Chromium ou transporte. Nenhum teste foi repetido e o timeout/critério permanecem intactos.

### Harness de waitlist preparado, bloqueado para execucao

`playwright.waitlist.config.cjs` valida antes de iniciar `webServer` o prefixo do banco PostgreSQL local dedicado, `MAIL_ENABLED=true`, host SMTP em loopback, `PUBLIC_URL` local, mailbox HTTP em loopback e auth/STARTTLS SMTP desligados; `reuseExistingServer=false` e app isolada na porta 8087. A spec usa o renderer/outbox real, extrai link da caixa Mailpit e cobre entrada anonima, aceite e replay em 1366x900/390x900.

A revisao encontrou duas lacunas de preflight: (1) usuario e senha SMTP sao apenas exigidos como nao vazios e copiados do ambiente do runner para o processo Java; o guard nao prova que sejam valores ficticios. O proximo run deve usar sentinelas locais fixas e nunca encaminhar variaveis Resend/SMTP de producao. (2) a disponibilidade da API Mailpit e verificada pela spec depois do startup da aplicacao; o config valida formato/loopback, mas nao a existencia do sink antes de Flyway/worker iniciar. Antes da liberacao, o operador deve comprovar sink Mailpit reconhecido, listener restrito a 127.0.0.1, porta/API correspondentes e mailbox acessivel; manter auth e TLS desabilitados somente nesse processo efemero. Neste checkpoint nao havia listener nas portas 1025/8025, portanto nao iniciar servidor, Maven ou Playwright.

O Front confirmou seletores/contrato estaveis e o roteiro descrito acima. Permanecem sem cobertura visual de gestao/cancelamento da waitlist (cancelamento da reserva base e setup via API), sem assercao explicita de overflow/capturas desktop-mobile e sem smoke de autenticacao que submeta formularios; o smoke atual verifica apenas renderizacao/alternancia e ausencia de `pageerror`. Validar empilhamento dos campos inicio/fim a 390px quando houver release de browser.

### Backup, restauracao e rollback: plano e pre-condicoes

`deploy-artifacts/HANDOFF-CD.md` e anterior ao checkpoint da VPS; descreve backup como preparado e nao executado. `deploy-artifacts/configure-backup.sh` foi somente lido. O script instala timer systemd, habilita-o e roda dump imediatamente; depois cria outra database e restaura nela dentro do mesmo container/cluster PostgreSQL de producao. Portanto, executa mutacoes no host e no cluster de producao e nao comprova restauracao isolada. Nao deve ser executado como esta para validar restore. Nao ha evidencia local de dump, checksum ou teste de restauracao. O script tambem nao configura retencao nem copia externa; depende de `/opt/marcae/backups` ja existir (o instalador local a cria), e deixa database de restore no cluster.

Pre-condicoes para um teste seguro: titular/Lead confirmar release corrente, origem/idade e checksum do backup, politica de retencao, espaco e permissao do local privado; criar cluster e volume separados, sem portas publicas e sem rota de saida; usar somente variaveis/segredos de teste (nunca `.env` de producao), `MAIL_ENABLED=false`, sem gateway de pagamentos; verificar dump com `pg_restore --list`, restaurar com `--exit-on-error` em database nova desse cluster isolado e comparar historico Flyway/contagens de tabelas essenciais e integridade de sequencias. Backup de producao pode conter dados pessoais: nao copiar para workstation nem publicar logs/dumps. Nenhuma restore ou conexao VPS foi executada nesta revisao.

O artefato `deploy-artifacts/marcae-8690a6d-vps.tar.gz` e um pacote de release antigo, nao backup do banco. O handoff/instalador local ainda referencia tag `8690a6d`; nao corresponde ao release informado pelo titular em 22/10 (`08a6c1d3f96219d8392d870708d665022c7aeb70`). O HEAD local tem esse hash, mas `git status` permanece sujo; igualdade de HEAD nao prova que a arvore integrada local corresponda ao container ativo. Nao usar instalador antigo como procedimento de rollback.

Rollback seguro de aplicacao deve manter release anterior e atual em diretorios distintos e alternar somente o apontador de release de forma atomica, preservando `.env`/segredos compartilhados, volume PostgreSQL e volumes Caddy; nao executar `down -v`, remover volumes, apagar databases ou restaurar sobre a database ativa. Antes, confirmar que migrations novas sao compativeis com o binario anterior. Se nao forem, parar para plano de recuperacao aprovado; restaurar dump sobre banco ativo nao e rollback comum e pode descartar gravacoes recentes. O layout/apontador real da VPS nao foi inspecionado remotamente; Lead continua dono de qualquer comando de mutacao.

### Processos observados localmente

Leitura de listeners em 22/10: `127.0.0.1:8080` PID 28872 (Java), `0.0.0.0:5432` PID 9728 (PostgreSQL existente), `127.0.0.1:55439` PID 34652 (PostgreSQL isolado de validacao). PID 20112 e outro processo Java sem listener identificado nesta leitura. Sem listener local em 8087, 1025 ou 8025. Nenhum processo foi iniciado, encerrado ou alterado. A VPS descrita pelo titular permanece separada desta observacao local.

### Addendo do Backend ao diagnostico autenticado (22/10/2026)

A revisao somente leitura do Backend confirmou no mesmo artefato da copia que `app.js:56` passa `AbortSignal.timeout(20000)` a cada `fetch`; `app.js:78` inicia os quatro GETs com `Promise.all`. Portanto, a causa imediata do `ERR_ABORTED` no browser e o timeout cliente de 20 s. Isso nao explica por que os pedidos ficam sem resposta antes do limite: a causa no servidor permanece inconclusiva. O Backend considera plausiveis latencia/contencao de pool, threads ou banco, sem evidencia conclusiva; os GETs que haviam respondido em ~10 s sustentam investigar isso. Para separar causas faltam logs da mesma janela com duracao SQL, espera/uso do pool e estado de threads. Sem repetir suite ou alterar timeout.

O Backend encaminhou essa avaliacao ao Lead. Minha leitura do terminal confirmou a entrega; nao enviei mensagem duplicada.

### Mapa dos GETs e protocolo de observabilidade (22/10/2026)

O Backend mapeou a cadeia dos quatro GETs: `/clientes` -> `ClienteController` -> `ClienteService` -> `ClienteRepository`; `/marcacoes` -> `MarcacaoController` -> `MarcacaoService` e repositorios de marcacao/recebimento; `/profissionais` -> `ProfissionalController` -> `ProfissionalService` e repositorios de profissional/horario (consulta de horarios por profissional); `/auth/me` -> `AuthController` -> `UsuarioService`. Todos passam por `SessionJwtConverter`, que consulta o usuario no banco a cada requisicao; `/auth/me` consulta novamente. Efeito potencial, nao causa provada. O filtro de assinatura nao consulta cobranca nos GETs.

Configuracao atual: SQL logging desligado; Actuator exposto somente para health/info; sem access log, metricas de pool ou thread dump preconfigurados. Uma unica reproducao local futura deve correlacionar: access log Tomcat `%t %m %U %s %D` sem query/body/headers; amostras a cada 1s de `pg_stat_activity` com estado/wait/pid/idade e bloqueadores, sem query/parametros; delta de `pg_stat_statements` somente se a extensao ja existir; metricas Hikari (active/idle/pending/max) e Tomcat (busy/current/max) expostas temporariamente apenas em loopback; `jcmd Thread.print` em t0, +5s e +15s. Nao instalar extensao, resetar estatisticas, expor Actuator externamente, logar bind params, nem coletar producao. Interpretacao: `wait_event_type=Lock`/blocking pid aponta lock; query age/delta SQL aponta consulta lenta; pool pending com active=max aponta espera de conexao; busy=max com stacks http-nio aponta saturacao de threads. Nenhum teste, processo ou alteracao foi executado pelo Backend.

### Validacao H2 em copia isolada da revisao atual (22/10/2026)

Copia em `target/marcae-validation-copy-20261022`: SHA-256 comparado nos 157 arquivos de `src/**`, sem divergencias. O servidor do usuario em `127.0.0.1:8080` permaneceu ativo e intocado. Com Temurin 25.0.3, suite Maven H2 terminou exit 0: 69 testes, 0 falhas, 0 erros, 1 skip (upgrade PostgreSQL condicionado ao driver). Email e credenciais de pagamento permaneceram desativados/ausentes. Log isolado: `target/marcae-validation-copy-20261022/target/validacao-h2-2026-10-22.log`. JAR do checkout original segue datado de 20/10 e nao foi usado para validar o frontend atual. Nenhum Playwright ou teste PostgreSQL desta revisao ainda.

### Preflight PostgreSQL da revisao atual - 22/10/2026 (bloqueado antes da execucao)

A suite PostgreSQL nao foi iniciada. Antes de qualquer build, a consulta local encontrou `java.exe` PID 20112 com marcadores de processo Maven/teste e `java.exe` PID 28872 com marcador da aplicacao. A consulta retornou somente PID/nome e classificacao sanitizada; argumentos completos nao foram registrados. Como nao foi possivel confirmar que PID 20112 nao executa teste/build de outro operador, nao iniciei outro Maven e nao encerrei nenhum processo. Nao ha processo nomeado `mvn`/`mvnw` nesta leitura; isso nao exclui Maven executado dentro de Java.

A leitura de presenca de variaveis encontrou apenas `DB_PASSWORD` configurada; `DB_URL`, `DB_USERNAME` e as tres variaveis `SPRING_DATASOURCE_*` nao estao presentes. O valor nao foi lido nem impresso, e sua origem/escopo de validacao nao pode ser confirmada, portanto nao foi usado. Nenhum arquivo `.env` foi consultado. Sem credencial local verificavel e sem exclusividade comprovada do processo Java, a tentativa foi bloqueada antes de conectar ao banco `marcae_validation_browser` em `127.0.0.1:55439`; nenhum schema ou dado foi alterado e nenhum novo log de teste foi gerado. O resultado H2 69/0/0/1 skip informado pelo operador permanece registrado no checkpoint anterior.

Para retomar, o titular/Lead deve confirmar a classificacao/encerramento seguro do PID 20112 e disponibilizar uma configuracao de validacao por processo com URL, usuario e senha autorizados para o banco dedicado; nao usar credenciais de producao nem tentar valores alternativos. Manter `MAIL_ENABLED=false`, credenciais de pagamento ausentes e schema de teste exclusivo.

### Checkpoint do Lead sobre PostgreSQL isolado - 22/10/2026

O Lead confirmou preservar o PID 20112, o servidor localhost do usuario e o `target` original. H2 permanece em 69 testes, 0 falhas, 0 erros e 1 skip, conforme resultado informado pelo operador. Nao ha URL, usuario e credencial de validacao explicitamente confirmados em conjunto; a variavel de senha sem origem validada nao foi lida nem usada, e nenhum `.env` foi consultado. PostgreSQL permanece bloqueado ate haver configuracao de validacao explicitamente autorizada para copia isolada e schema dedicado. Nenhuma nova tentativa foi feita; nenhum processo, servidor, banco ou dado foi alterado.

### Current isolated validation results 22-Oct-2026

The latest checkout source was copied to `target/marcae-validation-copy-20261022`. SHA-256 comparison of all 157 files under `src/**` reported zero mismatches. H2 reports show 69 tests, 0 failures, 0 errors, 1 PostgreSQL-only skip. PostgreSQL 18.4 ran in a newly initialized cluster bound only to `127.0.0.1:55441`, in database `marcae_validation_20261022` and fresh schema `marcae_validation_20261022_e2e`; Surefire reports show 69 tests, 0 failures, 0 errors, 0 skips. The isolated copy packaged successfully with `-DskipTests`; fresh JAR: `target/marcae-validation-copy-20261022/target/api-0.0.1-SNAPSHOT.jar`. No production credentials, payment calls or real emails were used.

### E2E final da lista de espera na revisao integrada (22/10/2026)

Antes do browser, comparei SHA-256 dos 157 arquivos de `src/**` do checkout e da copia usada nos testes Java: zero divergencias. Os 32 recursos em `src/main/resources/**` tambem correspondem byte a byte ao JAR testado; SHA-256 do JAR `3E3335408F905362AE53BD781D1D6DE1CCB761A93FA06C7E0A41F343E53808FE`. Assim o artefato inclui a API, V16, waitlist.js/agendar.html e o visual Editorial Cobalto atual.

Playwright executou cada viewport em uma invocacao e instancia efemera novas, com a spec versionada atual, schema PostgreSQL dedicado `marcae_validation_20261022_waitlist_retry2` e SMTP/Mailpit apenas em `127.0.0.1:1025/8025`. Desktop 1366px: 1 teste passou, exit 0; mobile 390px: 1 teste passou, exit 0. Cada jornada cobriu cadastro anonimo na espera, cancelamento da reserva-semente, captura do renderer/outbox real no sink local, abertura do token em documento novo, preview, aceite, replay e reserva ativa unica. Nenhum email externo foi enviado e o limite de 10 POSTs/min permaneceu inalterado.

O JDK 21 padrao impediu uma tentativa inicial antes do app iniciar; a execucao seguinte usou JDK 25. Uma configuracao de teste inicial apontou para um nome de database inexistente e falhou antes de conectar/aplicar migrations; foi corrigida para o database/scheme de validacao ja existentes. As duas execucoes finais usaram a copia JAR indicada acima. O wrapper Playwright manteve o filho Java depois da assercao; confirmei o PID pelo listener exclusivo 127.0.0.1:8087, encerrei somente esse processo efemero e ambos os runners terminaram com exit 0. A porta 8080 do localhost do usuario permaneceu ativa e intocada; 8087 ficou livre ao final.

O resultado Java H2/PostgreSQL permanece o do mesmo `src/**` comparado: H2 69 testes, 0 falhas/erros, 1 skip PostgreSQL-condicional; PostgreSQL 69 testes, 0 falhas/erros/skips. O timeout do frontend continua 20s. A causa de uma janela autenticada lenta (~10s) segue intermitente e sem causa-raiz demonstrada; duas reproducoes anteriores registraram uma janela lenta e outra abaixo de 500ms, sem bloqueadores PostgreSQL observados. Nao considerar essa causa investigada como resolvida.

The waitlist E2E preflight succeeded against the loopback-only SMTP/API sink and a separate validation schema. Playwright started the ephemeral app on `127.0.0.1:8087`, then failed before exercising the browser flow: the worker reloaded `playwright.waitlist.config.cjs`, inherited its own fixed `MAIL_ENABLED=true` sentinel, and `validation-env.cjs` rejected it as `MAIL_ENABLED=true is allowed only in the ephemeral Playwright processes`. The attempt was stopped without weakening guards and without external email. Engineering must correct the harness while preserving rejection of arbitrary inherited mail variables and external SMTP, then the desktop/mobile journey must be rerun serially.

After the attempt, the ephemeral app, SMTP/API sink and temporary PostgreSQL server were stopped. The user's existing app at `127.0.0.1:8080` remained available; the PostgreSQL data directory and synthetic fixtures remain under ignored `target/`. The prior authenticated studio E2E timeout and its server-side cause are still unresolved; current regular Playwright matrix was not run. Backup/isolated restore/rollback, final diff review, clean commit/push and release remain outstanding. Production `MAIL_ENABLED=false`; the Stripe live webhook remains disabled and its credentials have not been installed in the VPS.

### Diagnostico autenticado local, duas execucoes isoladas (22/10/2026)

O E2E de agenda/autenticacao passou duas vezes em app efemero separado e DB/schema sintetico dedicado (mesma arvore `src/**`: 157 arquivos iguais por SHA-256, email desligado). Em uma execucao, os quatro GETs iniciados apos `POST /marcacoes` responderam HTTP 200, mas em 10,037-10,053 s. No intervalo amostrado a cada segundo, PostgreSQL nao mostrou sessoes ativas, transacoes abertas, wait de lock ou bloqueadores; a coleta de Hikari/threads foi iniciada tarde nessa primeira tentativa. Na segunda execucao o teste passou em 27,2 s, sem GET acima de 500 ms e com Hikari active=0, pending=0, max=10. Portanto o problema e intermitente e a causa raiz continua sem prova; nao aumentar o timeout. O processo diagnostico proprio foi encerrado e o localhost do usuario permaneceu 200. Estes dois testes nao substituem matriz oficial desktop/mobile da revisao final; a jornada visual waitlist ainda depende da correcao aprovada do roteiro, e backup/restauracao/rollback seguem pendentes.

### Matriz regular e diagnóstico autenticado — 23/10/2026

Na mesma cópia/JAR SHA-256 `3E3335408F905362AE53BD781D1D6DE1CCB761A93FA06C7E0A41F343E53808FE`, a matriz regular Playwright passou 4/4 com runner exit 0: browser desktop 1366 px, browser mobile 390 px, contrato público e `studio.spec.cjs` autenticado. A porta efêmera 8087 foi liberada e o localhost 8080 permaneceu ativo.

Em execução instrumentada isolada, o access log Tomcat registrou somente método, caminho, status e duração, sem query, corpo ou headers. Os GETs da carga autenticada responderam HTTP 200; máximos observados na matriz: `/clientes` 165,2 ms, `/marcacoes` 203,2 ms, `/profissionais` 191,5 ms e `/auth/me` 162,8 ms. Na reprodução instrumentada anterior, os máximos foram 117,7 ms, 157,8 ms, 150,3 ms e 109,4 ms, respectivamente. Isso demonstra o comportamento normal nestas execuções, não explica os registros antigos de 10–20 s. A causa raiz da lentidão intermitente permanece inconclusiva, e o timeout de 20 s não foi alterado.

Uma execução diagnóstica inicial teve a asserção do cenário de estúdio aprovada, mas o encerramento manual atingiu o runner Node antes de sua saída limpa (exit 1); não foi contada como validação. A execução posterior foi encerrada corretamente e consta acima com exit 0.

### Rollback de imagem contra V16 em DB sintetico isolado (23/10/2026)

Preflight de processos: PIDs 20112 e 28872 nao existiam; `Get-Process java,javaw` nao encontrou JVM ativa antes do build. A leitura WMI da arvore foi negada, mas ambos os PIDs foram confirmados ausentes. Havia processos PostgreSQL preexistentes desde 21/10; foram preservados sem consulta/encerramento. Nenhum teste Maven, Playwright, VPS, imagem Docker local, email ou pagamento real foi executado.

Artefatos: HEAD/release source `08a6c1d3f96219d8392d870708d665022c7aeb70`. O JAR anterior foi reconstruido de um `git archive` limpo desse commit em `target/rollback-v16-sim-20261023-a`, com Temurin 25.0.3 e `mvnw.cmd -q -o -DskipTests package`; SHA-256 `F2875C61A2EA99265856DDE946C05CF48FE4A8824E5ABF3AF157F4A20284BE66`; pacote inclui migrations ate V15 e nao V16. JAR V16 ja validado, SHA-256 `3E3335408F905362AE53BD781D1D6DE1CCB761A93FA06C7E0A41F343E53808FE`. Ambos incluem Flyway Core e PostgreSQL 12.4.0. O digest da imagem Docker implantada nao estava disponivel localmente: este ensaio reproduz o JAR do commit de release, nao o container byte a byte.

Isolamento: cluster PostgreSQL 18.4 novo, criado em `target/rollback-v16-sim-20261023-a/pgdata`, listener `127.0.0.1:55443`; DB `marcae_validation_rollback_v16_20261023_a`, verificado inexistente antes de criar. URL usada: `jdbc:postgresql://127.0.0.1:55443/marcae_validation_rollback_v16_20261023_a`; role local `ink_validation`, trust restrito pelo listener loopback. MAIL_ENABLED=false, chaves Stripe/Mercado Pago vazias; JWT de teste foi sintetico e nao registrado. Nenhum dado de producao foi copiado.

Estado antes da imagem antiga: JAR V16 iniciou com JWT sintetico e health HTTP 200; Flyway 12.4.0 validou 17 migrations, schema atual 16. Historico V15 checksum `1875464855`, sucesso; V16 checksum `-2045024609`, sucesso, installed_rank 17. V16 presente: `provedor` NOT NULL DEFAULT MERCADOPAGO, `subscription_id` nullable e indice `idx_checkout_provider_subscription`. Contagens sinteticas: estudios=1 (seed do esquema); usuarios=0, cliente=0, marcacoes=0, assinatura_checkouts=0, email_outbox=0.

Resultado da imagem/JAR anterior: iniciou no mesmo DB sem desativar a validacao Flyway; log registra `Successfully validated 17 migrations`, schema na versao 16, aviso de que 16 e mais nova que a ultima migration local 15, `No migration necessary`, startup completo em 29.584 s e health HTTP 200. A configuracao e o ambiente nao definiram `spring.flyway.validate-on-migrate=false`; o log confirma que a validacao ocorreu. Portanto, a hipotese de que o Flyway necessariamente recusaria a imagem anterior foi refutada para estes JARs/Flyway 12.4.0: a versao aplicada futura foi tolerada com warning.

Estado depois: Flyway continua na V16 com os mesmos checksums/sucesso; colunas e indice permanecem; todas as contagens acima ficaram iguais. A imagem antiga nao alterou nem reverteu o banco. Isso prova startup/health do JAR reconstruido contra o schema V16, nao um smoke funcional completo nem o rollback do container Docker/tag real; confirme o digest e a configuracao da imagem implantada antes de generalizar. A aplicacao efemera e o cluster deste ensaio foram parados; os arquivos, dados sinteticos e logs foram preservados em `target/rollback-v16-sim-20261023-a`; portas 8089 e 55443 ficaram livres. Logs: `postgres.log`, `app-v16.stdout.log`, `app-v16.stderr.log`, `app-v16-baseline.stdout.log`, `app-v16-baseline.stderr.log`, `app-v15-rollback.stdout.log`, `app-v15-rollback.stderr.log`.


### Fechamento PostgreSQL e bloqueio da matriz browser (24/10/2026)

Hash canônico confirmado no checkpoint LEAD-047 para os 168 arquivos cobertos (src/**, e2e/**, pom.xml, Dockerfile e playwright*.config.cjs): `9e5bcff4c76f9f6067c3845056276b18b37c79d0b27052c2ead3a42852c995ad`. Este adendo altera somente este relatório, fora do conjunto usado no hash.

A suíte PostgreSQL isolada foi aprovada em Maven exit 0: 74 testes, 0 falhas, 0 erros e 0 skips (74/0/0/0). Resultado referente ao cluster dedicado LEAD-045; não houve rerun nesta etapa. Log preservado: `target/pg-validation-lead045-20261024-b/maven-postgresql.log`.

Playwright regular desktop/mobile não foi executado. A inicialização do mesmo cluster para a etapa browser, após o pedido formal de escalada, falhou ao abrir sockets de loopback: PostgreSQL registrou `Permission denied` para `::1` e `127.0.0.1`, seguido de `FATAL: could not create any TCP/IP sockets` e encerramento do servidor. Logs preservados: `target/pg-validation-lead045-20261024-b/postgres-playwright.log` e `target/pg-validation-lead045-20261024-b/postgres-final-attempt.log`. Nenhum banco novo, Maven adicional ou browser foi iniciado; a jornada waitlist não foi alcançada e o SMTP sink não foi iniciado.

No encerramento, 55446 e 8087 estavam sem listener; `pg_ctl status` informou que não havia servidor no data dir de validação. Não havia processo próprio Java/Maven/Playwright ativo. O PostgreSQL preexistente em 55439 e seus processos foram preservados sem consulta ou alteração. Nenhum código, teste, configuração ou VPS foi alterado nesta etapa.

### Guard fail-closed da matriz Playwright regular (24/10/2026)

Arquivos alterados neste ajuste: playwright.config.cjs e e2e/validation-env.cjs. A funcao database() mantem o requisito de JDBC PostgreSQL em host loopback, nome de banco com prefixo marcae_validation_ e usuario/senha em variaveis separadas da URL.

A config regular agora rejeita variaveis herdadas SPRING_*, MAIL_*, SMTP_*, RESEND_*, STRIPE_*, MP_*, MERCADOPAGO_*, BILLING_* e PAYMENT_*, alem de opcoes JVM que poderiam injetar propriedades. O ambiente do webServer e montado com allowlist de runtime e valores controlados: MAIL_ENABLED=false, campos SMTP vazios, credenciais Stripe/Mercado Pago vazias, URL publica loopback e JWT sintetico. Como Playwright mescla webServer.env com o processo pai, a validacao falha antes de iniciar o app quando esses overrides herdados estao presentes.

A config da waitlist e seus guards SMTP/Mailpit permanecem separados e inalterados. Checks: node --check playwright.config.cjs passou; node --check e2e/validation-env.cjs passou; git diff --check passou (exit 0).

Hash canonico apos o ajuste, calculado sobre 168 arquivos cobertos (src/**, e2e/**, pom.xml, Dockerfile e playwright*.config.cjs): 76b6016db53e3e14fb12a1af422e21899e6aff001900bdd20e71d237e5982f70. Este hash inclui os dois arquivos de harness e exclui este relatorio.

Nenhum servidor, Maven ou Playwright foi iniciado nesta etapa.

### Regra provisoria de retencao por recebimento para validacao integrada (24/10/2026)

Decisao operacional provisoria do titular: a retencao financeira e calculada individualmente por recebimento, usando o ano-calendario de America/Sao_Paulo. Para um recebimento no ano Y, manter ate o fim do ano Y+5; o expurgo so pode ocorrer a partir de 00:00:00 America/Sao_Paulo de 1 de janeiro de Y+6. Esta e uma escolha operacional provisoria, nao um prazo legal universal.

A retencao legal tambem e individual e requer justificativa, escopo e data de revisao. Um legal hold ativo ou revisao vencida bloqueia o expurgo. A validacao integrada futura deve cobrir recebimentos de anos distintos, o limite exato de virada do ano em America/Sao_Paulo, legal hold ativo e revisao vencida, confirmando que nenhum item bloqueado seja expurgado.

Backend esta ajustando apenas src/test/**. Nenhum build, Maven, Playwright, servidor ou alteracao de testes foi executado por Engenharia nesta rodada. A execucao da matriz permanece aguardando checkpoint do Lead.

### Tentativa da matriz integrada de retencao - bloqueada em H2 (24/10/2026)

HEAD observado: 08a6c1d3f96219d8392d870708d665022c7aeb70, com arvore de trabalho integrada/nao limpa. Hash canonico do snapshot de 172 arquivos cobertos (src/**, e2e/**, pom.xml, Dockerfile e playwright*.config.cjs; paths relativos em ordem ordinal, path + TAB + SHA-256 lowercase, LF final): 7fab8f2afc90fc1d6ffac4f437219377f65278032c3b3049c802149e9a98f6c2.

A primeira execucao H2 parou antes dos testes por erro de compilacao em StudioIntegrationTest.java:631 (Instant.plusDays). Com aceite do Backend, corrigi somente esse teste para clock.instant().plus(Duration.ofDays(1)); hash acima inclui a correcao.

O rerun H2 executou Surefire: total 82 testes, 2 falhas, 5 erros, 1 skip. StudioIntegrationTest: 57 testes, 2 falhas, 4 erros; RetentionConfigurationIntegrationTest: 1 erro; MigrationUpgradeTest: 1 skip condicional. Demais classes sem falhas/erros.

Descompassos objetivos: o worker consulta h.encerrado_em, mas V17 define retencoes_legais_financeiras.encerrada_em; testes consultam financeiro_concluido_em, enquanto V17 define expurgos_estudio.recebimentos_arquivo_concluido_em; testes tambem consultam coluna tipo, ausente na V17. O H2 reportou coluna H.ENCERRADO_EM inexistente. As duas falhas de assercao restantes sao antecedenciaMinimaSeAplicaNaDisponibilidadeENaReserva (esperava true, recebeu false) e expurgoExecutaEtapasArquivaRecebimentoSemPiiEIsolaTenant (esperava HTTP 202, recebeu 409 por entrega de email em andamento).

Logs/evidencias: target/validacao-h2-2026-10-24-retencao.log (falha inicial de compilacao); target/validacao-h2-2026-10-24-retencao-rerun.log (0 bytes, pois o wrapper PowerShell interrompeu no aviso Mockito enviado a stderr antes de registrar o exit nativo); target/surefire-reports/ink.manager.api.StudioIntegrationTest.txt e target/surefire-reports/ink.manager.api.RetentionConfigurationIntegrationTest.txt (relatorios atualizados nesta execucao). A soma Surefire, e nao o log vazio do wrapper, e a evidencia do rerun.

A matriz parou em H2. Nao houve PostgreSQL, browser Playwright, sink SMTP ou captura de instrumentacao autenticada; nenhum banco externo foi conectado e 55439 permaneceu intocado. Nenhum codigo main, migration, static, VPS ou dado real foi alterado. Proxima acao: Lead/Backend alinhar o contrato worker/V17 e as expectativas dos testes; emitir novo checkpoint/hash antes de retomar H2 -> PostgreSQL -> browser.


### ENG-008 - Matriz LEAD-054: H2 aprovado, PostgreSQL bloqueado (24/10/2026)

Fingerprint LEAD-047: 182 arquivos; SHA-256 canonico a628acea756a6764643147cd794676a6ddb511d47f9d3d549442a1ef740e61fc. O fingerprint permaneceu igual apos as execucoes. HEAD observado: 08a6c1d3f96219d8392d870708d665022c7aeb70.

H2: JDK Temurin 25.0.3; Maven exit 0; 87 testes, 0 falhas, 0 erros e 1 skip (MigrationUpgradeTest condicional para PostgreSQL). Banco em memoria jdbc:h2:mem:marcae_lead054; MAIL_ENABLED=false e nenhuma credencial real de email/pagamento. Log: target/validacao-h2-lead054-20261024.log.

PostgreSQL: o data dir legado LEAD-045 nao foi reutilizado apos as tentativas de identidade sem papeis postgres/Acer; nenhum banco foi criado nele. Foi criado um cluster descartavel novo em target/pg-validation-lead054-20261024/data, PostgreSQL 18.4, superuser ink_validation, autenticacao trust em loopback, ouvindo apenas em 127.0.0.1:55446. Conexao confirmada e o banco novo marcae_validation_lead054_pg_20261024 foi criado vazio. O PostgreSQL existente em 5432/55439 nao foi consultado nem alterado; DB_PASSWORD herdado nao foi lido/reutilizado.

Maven PostgreSQL: exit 1; 87 testes, 2 falhas, 0 erros e 0 skips. MigrationUpgradeTest passou e aplicou V19 no schema legado sintetico; o proprio teste removeu somente o schema que criou. As duas falhas foram RetentionConfigurationIntegrationTest.holdsSaoAutenticadosIsoladosIdempotentesEAuditaveis (linha 156) e operadorRevisaHoldDepoisDoEncerramentoSemReativarConta (linha 215): ambos esperavam HTTP 201 em POST /estudio/retencoes-financeiras e receberam 500. Causa objetiva no log: RetencaoFinanceiraService.generatedId (linha 301) chama GeneratedKeyHolder.getKey() quando PostgreSQL devolve varias colunas geradas; Spring lanca InvalidDataAccessApiUsageException. Falha especifica do PostgreSQL; H2 nao reproduziu. Nenhum codigo/teste foi editado por Engenharia.

Log Maven PG: target/pg-validation-lead054-20261024/maven-postgresql.log. Log do servidor: target/pg-validation-lead054-20261024/postgres-start.log. O cluster foi parado com pg_ctl exit 0; 55446, 8087, 1025 e 8025 ficaram sem listener e nao havia processo associado ao novo data dir.

A matriz parou na primeira suite PG reprovada. Playwright regular e waitlist desktop/mobile nao foram executados; instrumentacao da recarga autenticada nao foi coletada e o SMTP sink nao foi iniciado. O H2 nao foi repetido.

### ENG-011 - Preparacao da instrumentacao LEAD-057 (24/10/2026)

Escopo desta etapa: e2e/** e playwright*.config.cjs, com registro neste relatorio a pedido do titular. Nenhum arquivo de src/main, src/test, static ou VPS foi alterado nesta etapa.

Instrumentacao preparada para a futura recarga autenticada: amostragem a cada segundo das metricas Hikari active/idle/pending/max e Tomcat busy/current/max; amostragem sanitizada de pg_stat_activity (PID, state, wait event/type, idades de query/transacao e bloqueadores); diferenca de pg_stat_statements por queryid (calls, total_exec_time e mean_exec_time antes/depois), somente se a extensao ja existir, sem instalar/resetar; access log Tomcat no padrao %t %m %U %s %D, sem query string/headers; thread dumps sanitizados em t0, +5s e +15s.

A JVM dos dumps e identificada pelo PID gravado pelo wrapper do webServer efemero, validado por caminho de pidfile, comando do JAR e propriedade runId exclusiva; nenhuma busca global jcmd -l e usada. Se identidade, metricas ou amostragem estiverem indisponiveis, o estado fica registrado como unavailable/mismatch; nenhum criterio existente foi relaxado. O guard do harness exige exclusivamente PostgreSQL 127.0.0.1:55446 e banco marcae_validation_; 55439 permanece fora do alvo.

Arquivos da instrumentacao: playwright.config.cjs, e2e/marcae-browser.spec.cjs, e2e/diagnostics.cjs e e2e/diagnostic-server.cjs. Checks estaticos: node --check nos dois configs Playwright, no spec, no guard de ambiente, no modulo diagnostico e no wrapper passaram; git diff --check passou; verificacao estatica de whitespace/final newline dos dois arquivos novos passou.

Fingerprint canonico LEAD-047 recalculado duas vezes, com resultados identicos: 184 arquivos, SHA-256 7080dcc6132ea829d743eb91d56b707900d3302744743c9ce2b66595684f8463. Formula: src/**, e2e/**, pom.xml, Dockerfile e playwright*.config.cjs; paths relativos normalizados com /, ordenacao ordinal, linhas UTF-8 sem BOM path + TAB + SHA-256 lowercase + LF, incluindo LF final, SHA-256 do bloco.

Nenhum Maven, PostgreSQL, servidor, Playwright, Mailpit ou bind loopback foi iniciado. A matriz permanece bloqueada ate novo checkpoint explicito do titular.

### LEAD-059 - Revalidacao H2 apos instrumentacao (24/10/2026)

O fingerprint LEAD-047 foi recalculado duas vezes antes do teste e permaneceu estavel: 184 arquivos, SHA-256 `7080dcc6132ea829d743eb91d56b707900d3302744743c9ce2b66595684f8463`.

Com Temurin 25.0.3, `mvnw.cmd -q test` terminou com exit 0. Surefire confirmou 87 testes, 0 falhas, 0 erros e 1 skip condicional de PostgreSQL. As migrations locais chegaram a V19. A primeira tentativa usou o Java padrao do shell, que nao suporta `--release 25`; ela falhou antes de executar testes e foi corrigida apontando o processo ao JDK 25.

O PostgreSQL existente em `127.0.0.1:55439` foi preservado. Nenhuma aplicacao ou SMTP foi iniciado nesta etapa. A proxima etapa continua sendo a suite PostgreSQL em banco descartavel e isolado na porta `55446`; depois dela, Playwright regular instrumentado e lista/oferta com sink exclusivamente local.

### LEAD-060 - PostgreSQL aprovado na mesma revisao (24/10/2026)

Fingerprint LEAD-047 confirmado duas vezes, sem mudanca desde LEAD-059: 184 arquivos, SHA-256 `7080dcc6132ea829d743eb91d56b707900d3302744743c9ce2b66595684f8463`.

PostgreSQL 18.4 em cluster e banco novos para esta rodada (`127.0.0.1:55446`, `marcae_validation_lead059_pg_20261024`, role sintetica `ink_validation`): Maven exit 0, 87 testes, 0 falhas, 0 erros, 0 skips. Isso inclui `MigrationUpgradeTest` e exercita V17–V19 com o ajuste da chave gerada. O cluster foi parado com exit 0; o listener 55439 permaneceu preservado. Log: `target/pg-validation-lead059-20261024/maven-postgresql.log`.

H2 e PostgreSQL estao aprovados na mesma fingerprint. Falta agora compilar o JAR dessa arvore e executar Playwright regular desktop/mobile com recarga autenticada instrumentada; em seguida, validar lista/oferta nos dois viewports com SMTP sink local. Nenhum envio real foi feito.

### LEAD-063 - Primeiro Playwright regular na fingerprint atual (24/10/2026)

No mesmo fingerprint LEAD-047 `7080dcc6132ea829d743eb91d56b707900d3302744743c9ce2b66595684f8463`, o JAR foi empacotado com `mvnw.cmd -q -DskipTests package` (exit 0). Playwright regular Chromium headless terminou exit 0, 4/4: navegador 1366x900, navegador 390x900, contrato e cadastro. Log: `target/pg-validation-lead059-20261024/playwright-regular.log`; artefatos isolados: `target/playwright-lead059-regular`.

Na recarga autenticada, os quatro GETs completaram em aproximadamente 1.0 s tanto em 1366 quanto 390, sem reproduzir os abortos de 20 s. As amostras nao mostraram conexoes pendentes no Hikari nem waits PostgreSQL; `pg_stat_statements` nao estava instalado e nao foi adicionado. A coleta de thread dump ficou incompleta: os pidfiles da JVM existiam, mas o spec leu os metadados do projeto em vez dos metadados globais do config e registrou `pidfile_unavailable`. Engenharia recebeu LEAD-062 para corrigir somente essa leitura; os dumps e o diagnostico de causa-raiz ainda precisam ser repetidos antes de fechar o gate.

O banco `marcae_validation_lead059_browser_20261024`, app loopback e processo Playwright foram encerrados; parada PG exit 0. A porta 55439 e o servico local preexistente permaneceram intocados. Esta rodada prova os quatro cenarios e o tempo de resposta local, mas nao substitui a validacao final apos a correcao do coletor de threads.

### LEAD-067 - Matriz integrada apos LEAD-062 (24/10/2026)

Fingerprint LEAD-047 recalculado duas vezes antes da rodada: 184 arquivos, SHA-256 `0050d3aef1065883d8ec4b6e2088145915e7ae430ebb19032630cae1778c826f`. HEAD observado: `08a6c1d3f96219d8392d870708d665022c7aeb70`.

H2, Temurin 25.0.3: Maven exit 0; 87 testes, 0 falhas, 0 erros e 1 skip condicional para PostgreSQL. PostgreSQL 18.4 em cluster/banco descartaveis na porta 55446: Maven exit 0; 87 testes, 0 falhas, 0 erros e 0 skips, incluindo MigrationUpgradeTest e V17-V19. O cluster foi encerrado com exit 0; o PostgreSQL preexistente em 55439 foi preservado. Log PostgreSQL: `target/pg-validation-lead063-20261024/maven-postgresql.log`.

O JAR desta fingerprint foi empacotado com `mvnw.cmd -q -DskipTests package` (exit 0). Playwright regular terminou 4/4: 1366x900, 390x900, contrato e cadastro. A jornada integrada de lista/oferta que usa o renderer real e um SMTP sink somente em loopback passou 1/1 em 1366x900 e 1/1 em 390x900. Total browser nesta rodada: 6/6. Logs: `target/pg-validation-lead063-20261024/playwright-regular-final.log`, `playwright-waitlist-desktop.log` e `playwright-waitlist-mobile.log`; artefatos em `target/playwright-lead063-regular`, `target/playwright-lead063-waitlist-desktop` e `target/playwright-lead063-waitlist-mobile`.

A recarga autenticada completou localmente em aproximadamente 1.0 s nos dois viewports; os quatro GETs monitorados responderam HTTP 200, sem falha de rede. As amostras nao indicaram fila Hikari nem espera PostgreSQL por lock. `pg_stat_statements` nao estava instalado e nao foi adicionado. O SMTP sink guardou as mensagens somente em memoria; MAIL_ENABLED=true existiu apenas no processo efemero de teste e nao houve envio real.

Permanece incompleta a coleta dos dumps de thread: o harness registrou `pidfile_unavailable`, embora o arquivo `application.pid` tenha sido criado. Logo, esta rodada nao identifica a causa historica dos abortos de 20 s na VPS. A lentidao nao foi reproduzida nesta maquina; o gate de diagnostico so fecha quando os dumps forem coletados corretamente e a instrumentacao passar na revisao final. Apps, sink e cluster foram encerrados; portas 55446, 8087, 1025 e 8025 ficaram livres. Esta evidencia valida os testes de codigo e os fluxos browser nesta fingerprint, nao configura Stripe/email de producao nem substitui os demais gates de release.

### LEAD-070 - Revalidacao e falha do coletor de PID (24/10/2026)

Fingerprint LEAD-047 recalculado duas vezes: 184 arquivos, SHA-256 `5b5ca5323ec6a985bfdf4f3bf2fab0e2d250c8600ee4f55778f43212686ed1f5`. HEAD observado permanece `08a6c1d3f96219d8392d870708d665022c7aeb70`.

H2, Temurin 25.0.3: Maven exit 0; 87 testes, 0 falhas, 0 erros e 1 skip condicional. PostgreSQL 18.4 em cluster descartavel novo, bind `127.0.0.1:55446`: Maven exit 0; 87 testes, 0 falhas, 0 erros e 0 skips, incluindo MigrationUpgradeTest. O JAR foi reconstruido (exit 0), SHA-256 `95acedda26e2898d82d3ee904f6a127cbe76b9943b9c694402cea6af8933a3d3`. Logs: `target/validacao-lead068-h2.log`, `target/pg-validation-lead068-20261024-a/maven-postgresql.log` e `target/validacao-lead068-package.log`.

Playwright regular terminou exit 1: os cenarios de contrato e cadastro passaram (2/2); os dois cenarios autenticados (1366 e 390) falharam antes da captura porque `effectiveDiagnosticsMetadata` nao encontrou `application.pid`. O erro especifico esta em `target/validacao-lead068-playwright-regular.log`; artefatos em `target/playwright-lead068-regular-20261024-a`. A jornada waitlist nao foi executada nesta rodada. O cluster descartavel foi parado e 55446/8087/1025/8025 ficaram livres.

O PID file e criado pelo wrapper e removido somente quando o processo filho encerra. O teste confirmou a indisponibilidade no caminho esperado, mas ainda nao determinou se o wrapper/JVM terminou cedo, se houve diferenca de ciclo de vida do webServer ou outro motivo. Portanto nao ha dumps de thread desta rodada e a causa historica da latencia continua inconclusiva. A Engenharia esta investigando o ciclo de vida; nenhuma nova execucao sera feita ate Lead revisar a correcao. Os resultados funcionais da fingerprint anterior `0050d3...` (browser 6/6) permanecem validos apenas para aquela fingerprint, nao para substituir este reteste incompleto.

### LEAD-074 - Matriz integrada final (24/10/2026)

Fingerprint LEAD-047 conferido duas vezes depois de todas as fases: 184 arquivos, SHA-256 `936e9d005669b8a558b247815144dada72c7fe54ec0d0f5210b509a6a86c532e`. Metodo: enumerar `src/**`, `e2e/**`, `pom.xml`, `Dockerfile` e `playwright*.config.cjs`; path relativo com `/`, ordenacao ordinal, linhas UTF-8 sem BOM `path<TAB>sha256 lowercase` com LF final e SHA-256 do bloco. Nao houve alteracao no conjunto coberto durante a matriz. A primeira tentativa de calcular com `Sort-Object -Comparer` falhou por incompatibilidade do PowerShell 5.1; nao foi teste. `Array.Sort` ordinal produziu o fingerprint acima.

H2, Temurin JDK 25.0.3: Maven exit 0; 87 testes, 0 falhas, 0 erros e 1 skip (MigrationUpgradeTest condicional para PostgreSQL). MAIL_ENABLED=false e sem credenciais reais. Log: `target/validacao-lead073-h2.log`.

PostgreSQL 18.4: Maven exit 0; 87 testes, 0 falhas, 0 erros e 0 skips, incluindo MigrationUpgradeTest/V18/V19. Cluster efemero dedicado em `target/pg-validation-lead073-20261024-a/data`, bind somente `127.0.0.1:55446`; banco Maven `marcae_validation_lead073_pg_20261024_a`. Log Maven: `target/pg-validation-lead073-20261024-a/maven-postgresql.log`; logs do cluster em `target/pg-validation-lead073-20261024-a/`. O banco waitlist separado foi `marcae_validation_lead073_waitlist_20261024_a` no mesmo cluster.

Build do JAR: `mvnw.cmd -q -DskipTests package`, exit 0. SHA-256 de `target/api-0.0.1-SNAPSHOT.jar`: `95acedda26e2898d82d3ee904f6a127cbe76b9943b9c694402cea6af8933a3d3`. Log: `target/validacao-lead073-package.log`.

Playwright regular, sem repeticao: exit 0, 4/4 (viewports 1366x900 e 390x900, contrato e studio). Log: `target/validacao-lead073-playwright-regular.log`; saida: `target/playwright-lead073-regular-20261024-a/`.

Instrumentacao da recarga autenticada: os dois reloads completaram em 1000 ms (390 px) e 1022 ms (1366 px); cada resumo teve uma amostra e samplingState `sampled`. Seis dumps de thread ficaram `captured`, nos offsets 0/5000/15000 ms para cada viewport. Evidencias: `authenticated-reload-diagnostics.jsonl` e `authenticated-reload-thread-{0,5000,15000}ms.txt` nos dois diretorios de teste `marcae-browser-*` em `target/playwright-lead073-regular-20261024-a/`. Os dois JSONL referenciam `target/diagnostics/access/lead057-14192-1792865460304.log` (288 linhas, formato `%t %m %U %s %D`, sem query string/headers). As amostras de metricas responderam HTTP 200; Hikari pending=0 e nenhum blocker/lock wait no PostgreSQL. `pg_stat_statements` nao estava instalado e nao foi consultado. Anomalia registrada: na amostra 1366 px Hikari informou active/idle/pending/max `2/8/0/0`, embora active+idle=10; a outra amostra informou `0/10/0/10`. Tomcat busy/current/max: `7/13/200` e `8/10/200`; PostgreSQL sessions/active/wait-event sessions/blockers: `10/1/9/0` e `10/0/10/0`. A contagem de wait-event nao indicou lock wait; blockers permaneceu zero. Dumps e resumos foram sanitizados, sem query/bind, PII ou tokens.

Lista/oferta com renderer real e SMTP sink sintetico estritamente em loopback: primeiro invocador PowerShell foi interrompido por NativeCommandError ao receber aviso de cor do Node (`NO_COLOR` junto com `FORCE_COLOR`); o log `target/validacao-lead073-waitlist-desktop.log` continha apenas `Running 1 test using 1 worker`, sem resultado. Esta tentativa e preflight/invocacao incompleta, nao e contada como teste. O Java 38484 da tentativa foi confirmado como webServer do projeto em 8087 com pai encerrado e finalizado; nenhum DB ou artefato foi apagado.

Rerun desktop serial, com ErrorActionPreference Continue e stdout/stderr separados: exit 0, 1/1 passou em 51.4 s. Rerun mobile: exit 0, 1/1 passou em 50.3 s. Em ambos, `lista de espera e oferta real sem duplicidade` passou. Logs: `target/validacao-lead073-waitlist-desktop.stdout.log` e `.stderr.log`; `target/validacao-lead073-waitlist-mobile.stdout.log` e `.stderr.log`. Artefatos: `target/playwright-lead073-waitlist-desktop-20261024-a/` e `target/playwright-lead073-waitlist-mobile-20261024-a/`. O sink guardou mensagens somente localmente/em memoria; nao houve email externo, Stripe live ou cobranca.

Encerramento: sink PID 29664 parado e portas 1025/8025 livres. Cluster parado graciosamente por `pg_ctl -D target/pg-validation-lead073-20261024-a/data stop -m fast -w -t 30`, exit 0; PID 34404 encerrou. O data dir/bancos e logs foram preservados. Portas 55446, 8087, 1025 e 8025 ficaram sem listener; processos 34404/38484/29664 e runner Playwright nao permaneceram. PostgreSQL preexistente 55439 e servicos 5432 nao foram tocados. Nenhum deploy, commit, email real ou cobranca foi feito.
### LEAD-085 - Matriz integrada final (24/10/2026)

Fingerprint congelado LEAD-047: 189 arquivos, SHA-256 `fac9f2cc638ecd003d110cc18f44c4737478bdf04d0e713951086c72de732bef`. Formula: `src/**`, `e2e/**`, `pom.xml`, `Dockerfile` e `playwright*.config.cjs`; caminhos relativos com `/`, ordenacao ordinal, linhas UTF-8 sem BOM `path<TAB>sha256-lowercase` separadas por LF e LF final. Fingerprint suplementar de configs/deploy: 10 arquivos, SHA-256 `50622ee959e94ebc9cef54cd323361f2591c14e6363275a65887419c15c2b2f1`. Os hashes conferem com LEAD-085; nenhuma alteracao foi feita no conjunto congelado durante a matriz.

Classe focada: `.\mvnw.cmd -Dtest=ManifestoEncerramentoGitHubAnchorTest test`, JDK Temurin 25.0.3, exit 0; 7 testes, 0 falhas, 0 erros, 0 skips. Log: `target/validacao-lead085-manifesto-focused.log`.

H2: `.\mvnw.cmd test`, JDK Temurin 25.0.3, exit 0; 94 testes, 0 falhas, 0 erros, 1 skip (MigrationUpgradeTest condicional para PostgreSQL). `MAIL_ENABLED=false`; sem credenciais externas. Log: `target/validacao-lead085-h2.log`.

PostgreSQL: suite Maven exit 0; 94 testes, 0 falhas, 0 erros, 0 skips, incluindo MigrationUpgradeTest. Cluster PostgreSQL 18.4 descartavel em `target/pg-validation-lead085-20261024-a/data`, bind exclusivo `127.0.0.1:55446`, banco `marcae_validation_lead085_pg_20261024_a`, usuario `ink_validation`; `55439` nao foi consultada nem tocada. Log: `target/pg-validation-lead085-20261024-a/maven-postgresql.log`; logs de inicializacao e servidor permanecem no mesmo diretorio.

Build: `.\mvnw.cmd -DskipTests package`, exit 0. SHA-256 do `target/api-0.0.1-SNAPSHOT.jar`: `e4a2442883e4ca531a0625a43a73be4762f2fa4df2a625a18785ddedf128840c`. Log: `target/validacao-lead085-package.log`.

Playwright regular: exit 0, 4/4 (1366x900, 390x900, contrato e studio). Saida: `target/validacao-lead085-playwright-regular.stdout.log`; stderr: `target/validacao-lead085-playwright-regular.stderr.log`; artefatos em `target/playwright-lead085-regular-20261024-a/`.

Diagnostico das duas recargas autenticadas: 390 px concluiu em 1011 ms; 1366 px em 1009 ms. Foram capturados seis dumps, offsets 0/5000/15000 ms em cada viewport, nos diretorios `marcae-browser-*` sob `target/playwright-lead085-regular-20261024-a/`. Os resumos JSONL ficaram `sampled`; access log em `target/diagnostics/access/lead057-39720-1792880669763.log`, 288 linhas, sem query string nem headers. Metricas HTTP responderam 200. Hikari active/idle/pending/max: 0/10/0/10 (390 px) e 4/6/0/10 (1366 px); Tomcat busy/current/max: 7/13/200 e 8/10/200. PostgreSQL: 10 sessoes em cada amostra, 0 lock waits e 0 blockers. `pg_stat_statements` nao estava instalado e nao foi consultado. Dumps e resumos foram sanitizados, sem SQL/bind, PII ou tokens.

Lista/oferta: SMTP fake em memoria, limitado a `127.0.0.1:1025` e API Mailpit local `127.0.0.1:8025`; nenhum email saiu para fora. Banco separado `marcae_validation_lead085_waitlist_20261024_a` no cluster descartavel. Desktop 1366x900: exit 0, 1/1 passou em 37.7 s; log `target/validacao-lead085-waitlist-desktop.stdout.log`, stderr `.stderr.log`, artefatos `target/playwright-lead085-waitlist-desktop-20261024-a/`. Mobile 390x900: exit 0, 1/1 passou em 35.5 s; log `target/validacao-lead085-waitlist-mobile.stdout.log`, stderr `.stderr.log`, artefatos `target/playwright-lead085-waitlist-mobile-20261024-a/`.

Encerramento: app, sink e cluster efemeros foram finalizados; portas 55446, 8087, 1025 e 8025 sem listener, processos desta matriz encerrados. Data dir, bancos e logs foram preservados; nenhum DROP ou limpeza foi executado. PostgreSQL 55439, VPS, Stripe live, cobrancas e envio de email real nao foram usados. Nao houve alteracao de codigo nem novo teste apos a fingerprint congelada.
