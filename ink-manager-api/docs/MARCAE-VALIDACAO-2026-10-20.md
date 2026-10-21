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
