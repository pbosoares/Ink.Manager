# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Visao geral

Marcae (nome tecnico: Ink Manager) e um SaaS multi-estudio de agenda, clientes, equipe,
reservas publicas e recebimentos. Toda a aplicacao ativa fica em `ink-manager-api/`
(Spring Boot 4.1, Java 25, PostgreSQL 18; H2 nos testes). Os `.java` da raiz, `lib/` e
`clientes.txt` sao um CLI historico, fora do build: nao altere nem use como referencia.

A marca visivel e "Marcae", mas pacotes (`ink.manager.api`), endpoints e identificadores
"Ink Manager" sao mantidos por compatibilidade. Nao renomeie. Codigo, nomes de dominio
e docs estao em portugues (estudio, marcacao, profissional, recebimento, assinatura).

## Comandos (executar em `ink-manager-api/`)

- Rodar: `.\mvnw.cmd spring-boot:run` (exige `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` >= 32 bytes; veja `.env.example`).
- Testes (H2): `.\mvnw.cmd verify` (o CI usa `./mvnw clean test`).
- Um teste: `.\mvnw.cmd test -Dtest=StudioIntegrationTest#nomeDoMetodo`.
- Testes em PostgreSQL: definir `INK_TEST_DB_URL`, `INK_TEST_DB_DRIVER=org.postgresql.Driver`, `INK_TEST_DB_USER`, `INK_TEST_DB_PASSWORD`. Use sempre um banco exclusivo: os testes apagam os dados de negocio. `MigrationUpgradeTest` so roda em PostgreSQL.
- E2E (Playwright): gerar o JAR (`.\mvnw.cmd package`), `npm ci`, `npx playwright install chromium`, apontar `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` para outro banco exclusivo e rodar `npm run test:e2e` (um arquivo: `npx playwright test e2e/studio.spec.cjs`). A matriz da lista de espera roda com `npm run test:e2e:waitlist-matrix`.
- Nao rode suites H2, PostgreSQL e Playwright em paralelo; rode em serie.

## Arquitetura

- **Camadas:** `controller` -> `service` -> `repository` (Spring Data JPA) com `model`/`dto`. O schema e gerenciado so pelo Flyway (`ddl-auto=validate`). Toda mudanca de schema vira uma nova migracao `src/main/resources/db/migration/V<n>__*.sql`, aditiva e nunca editando uma existente. SQL especifico de PostgreSQL vai em `db/vendor/postgresql`.
- **Multi-tenant:** o estudio vem sempre do JWT, via `config/AcessoAtual` (claims `estudioId`/`usuarioId`), nunca de um parametro da requisicao. Toda consulta e escrita deve filtrar pelo estudio da sessao, inclusive nos vinculos entre entidades (ex.: profissional/servico de outro estudio).
- **Sessoes:** JWT com versao de sessao validada a cada requisicao (`SessionJwtConverter`). Sair, redefinir senha ou desativar um usuario revoga os tokens anteriores. Papeis: responsavel (owner) e funcionario; recebimentos e configuracoes sao so do responsavel.
- **Cadeia de filtros (`SecurityConfig`):** rate limit de auth e de rotas publicas, `RetentionOperatorFilter` (rotas `/internal/retencoes-financeiras/**`), `ManifestoRestauracaoGateFilter` e `AssinaturaAccessFilter`. Este ultimo devolve 402 em gravacoes quando a avaliacao e o periodo pago venceram; a leitura continua liberada. Rotas publicas novas precisam ser liberadas explicitamente em `SecurityConfig`.
- **Agenda:** `DisponibilidadeService` calcula horarios livres em passos de 15 minutos a partir de expedientes, intervalos e bloqueios, e o servidor revalida tudo ao salvar. AGENDADA/CONFIRMADA ocupam o horario; CANCELADA/CONCLUIDA o liberam, e status encerrados nao reabrem. Edicoes usam a `version` lida por ultimo (lock otimista): conflito de agenda ou de edicao retorna 409 e entrada invalida retorna 400.
- **Reservas publicas** (`/publico/{link}`, `agendar.html`): sem conta, idempotentes por chave UUID. Preco e duracao vem do catalogo no servidor e ficam gravados como snapshot.
- **Recebimentos:** a confirmacao de valor conclui o atendimento e bloqueia a edicao. Os totais de semana (segunda a domingo) e mes sao calculados pela data da confirmacao no fuso `STUDIO_TIME_ZONE`, com valores em `BigDecimal`.
- **Cobranca:** o provedor em uso e o **Stripe** (`StripeGateway`, `billing.provider=stripe`, `STRIPE_SECRET_KEY`/`STRIPE_WEBHOOK_SECRET`, webhook `POST /assinatura/stripe-webhook` verificado por `StripeWebhookSignature`). `MercadoPagoGateway` e codigo legado; as secoes do README sobre Mercado Pago estao desatualizadas. O webhook consulta o provedor e aplica o resultado de forma idempotente. A conciliacao roda em lote a cada 10 minutos. O retorno do checkout nunca ativa o acesso.
- **E-mail:** padrao outbox (`EmailOutboxService`/`Worker`/`Job`) via SMTP. Sem SMTP configurado, as funcoes de e-mail ficam indisponiveis e o resto do sistema segue funcionando. Nao existe envio simulado. Links usam `PUBLIC_URL`, nunca o host enviado na requisicao.
- **Workers agendados:** expiracao de ofertas da lista de espera, expurgo de estudios encerrados (retencao), outbox e lembretes.
- **Frontend:** HTML/JS puro, sem build, em `src/main/resources/static` (`index.html` + `app.js` do painel, `agendar.*` da reserva publica, `landing/`). Arquivo estatico novo tambem precisa entrar no `permitAll` do `SecurityConfig`.

## CI/CD

`.github/workflows/ci.yml` roda `./mvnw clean test` em push/PR para `main`. Num push para
`main` que altere caminhos implantaveis (`src/`, `pom.xml`, `Dockerfile`, `deploy/`,
`compose.production.yml`, `.mvn/`, `mvnw`), o workflow **faz deploy automatico em producao
na VPS** via `deploy/deploy-release.sh`. Mudancas so em docs ou testes nao disparam deploy.
Producao usa `compose.production.yml` com Caddy (`deploy/Caddyfile`).

## Documentacao do projeto

`ink-manager-api/docs/README.md` e o indice: `MARCAE-CONTRATOS.md` (contratos da API),
`OPERACAO.md` (producao, deploy, backup) e o runbook de backup/restauracao.
`docs/historico/` guarda relatorios datados anteriores a producao; nao os trate como
estado atual. `src/test/MARCAE-TESTES.md` descreve a cobertura dos testes.
