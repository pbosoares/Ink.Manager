# Ink.Manager — Estúdio

Interface old school e limpa, integrada à API Spring Boot na pasta existente do projeto.

## Interface

- Login e criação de conta.
- Visão geral com sessões do dia, próximas sessões e total de clientes.
- Agenda com busca, filtros de data/status, criação, edição e exclusão.
- Cadastro de cliente e marcação na mesma tela, com um único botão para salvar tudo.
- Clientes com busca, cadastro, edição e exclusão.
- Layout adaptado para celular, formulários acessíveis, estados vazios e mensagens de erro.

O frontend está em `src/main/resources/static`. HTML, CSS e JavaScript sem etapa de build adicional, servido em `/` pela própria API. Os dados são persistidos no PostgreSQL. A sessão JWT fica no `sessionStorage` da aba e é removida ao sair ou quando a API informa que expirou. A tipografia usa Google Fonts, com fontes locais de fallback.

## Executar

Requisitos: Java 25 e PostgreSQL, ou Docker Compose.

Configure as variáveis de ambiente (não publique valores reais):

```text
DB_URL=jdbc:postgresql://localhost:5432/ink_manager
DB_USERNAME=postgres
DB_PASSWORD=<senha-do-seu-banco>
JWT_SECRET=<segredo-aleatorio-com-pelo-menos-32-bytes>
```

No Windows, dentro de `ink-manager-api`:

```powershell
.\mvnw.cmd spring-boot:run
```

No Linux/macOS: `./mvnw spring-boot:run`.

Abra **http://localhost:8080** e crie sua conta. Na nova marcação, preencha nome, telefone, data, horário e descrição e clique em **Salvar cliente e marcação**. Se a pessoa já estiver cadastrada, escolha **Cliente já cadastrado**. Cliente e marcação são gravados juntos em uma transação: se ocorrer um erro, nenhum cadastro parcial fica salvo. Clientes também podem ser cadastrados pela tela Clientes.

Para Docker Compose, configure `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` e `JWT_SECRET` em um arquivo `.env` local (ignorado pelo Git), e execute `docker compose up --build`. A interface também abre na porta 8080. O PostgreSQL do Compose é exposto localmente na porta 5433.

## Testes

```powershell
.\mvnw.cmd test
```

Os testes de integração usam H2 em memória, sem acessar o banco do estúdio, e verificam autenticação, arquivos públicos, validação, CRUD e integridade dos vínculos. PostgreSQL continua sendo o banco em produção.

## Contrato da API

`/auth/cadastro` e `/auth/login` são públicos. `/clientes` e `/marcacoes` exigem `Authorization: Bearer <token>`.

- `400`: dados inválidos ou campos obrigatórios ausentes.
- `401`: credenciais inválidas ou sessão ausente/expirada.
- `404`: registro não encontrado.
- `409`: e-mail duplicado ou conflito de integridade, como excluir um cliente com marcações.

O frontend e a API compartilham a mesma origem, sem configuração de CORS adicional. As contas compartilham os dados deste estúdio; esta versão não implementa múltiplos estúdios nem permissões por função. O cadastro de contas permanece público, como na API original. Antes de expor o sistema na internet, defina quem pode criar contas e configure HTTPS.

Esta versão não impede sobreposição de horários: essa regra depende de definir profissionais e duração das sessões.
