# Pendências para produção — Marcaê

Atualizado em 21/10/2026. A implementação incremental foi validada localmente e enviada para `main` no commit `8a9d36d`. O registro desta checklist foi enviado no commit `8a658c3`. Nenhuma publicação em produção, cobrança ou entrega de email real foi feita.

## Antes de implantar

- [x] Confirmar que o workflow de CI do commit `8a9d36d` terminou verde: [run 39522546413](https://github.com/pbosoares/Ink.Manager/actions/runs/39522546413). O commit documental `8a658c3` também passou: [run 39523164011](https://github.com/pbosoares/Ink.Manager/actions/runs/39523164011).
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

## Atualizacao da revisao local - 21/10/2026

- H2 / Java 25: 65 testes, 0 falhas, 0 erros e 1 ignorado nesta revisao local; log `target/validacao-final-h2.log`.
- PostgreSQL e E2E visual da lista/oferta ainda nao foram repetidos nesta revisao: os servicos locais dedicados (PostgreSQL 55439, SMTP fake 1025 e Mailpit API 8025) nao estavam ativos. Os resultados anteriores acima correspondem ao commit `8a9d36d`, nao a este diff.
- Decisao comercial atualizada pelo usuario: o checkout futuro usara Stripe. O codigo existente ainda usa Mercado Pago; a troca para Stripe permanece pendente e nao faz parte deste envio.
- Nenhuma cobranca, email externo ou implantacao foi feita.
