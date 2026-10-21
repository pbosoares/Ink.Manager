# Pendências para produção — Marcaê

Atualizado em 20/10/2026. A implementação incremental foi validada localmente e enviada para `main` no commit `8a9d36d`. Nenhuma publicação em produção, cobrança ou entrega de email real foi feita.

## Antes de implantar

- [ ] Confirmar que o workflow de CI do commit `8a9d36d` terminou verde.
- [ ] Preparar a VPS com Java 25, processo/serviço da aplicação, logs e reinício seguro.
- [ ] Provisionar PostgreSQL de produção e aplicar as migrações; configurar backups e testar restauração.
- [ ] Configurar domínio, DNS, proxy reverso e HTTPS.
- [ ] Guardar fora do repositório os segredos de produção: `JWT_SECRET`, credenciais do banco, assinatura/pagamentos e email.
- [ ] Configurar o provedor de email escolhido e validar remetente/domínio. Publicar SPF, DKIM e DMARC conforme os registros fornecidos pelo provedor. Manter envio desligado até validar a configuração.
- [ ] Conferir em sandbox o fluxo de assinatura de R$ 29,90/mês, teste de 14 dias sem cartão e processamento idempotente dos webhooks.
- [ ] Finalizar revisão dos termos, aviso de privacidade, retenção e tratamento de dados pessoais do agendamento e da lista de espera.

## Depois do deploy

- [ ] Fazer smoke test no domínio: cadastro/login, configuração de serviços e expediente, agendamento público, cancelamento, oferta da lista de espera, confirmação e recebimentos.
- [ ] Confirmar isolamento entre estabelecimentos e que emails reais chegam somente ao destinatário e contexto corretos, primeiro com contas controladas.
- [ ] Verificar saúde, logs, alertas, backups e procedimento de rollback.

## Limites da validação atual

- H2: 61 testes, 0 falhas, 0 erros, 1 ignorado.
- PostgreSQL 18.4 em schema isolado: 61 testes, 0 falhas, 0 erros, 0 ignorados.
- Playwright Chromium: 2 cenários aprovados (desktop e mobile); sem jornada visual E2E específica da lista de espera.
- `MAIL_ENABLED=false`; nenhum email real foi enviado e o provedor/domínio remetente não foram configurados.
- Não houve cobrança real nem implantação em VPS.
