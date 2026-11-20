# Marcae

[![CI](https://github.com/pbosoares/Ink.Manager/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/pbosoares/Ink.Manager/actions/workflows/ci.yml)

SaaS multi-estudio de agenda, clientes, equipe, reservas publicas e recebimentos.
Cada responsavel cria o proprio estudio pelo navegador, configura servicos e
expedientes e compartilha um link publico para clientes reservarem sem conta.

> Nome tecnico: **Ink Manager**. Pacotes (`ink.manager.api`), endpoints e
> identificadores mantem esse nome por compatibilidade; a marca visivel e Marcae.

## Funcionalidades

- **Agenda por profissional:** expedientes semanais, intervalos, folgas e horarios livres em passos de 15 minutos, com revalidacao no servidor e protecao contra reservas simultaneas.
- **Reserva publica:** link fixo por estudio, sem conta, com preco e duracao vindos do catalogo.
- **Lista de espera:** ofertas automaticas quando um horario e liberado.
- **Recebimentos:** confirmacao de valor e totais de semana e mes.
- **Equipe e acessos:** papeis de responsavel e funcionario, com revogacao imediata de sessoes.
- **Assinatura:** R$ 29,90/mes com 14 dias de avaliacao, por cartao recorrente ou Pix via Stripe.
- **Isolamento:** os dados de cada estudio ficam separados em todas as consultas e vinculos.

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 25, Spring Boot 4.1 (Web MVC, Security/JWT, Data JPA, Mail) |
| Banco | PostgreSQL 18, schema versionado com Flyway (H2 nos testes) |
| Frontend | HTML, CSS e JavaScript puros, sem etapa de build |
| Pagamentos | Stripe (checkout hospedado e webhooks) |
| Testes | JUnit 5 (H2 e PostgreSQL) e Playwright (E2E) |
| Infra | Docker Compose, Caddy (HTTPS), GitHub Actions (CI/CD e backup) |

## Estrutura do repositorio

```
.
├── ink-manager-api/        Aplicacao (codigo, testes, deploy e docs)
│   ├── src/main/java/      controller -> service -> repository
│   ├── src/main/resources/ application.properties, migracoes Flyway, frontend em static/
│   ├── src/test/           testes de integracao e de unidade
│   ├── e2e/                testes Playwright
│   ├── deploy/             Caddyfile e script de release da VPS
│   └── docs/               documentacao (comece por docs/README.md)
├── .github/                CI/CD, backup diario e Dependabot
└── legado/                 CLI original, fora do build (somente historico)
```

## Como rodar localmente

Requisitos: JDK 25 e um PostgreSQL 18 (ou `docker compose up postgres`).

```powershell
cd ink-manager-api
copy .env.example .env     # preencha DB_*, JWT_SECRET (>= 32 bytes) e o resto
.\mvnw.cmd spring-boot:run
```

A aplicacao sobe em `http://localhost:8080`.

## Testes

```powershell
cd ink-manager-api
.\mvnw.cmd verify          # suite completa em H2
```

Para rodar em PostgreSQL e com Playwright, veja o
[README da aplicacao](ink-manager-api/README.md). O CI roda as suites H2 e
PostgreSQL em todo push e pull request.

## Deploy

Um push em `main` que altere caminhos implantaveis (`src/`, `pom.xml`, `Dockerfile`,
`deploy/`, `compose.production.yml`) publica em producao na VPS depois que os
testes passam, com health check e rollback automatico. Mudancas so em
documentacao ou testes nao fazem deploy. Detalhes em
[docs/OPERACAO.md](ink-manager-api/docs/OPERACAO.md).

## Documentacao

- [README da aplicacao](ink-manager-api/README.md): regras de negocio, configuracao e pagamentos
- [Indice da documentacao](ink-manager-api/docs/README.md): operacao, contratos da API, backup e privacidade
