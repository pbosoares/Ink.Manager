# GitHub Actions: CI e CD

O workflow `.github/workflows/ci.yml` executa `./mvnw clean test` em pushes e pull requests para `main`. Um push em `main` ou uma execucao manual em `main` publica a release somente depois do sucesso dos testes.

## Segredo requerido

Cadastre `VPS_SSH_KEY` como secret do repositorio, contendo a chave privada dedicada ao deploy da VPS. A chave de host ED25519 da VPS esta fixada no workflow; a action exige `StrictHostKeyChecking=yes`. Nao cadastre senha do root nem credenciais do Resend no GitHub Actions.

## Deploy e rollback

A action envia um arquivo do commit e `deploy-release.sh` para `/opt/marcae`. Cada release fica em `/opt/marcae/releases/<commit>`. O deploy usa o mesmo projeto Compose `marcae`, o `.env` privado ja instalado e os volumes existentes; nao recria nem apaga o banco. Depois de subir API e proxy, exige `https://<INK_DOMAIN>/actuator/health` com status `UP`. Se o deploy ou a verificacao falhar, tenta voltar API e proxy a imagem indicada no `.env` anterior. Releases anteriores sao preservadas para rollback.

O segredo `VPS_SSH_KEY` precisa permanecer cadastrado no GitHub para a action publicar. A configuracao atual do Resend permanece no servidor e `MAIL_ENABLED` deve continuar desligado ate validar a credencial e autorizar envios reais.
