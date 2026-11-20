# Instruções do projeto Marcaê para o Octo

O Octo lê este arquivo antes de responder a um agente. As regras técnicas completas
estão em [CLAUDE.md](CLAUDE.md); aqui ficam as decisões que o Octo pode tomar sozinho
e as que precisam do dono do projeto.

## Produção: o que nunca aprovar sozinho

O sistema está em produção e atende estúdios reais. Um push ou merge em `main` que
altere `ink-manager-api/src/`, `pom.xml`, `Dockerfile`, `deploy/`,
`compose.production.yml`, `.mvn/` ou `mvnw` **publica automaticamente na VPS**.

Responda **não** (deny) e devolva a decisão ao usuário quando um agente pedir para:

- fazer push, merge ou rebase em `main`, ou `git push --force` em qualquer branch;
- rodar `gh pr merge`, aprovar PR ou disparar workflow (`gh workflow run`);
- acessar a VPS (`ssh`, `scp`) ou alterar `/opt/marcae/.env`;
- editar uma migração Flyway que já existe em `db/migration/` (só se cria `V<n>__*.sql` nova);
- apagar dados, rodar SQL contra um banco que não seja de teste ou mexer em backups;
- ler, imprimir ou commitar segredos (`.env`, chaves Stripe/Resend, `JWT_SECRET`);
- enviar e-mail, mensagem ou cobrança real.

## O que pode aprovar

- Criar branch própria, commitar nela e fazer push dessa branch.
- Abrir pull request para `main` (o merge fica com o usuário).
- Rodar os testes H2: `./mvnw verify` dentro de `ink-manager-api/` (use JDK 25:
  `C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot`).
- Ler código, docs e logs do CI (`gh run view`, `gh pr checks`).
- Instalar dependências de teste locais (`npm ci`) sem alterar `package.json`.

## Ao decidir entre opções

- Prefira a mudança menor que resolve a tarefa; não refatore o que não foi pedido.
- Toda consulta ou gravação filtra pelo estúdio da sessão (JWT), nunca por parâmetro.
- Código, nomes de domínio e textos da interface ficam em português.
- Marca visível é "Marcaê"; pacotes, endpoints e `Ink Manager` não são renomeados.
- Dados mostrados em telas de exemplo são fictícios; nunca invente depoimentos,
  números de clientes ou resultados apresentados como reais.
- Na dúvida sobre produto, preço, texto comercial ou dados reais: pergunte ao usuário.

## Agentes do projeto

Definidos em `.claude/agents/` (Claude Code):

| Agente | Para quê |
|---|---|
| `backend-java` | API Spring Boot, regras de agenda, migrações, e-mail e cobrança |
| `frontend-painel` | Painel, reserva pública e landing (HTML/CSS/JS sem build) |
| `revisor-seguranca` | Revisão somente leitura: isolamento entre estúdios, auth, cobrança |
| `testes-qa` | Testes JUnit (H2/PostgreSQL) e Playwright; não altera código de produção |
| `seo` | SEO do site público: técnico, conteúdo, dados estruturados e Search Console |

Para trabalho em paralelo, cada agente usa a própria worktree e branch; o resultado
volta como PR, revisado pelo `revisor-seguranca` antes do merge pelo usuário.
