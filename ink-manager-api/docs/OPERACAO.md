# Operacao da plataforma SaaS

O operador publica o backend Java e o PostgreSQL uma unica vez. Os assinantes
acessam a URL publica, criam o estudio e configuram sua equipe pelo navegador.

Segredos e variaveis de banco, JWT, SMTP e pagamentos ficam no ambiente da
hospedagem, nunca no frontend ou no Git. Cada instancia deve compartilhar o mesmo
banco e a mesma chave JWT. Configure HTTPS antes de expor credenciais ou pagamentos.

As migracoes Flyway sao executadas pela plataforma. O Hibernate apenas valida.
Antes de atualizar banco antigo sem Flyway, restaure uma copia e inspecione tabelas,
IDs e vinculos. DB_BASELINE_ON_MIGRATE e falso por padrao; baseline 0 so deve ser
habilitado temporariamente em uma copia revisada. A mudanca para SaaS associa os
dados antigos a um estudio legado. Nao execute os testes em banco de clientes.

O backup diario roda pelo workflow `.github/workflows/backup-manual.yml`
(habilitado pela variavel `MARCAE_BACKUP_SCHEDULE_ENABLED`): dump cifrado com age,
restore efemero de verificacao e artifact com retencao de 30 dias. Procedimentos em
[MARCAE-BACKUP-RESTORE-RUNBOOK.md](MARCAE-BACKUP-RESTORE-RUNBOOK.md). Monitore saude,
erros de e-mail e processamento de pagamentos.

A administracao de acessos e feita pelo responsavel no painel: desativar usuario
invalida as sessoes imediatamente. Sair encerra as sessoes daquela conta; redefinir
senha tambem. O limite de autenticacao e por endereco observado e por instancia;
quando houver varias instancias, aplique limite compartilhado na infraestrutura.

## Producao

A plataforma roda numa VPS com Docker Compose (`compose.production.yml`): PostgreSQL 18,
a API e o Caddy (`deploy/Caddyfile`) com HTTPS automatico. A API escuta so em
`127.0.0.1` e o Caddy responde 404 para `/internal/*`.

Um push em `main` que altere caminhos implantaveis dispara o deploy apos os testes
(veja [deploy/GITHUB-CD.md](../deploy/GITHUB-CD.md)). O `deploy/deploy-release.sh`
cria uma pasta por release, sobe a nova imagem, espera `/actuator/health` responder
UP por HTTPS e, se falhar, volta para a imagem anterior.
