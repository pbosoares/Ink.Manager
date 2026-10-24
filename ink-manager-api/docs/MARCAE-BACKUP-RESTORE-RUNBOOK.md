# Runbook preparado — backup, restauração e expurgo

**Estado atual:** o workflow sintético `.github/workflows/backup-synthetic-proof.yml` foi publicado somente em `ops/backup-proof` e executado com sucesso; o artifact `12576999832` contém apenas fixture fictícia e teve retenção efetiva confirmada em 30 dias. O workflow manual de produção `.github/workflows/backup-manual.yml` não foi executado. Nenhum dump, backup ou restore de produção foi feito.

**Bloqueio do script legado:** `deploy-artifacts/configure-backup.sh` não é um mecanismo aprovado para produção. A leitura estática mostrou que ele grava dump PostgreSQL em claro dentro do host e restaura no mesmo cluster Compose ativo. Não execute nem instale esse script. A preparação da identidade restrita não executou esse script nem copiou dados.

Este runbook complementa [DEVOPS-SRE-STATUS.md](DEVOPS-SRE-STATUS.md), que contém a proposta técnica detalhada de fluxo cifrado por GitHub Actions e restauração em runner efêmero. A aprovação do documento não autoriza a execução.

## Decisões selecionadas e gates restantes

O titular selecionou os artifacts privados do GitHub Actions, backup diário, expiração em 30 dias, RPO de até 24 horas e RTO-alvo de 4 horas. A identidade SSH restrita, a chave age e o workflow manual foram preparados; o segredo SSH foi registrado no ambiente privado. Isso não autoriza cópia/restauração de dados reais nesta etapa.

1. **Destino:** auditoria read-only confirmou repositório privado `pbosoares/Ink.Manager`, Actions habilitado, colaborador direto `pbosoares` com `admin`, sem teams/deploy keys, e `production-backup` restrito a `main`, sem reviewer obrigatório. A cota de billing não foi acessível; confirmar capacidade e aprovar processamento de dados reais antes de backup real.
2. **Retenção e RPO/RTO:** o artifact sintético `12576999832` expirará em 30 dias conforme consulta API; isso não é evidência de backup de produção. RPO de 24h é alvo, RTO de 4h é meta ainda não medida.
3. **Chave age:** par gerado fora do checkout; destinatário público cadastrado no ambiente. A identidade privada fica no perfil local do titular, com ACL restrita; falta guardar uma cópia segura para recuperação em outro dispositivo.
4. **Acesso de backup:** conta `marcae-backup`, chave pública dedicada e comando forçado root-owned provisionados. `ssh -N -T` autenticou por chave sem invocar o wrapper/helper. A chave privada correspondente está no secret `MARCAE_BACKUP_SSH_KEY` do ambiente `production-backup`. Não reutilizar `VPS_SSH_KEY`.
5. **Restauração:** nenhum dado real foi copiado/restaurado. O workflow manual de produção não executa restore; a prova sintética restaurou somente a fixture em `synthetic_restore`. A identidade de descriptografia é local ao titular; o primeiro restore real precisa ocorrer em ambiente efêmero isolado, sem acesso a serviços ou credenciais de produção.
6. **Manifesto:** a implementação local existe; armazenamento externo e reaplicação do manifesto antes de reabrir um restore continuam pendentes.

### Ensaio sintético local — PostgreSQL 18.4 (24/10/2026)

Criei um cluster descartável em `target/backup-restore-synthetic-20261024/pgdata`, exclusivamente em `127.0.0.1:55448`, e os bancos `marcae_validation_backup_20261024` e `marcae_validation_restore_20261024`. O primeiro recebeu três marcadores fictícios; `pg_dump -Fc` foi cifrado com uma chave AES/HMAC efêmera em memória, o plaintext foi removido e o ciphertext foi decifrado com verificação de integridade/hash. `pg_restore` carregou os três registros no segundo banco. O servidor foi parado e a porta ficou livre; ciphertext sintético preservado em `target/`.

Este ensaio confirma dump/restore PostgreSQL isolado e um roundtrip de cifragem sintético; **não usa o formato age da workflow, não é off-host e não contém dados de produção**. O roundtrip age previamente registrado continua sendo uma evidência separada. Ainda faltam a cópia cifrada real no destino externo aprovado e um restore isolado desse artifact; nenhum dump real foi iniciado.

## Preparação operacional escolhida

- **Destino:** artifacts do GitHub Actions no repositório privado existente. A lista direta atual retornou somente `pbosoares` (`admin`), sem teams/deploy keys; artifacts são legíveis por quem tenha leitura do repositório. A API não revelou cota de billing/capacidade. O único artifact listado, `12576999832`, é sintético.
- **Frequência/retenção:** diário é o alvo, 30 dias por artifact, RPO-alvo de 24h. O arquivo local `backup-manual.yml` declara dispatch e cron, mas não aparece na lista da API para `main`; nenhum schedule de produção está comprovado como ativo e não houve execução. A configuração do repositório é 90 dias (máximo 400); o upload sintético teve expiração efetiva de 30 dias. Artifacts são temporários e não substituem arquivo permanente.
- **RTO-alvo:** 4 horas, a medir no primeiro restore.
- **Criptografia:** o fluxo da workflow cifra em age antes de publicar. A chave privada não vai para GitHub, VPS, checkout ou logs.
- **Acessos:** a conta não interativa, a chave pública e o comando forçado somente leitura estão provisionados; a autenticação sem sessão passou. O secret SSH dedicado está registrado no ambiente `production-backup`. O Compose permanece `root:root 0666`, inalterado; o helper não o lê nem executa.
- **Manifesto:** um banco restaurado não serve tráfego antes de reaplicar os encerramentos posteriores ao snapshot; o armazenamento externo do manifesto ainda não está configurado.

O registro acima descreve a autorização inicial de preparação. A autorização posterior cobriu somente a prova sintética na branch `ops/backup-proof`; não houve autorização/execução de dump ou restore de produção neste checkpoint documental. Não incluir valores de chaves, senhas, dumps ou dados pessoais no repositório.

## Sequência operacional futura

1. Conferir, sem imprimir `.env`, o release e a imagem implantados, saúde do serviço, host key e espaço livre. Interromper se a identidade da imagem/base ou o destino não puderem ser confirmados.
2. Executar `pg_dump` pela conta restrita e transmitir diretamente ao processo de criptografia no runner. Não gravar dump em claro no disco persistente; registrar somente horário, tamanho, checksum cifrado, versão do schema e resultado.
3. Publicar o artifact cifrado com a expiração aprovada. Verificar metadados, checksum, permissões de leitura e data de expiração sem baixar/exibir conteúdo.
4. Em janela separada, restaurar em ambiente efêmero sem conexão, porta, volume ou credencial de produção. Verificar checksum, Flyway, integridade e contagens agregadas; apagar os recursos temporários ao terminar.
5. Antes de liberar um banco restaurado, comparar o snapshot com o manifesto externo mais recente de encerramentos/expurgos e reaplicar os pedidos posteriores ao horário do backup. A fila V17 fica no mesmo banco: um backup feito antes de um encerramento não contém esse pedido. O checkout ainda não exporta esse manifesto para fora do PostgreSQL; até existir um canal durável e aprovado para guardá-lo, não reabrir um restore antigo como produção.
6. Para rollback, usar a imagem anterior identificada por seu digest/ID real e validar em clone isolado do schema. Não usar o script de deploy/rollback contra produção como ensaio.

### Conclusão SRE sobre o manifesto (24/10/2026)

O workflow de backup produz no máximo um snapshot manual do PostgreSQL; ele não exporta o manifesto e não recebe eventos de encerramento. Anexar o manifesto ao próximo artifact diário não é suficiente: um encerramento posterior ao último snapshot ainda poderia ser perdido no restore. O destino GitHub Actions escolhido continua válido como transporte/armazenamento temporário do dump cifrado, mas não como publicador síncrono do manifesto. Antes de habilitar encerramento/expurgo em produção, provisionar um canal externo autenticado e durável que grave cada tombstone append-only; o procedimento de restore deve obter o watermark mais recente, validar integridade e interromper a recuperação se o manifesto faltar ou estiver desatualizado. A VPS não tem mount remoto disponível e nenhum serviço adicional foi provisionado.

O procedimento detalhado e os comandos de referência estão em `DEVOPS-SRE-STATUS.md`, sob responsabilidade de DevOps. Esta conclusão é anterior à prova sintética descrita abaixo. O workflow manual de produção continua distinto e sem execução; nenhum backup ou restore real foi realizado.

## Decisão técnica e checkpoint operacional (24/10/2026)

Para avançar sem nova escolha de produto, o Lead seleciona como padrão de preparação os artifacts do GitHub Actions no repositório privado existente `pbosoares/Ink.Manager`: backup diário cifrado antes do upload, expiração em 30 dias, RPO máximo de 24 horas e RTO-alvo de 4 horas. O RTO é meta, ainda não medido. Não contratar serviço adicional. O artifact não substitui retenção legal nem cópia permanente.

Nenhum backup real foi executado. A auditoria autenticada confirmou repositório privado/admin, Actions habilitado, colaborador direto apenas `pbosoares` (`admin`), environment `production-backup` limitado a `main` sem reviewer obrigatório e retenção do repositório em 90 dias (máximo 400). O secret de deploy `VPS_SSH_KEY` continua separado, a variável pública age `MARCAE_BACKUP_AGE_RECIPIENT` existe e o secret SSH de backup `MARCAE_BACKUP_SSH_KEY` foi cadastrado no ambiente `production-backup`; nenhum valor foi consultado nesta auditoria. A cota de billing não foi acessível. Os 30 dias efetivos foram confirmados apenas no artifact sintético; confirmar capacidade e aprovar processamento de dados reais. Nenhum dado real foi copiado.

Rollback sintético LEAD-086 concluído: o JAR validado aplicou V19 e respondeu health 200/UP; em seguida, a imagem anterior `sha256:b15e0c328915364e7e501f5558d559fcb58618ce84c9a08178da026f95e70c58` subiu no mesmo PostgreSQL efêmero, também com health 200/UP, preservando Flyway e o marcador sintético. Sem volumes, dados reais ou acesso à VPS. Isso não valida ainda rollback Compose ou imagem Docker nova do pacote final.

O ensaio local sintético de 24/10 (RunID `5c3091a5a183`) comprovou dump cifrado/restauração para três registros fictícios, com comparação de checksum e contagens. Não comprova transferência off-host, chave portátil nem restore de produção.

## Preparação autorizada pelo titular — 24/10/2026

O titular confirmou a preparação do destino privado GitHub Actions Artifacts, com backup diário, retenção de 30 dias, RPO de até 24 horas e RTO-alvo de 4 horas. O RTO segue sem medição. Esta autorização cobre preparar uma identidade SSH restrita, uma chave age e um workflow **manual separado**; não autoriza copiar dados reais. A primeira execução real continua bloqueada até todos os controles abaixo estarem prontos.

### Reconciliação do acesso GitHub

- Fotografia anterior ao run `40139765467`: o checkpoint então registrou que nenhum artifact existia. Depois, a prova sintética criou o artifact `12576999832`; isso não representa backup de produção. As demais observações deste checkpoint sobre acesso/configuração refletem apenas o que foi conferido naquela data.
- A API atual confirmou que `production-backup` permite somente `main` e não retorna reviewer obrigatório. Essa política de deploy não equivale a proteção da branch. Consulta de branch protection/rulesets em `main` foi negada por HTTP 403; não se afirma que revisão de branch esteja ausente. A confirmação literal do workflow não substitui aprovação humana.
- `VPS_SSH_KEY` é exclusivamente para deploy. Não reutilizar essa chave para backup, dump, restore ou manifesto.

### Controles para preparar

1. **Concluído:** provisionada na VPS a conta não interativa `marcae-backup`, com senha bloqueada, chave pública dedicada, `authorized_keys` restrito e comando forçado. O wrapper/helper são root-owned; o helper fixa/revalida o container PostgreSQL e não lê nem executa Compose. `ssh -N -T` autenticou por chave sem abrir sessão e sem invocar wrapper/helper. Nenhum dump foi gerado.
2. Um par age 1.3.2 foi gerado fora do checkout em `%LOCALAPPDATA%\Marcae\backup-keys\marcae-backup-age-identity.txt`; a pasta tem ACL restrita ao titular, SYSTEM e Administradores. A chave privada não foi copiada ao GitHub ou à VPS. A derivação pública da identidade local correspondeu à variável `MARCAE_BACKUP_AGE_RECIPIENT`; um arquivo sintético foi cifrado/decifrado com hash SHA-256 idêntico. Antes da primeira cópia real, ainda é necessário guardar uma cópia segura da identidade privada para recuperação em outro dispositivo.
3. **Concluído:** chave SSH ed25519 dedicada gerada fora do checkout, fingerprint pública `SHA256:TQW0fM8ACAWj8mydz+sweF4EfcfSnLh9OpRWXvR3jhQ`, instalada na VPS e cadastrada como secret `MARCAE_BACKUP_SSH_KEY` em `production-backup`. O valor não foi exibido nem registrado em arquivo.
4. A API confirmou política customizada de `production-backup` somente para `main`, sem reviewer obrigatório. Branch protection/rulesets não puderam ser consultados (HTTP 403). A lista de colaboradores diretos contém apenas o proprietário; teams e deploy keys estão vazios. A cota de billing não foi acessível; confirmar capacidade e aprovação de dados antes do primeiro backup.
5. O arquivo local `.github/workflows/backup-manual.yml` declara `workflow_dispatch` com confirmação `COPIAR_BACKUP_PRODUCAO` e cron `17 06 UTC`, aceita `main`, usa `production-backup` e configura artifact age com metadados técnicos e retenção de 30 dias. A API de workflows do repositório lista em `main` somente `ci.yml` e `backup-synthetic-proof.yml`; o workflow manual não está publicado/ativo nessa branch. Ele não foi executado. A prova que passou foi outro arquivo, sem secrets e com dados sintéticos, na branch `ops/backup-proof`.
6. **Ainda pendente:** antes do primeiro backup/restore real, aprovar processamento de dados e confirmar cota/capacidade (não observada pela API), publicar/ativar o workflow em `main` por rota segura, preparar recuperação da chave fora do perfil atual, provisionar manifesto externo e aprovar janela. Registrar checksum cifrado, schema, expiração e evidência de recuperação, sem conteúdo pessoal em logs. O artifact sintético não fecha esses gates.

### Estado do rollback isolado

O ensaio sintético LEAD-086 foi concluído com PostgreSQL em `tmpfs` e rede interna isolada: imagem atual/JAR validado aplicou V19 e teve health 200/UP; a imagem anterior também ficou saudável contra o mesmo schema V19, preservando Flyway e o marcador sintético. Containers foram parados; dados fictícios desapareceram com o tmpfs. Sem Compose, banco real, VPS, backup ou restore off-host. Detalhes e digests constam em `DEVOPS-SRE-STATUS.md`.

## Atualização — identidade de backup provisionada (24/10/2026)

- O SRE provisionou e validou a conta restrita `marcae-backup`, o wrapper/helper root-owned e a chave pública dedicada. A autenticação por chave com `ssh -N -T` passou; wrapper/helper não foram invocados. A senha da conta está bloqueada. O Compose inseguro `root:root 0666` permaneceu inalterado; o helper usa um container PostgreSQL fixo e validado, sem ler Compose ou `.env`.
- Fotografia anterior ao run `40139765467`: nenhum workflow havia sido executado naquele checkpoint. A atualização sintética abaixo supersede a afirmação quanto ao workflow sintético apenas; o workflow manual de produção continua sem execução.
- A identidade age local deriva exatamente o destinatário público `MARCAE_BACKUP_AGE_RECIPIENT`; um roundtrip sintético cifra/decifra passou com SHA-256 idêntico. A chave privada permanece fora do GitHub e da VPS; ainda falta uma cópia segura de recuperação em outro dispositivo.
- A workflow `.github/workflows/backup-manual.yml` permanece local/não publicada e sem execução. O arquivo declara cron diário, mas não consta na lista de workflows de `main`; portanto, não há schedule de produção comprovado como ativo. Não há backup/artifact real; o único artifact listado é sintético e o objetivo de RPO 24h ainda não está operacional.
- Permanecem: cópia segura de recuperação da identidade age privada em outro dispositivo; confirmação de cota/capacidade e aprovação de dados reais (a API só mostrou o proprietário na lista direta); publicação/ativação segura do workflow de produção; manifesto externo durável; teste de dump cifrado de produção off-host e restore isolado em janela autorizada; RTO medido; validação do pacote final e do fluxo de rollback Compose/CD, se exigido pelo release. O `passwordauthentication yes` global do sshd continua como configuração preexistente; senha da conta `marcae-backup` segue bloqueada, e o SSHD não foi alterado/recarregado.

### Atualização local do workflow — 24/10/2026

Sem acessar a VPS, o workflow manual de produção foi ajustado para acompanhar um artifact cifrado com `metadata.json` sem dados pessoais. O arquivo local declara dispatch com confirmação literal `COPIAR_BACKUP_PRODUCAO` e cron diário, mas a API de workflows de `main` não o lista; não foi disparado e nenhum backup real foi copiado. A validação sintética separada do GitHub Actions foi executada depois, conforme o checkpoint abaixo; ela não valida esse workflow manual.

### Atualização técnica do manifesto — 24/10/2026

O checkout agora integra o backend GitHub privado com atualização condicional pelo SHA, cadeia HMAC, genesis obrigatório, verificação após gravação e gate de tráfego até reconciliar o banco restaurado. O bootstrap é manual, protegido por operador e desligado por padrão. Código e procedimento estão em `MARCAE-MANIFESTO-EXTERNO.md`.

O repositório privado `pbosoares/marcae-closure-manifest` já existe com branch `main`; a autenticação GitHub foi restabelecida. A proteção de branch foi recusada no plano atual (403: exige GitHub Pro ou repositório público), e nenhuma VPS foi alterada. Ainda faltam token fine-grained, genesis e configuração segura do app. O protocolo e os limites estão em `MARCAE-MANIFESTO-EXTERNO.md`. Sem backend configurado, a solução não libera retenção em produção.

Checkpoint posterior: o backend também usa `manifest-anchor` para detectar rollback ou truncamento apenas de `main`, exigindo avanço fast-forward sem force e extensão byte a byte do ledger. A verificação agendada fecha o gate se a âncora/regra falhar. Isso reduz restauração acidental de um branch, mas não oferece imutabilidade contra o administrador/credencial que possa reescrever ambos; a restrição do plano GitHub continua. A configuração ainda exige a branch âncora, token fine-grained e genesis na VPS. Sem testes de integração HTTP GitHub fake, sem token e sem provisionamento/deploy.

## Prova sintética GitHub Actions — artifact e restore (24/10/2026)

O workflow `.github/workflows/backup-synthetic-proof.yml` foi publicado somente na branch `ops/backup-proof`, baseada em `main` `08a6c1d3f96219d8392d870708d665022c7aeb70`. O commit `9825a7ad2870b3aebe64d0b881a0776efc02ffbf` contém apenas esse workflow. A execução por push `40139765467` concluiu com sucesso em 49 s.

O job usou PostgreSQL efêmero e dados fictícios, fez `pg_dump` custom, cifrou com age, publicou e baixou o mesmo artifact `12576999832`, e restaurou em `synthetic_restore`. SHA-256/tamanho do ciphertext e marcador pós-restore foram conferidos; SHA-256 do marcador: `26b415befa6cfd4a4a8e9acc04af107bee0f53c71c51ec89ae2ccb1da4e9f55e`. A API confirmou `expired=false`, 2.663 bytes, criação `2026-10-24T22:44:09Z` e expiração `2026-11-23T22:44:09Z` (30 dias efetivos). A chave age sintética e os arquivos temporários foram removidos no runner; os containers do job pararam.

**Esta prova não é backup off-host de produção.** Não houve dump de produção, restore de produção, acesso à VPS ou alteração de `main`. A workflow manual `.github/workflows/backup-manual.yml` não foi publicada/executada neste push e continua sem prova operacional.

### Gates restantes

- Aprovar processamento de dados reais e confirmar cota/capacidade de Actions pela cobrança da conta; a API não a revelou. O acesso direto observado lista apenas o proprietário; confirmar a decisão de acesso e publicar o workflow de produção em `main` por caminho seguro. O artifact/retention de 30 dias comprovados pertencem somente à prova sintética.
- Preparar recuperação da chave age fora do perfil local, fazer restore de um backup real em ambiente isolado, validar schema/integridade e medir o RTO.
- Provisionar o manifesto externo durável; exigir reconciliação do snapshot com encerramentos posteriores antes de liberar tráfego restaurado.
- O fluxo de produção ainda não é diário (RPO de 24 h não operacional); rollback Compose/CD também permanece sem ensaio validado.

## Auditoria read-only de metadados GitHub — 24/10/2026

Consultas autenticadas somente GET e leitura local do workflow; nenhum valor de secrets/vars foi requisitado, nenhum workflow foi iniciado e não houve SSH/VPS ou alteração remota.

- **Acesso:** `pbosoares/Ink.Manager` é privado e de conta pessoal; o usuário autenticado tem `ADMIN`. A API lista apenas `pbosoares` (`admin`) como colaborador; teams e deploy keys retornam vazios. `production-backup` permite somente `main` por política customizada e não tem reviewer obrigatório listado. A API não fornece roster separado de usuários do environment. Branch protection/rulesets de `main` retornaram HTTP 403 (upgrade ou tornar público), então esse estado permanece desconhecido.
- **Actions:** habilitado; `allowed_actions=all`, `sha_pinning_required=false`; permissão default do `GITHUB_TOKEN` é `read`. A API de workflows de `main` lista `ci.yml` e `backup-synthetic-proof.yml`, mas não `backup-manual.yml`. O cron `17 06 UTC` está no commit local `13ebe04`, ainda não enviado; não comprova schedule ativo. Upload-artifact está fixado no mesmo SHA usado pela prova sintética.
- **Uso observado:** inventário do repositório tem um artifact (sintético `12576999832`, 2.663 bytes, não expirado) e cache Actions ativo de 69.176.733 bytes (~65,97 MiB, uma entrada). Isso não é relatório de cobrança. APIs de billing retornaram 404/escopo insuficiente e `/user` retornou `plan=null`; cota incluída, minutos e saldo não foram confirmados nem estimados.
- **Retenção:** configuração atual do repositório é 90 dias, máximo 400. O upload sintético pediu 30 dias e a API confirma expiração em `2026-11-23T22:44:09Z`, criado em `2026-10-24T22:44:09Z`. A documentação GitHub publica 10 GB como limite de cache por repositório; é referência de plataforma, não cota medida nem saldo desta conta. Fontes: [Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions), [API de retenção](https://docs.github.com/en/rest/actions/permissions#get-artifact-and-log-retention-settings-for-a-repository).

Não houve backup/restore real nem execução da workflow manual de produção. Permanecem os gates: aprovação/capacidade para dados reais, publicação/validação do workflow de produção, recuperação da chave, restore real isolado, manifesto externo durável, RPO/RTO e rollback Compose/CD se requerido.

## Complemento read-only — plano, Packages e artifacts vigentes (24/10/2026)

Consultas GET autenticadas à API GitHub; sem leitura de valores de token/secret, sem executar workflow, sem VPS e sem mutação.

- `/users/pbosoares` retornou `plan.name=null`; o plano da conta não foi determinado.
- A listagem de packages `container` retornou HTTP 403 por falta do escopo `read:packages`. Não foi possível determinar packages publicados ou uso de storage; não interpretar como inventário vazio e não estimar cobrança.
- O inventário do repositório retornou `total_count=1`: artifact sintético `marcae-synthetic-backup-proof-40139765467` (`12576999832`), 2.663 bytes, `expired=false`, criado em `2026-10-24T22:44:09Z` e expirando em `2026-11-23T22:44:09Z`. Não há artifact de backup real observado.

## Ajuste local do workflow manual — restore antes do artifact (24/10/2026)

Este complemento atualiza a descrição do arquivo local; os checkpoints anteriores permanecem como registros do estado observado à época.

O arquivo local `.github/workflows/backup-manual.yml` foi preparado para só publicar o artifact depois de descriptografar e restaurar o stream com sucesso em um PostgreSQL efêmero. O job manual continua limitado a `main` e exige a confirmação literal `COPIAR_BACKUP_PRODUCAO`. O cron diário (`06:17 UTC`) só inicia o job se a variável de repositório `MARCAE_BACKUP_SCHEDULE_ENABLED` for exatamente `true`; variável ausente ou qualquer outro valor deixa a agenda inativa. O valor atual não foi consultado nem alterado; manter desabilitada até concluir uma primeira execução controlada.

A etapa de captura cifra o fluxo age para o destinatário de longo prazo e para uma identidade aleatória criada no runner. A etapa de restore não recebe credenciais SSH nem destinatário de produção. Ela inicia PostgreSQL 18.4 fixado por digest, sem rede (`--network none`) ou portas publicadas, com credenciais sintéticas, e envia a descriptografia diretamente a `pg_restore --exit-on-error`, sem arquivo de dump em claro. Após o restore, uma checagem interna exige tabelas de usuário e calcula contagens agregadas exatas. Esses números são usados somente na validação em memória: não entram em `metadata.json`, `GITHUB_OUTPUT`, artifact ou log. O metadata registra apenas `restore_validation.status=passed`. O artifact só é enviado após esse status ser gravado; a identidade age e os arquivos temporários são removidos e o container/volume efêmero é limpo no fim do job.

O restore não compara as contagens com uma leitura do banco de origem; é um smoke check de descriptografia, restauração e schema, não uma reconciliação linha a linha. O isolamento se aplica ao container PostgreSQL e à etapa de restore; a etapa anterior de captura ainda usa SSH para obter o backup de produção quando o workflow for autorizado e executado.

Este ajuste é apenas local: não houve execução do workflow, cópia de dados, commit ou push nesta tarefa; nenhuma variável ou secret foi consultada ou alterada. A prova sintética anterior continua sendo a única execução comprovada. Permanecem pendentes a revisão e publicação operacional, a primeira execução controlada, confirmação de capacidade/custo, recuperação da chave age fora do perfil local e a validação de backup/restore real. Não habilitar o cron antes de fechar esses gates.

## Backup real controlado e restore efêmero (24/10/2026)

Este checkpoint atualiza os estados dos complementos anteriores, que registravam corretamente que ainda não havia backup/restore real. A única execução controlada foi solicitada manualmente em `main`, com o input literal `COPIAR_BACKUP_PRODUCAO`; não houve execução do cron, alteração de `MARCAE_BACKUP_SCHEDULE_ENABLED`, mudança de secrets/variables, deploy ou alteração direta na VPS.

- **Run:** GitHub Actions `40143560282`, evento `workflow_dispatch`, commit `334490c26a23ee59991ed4cfab289d38f62d6a9d`, conclusão `success`.
- **Fluxo:** credenciais preparadas; captura do stream cifrado; descriptografia e restore em PostgreSQL efêmero `postgres:18.4` com `--network none`; verificação agregada de schema; upload somente depois do restore; conferência da expiração; limpeza. Todos os steps concluíram com sucesso. O log técnico confirmou `restore_validation.status=passed`.
- **Artifact off-host:** `marcae-db-40143560282`, ID `12579597315`; ciphertext de `61.158` bytes; SHA-256 `6d388b29f2b516ef91df7304b2597a0896db329d44b2430f3b0d1495dd6bb5ee`. A API informou artifact compactado de `61.889` bytes, `expired=false`, criado em `2026-10-24T23:39:18Z` e expirando em `2026-11-23T23:39:16Z` (29 dias, 23:59:58 pela precisão de segundos da API; dentro da tolerância de cinco minutos validada pelo workflow).
- **Privacidade/escopo:** o artifact foi cifrado para o destinatário age de longo prazo e para uma identidade temporária do runner. O restore foi feito a partir do ciphertext no runner; a identidade temporária e os arquivos temporários foram removidos. A inspeção posterior consultou somente metadados e logs técnicos; o artifact não foi baixado ou aberto. Este registro não inclui dados, contagens de produção, credenciais nem valores de secrets/variables.
- **Limites restantes:** o backup real cifrado e seu restore efêmero estão comprovados, mas a recuperação da chave privada age de longo prazo e a descriptografia futura com essa chave ainda precisam de procedimento verificado. O cron continua inativo: a variável de habilitação estava ausente no repositório e no environment no precheck e não foi alterada. Monitoramento/alerta da rotina e manifesto síncrono durável para reconciliar encerramentos após restore continuam gates separados; este artifact de 30 dias não substitui o manifesto.
