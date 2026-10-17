# Validacao do MVP SaaS - 17/10/2026

- PostgreSQL 18.4: 21 testes aprovados, sem falhas ou testes ignorados.
- H2: 20 testes aprovados; teste exclusivo de migracao PostgreSQL ignorado por condicao.
- Chromium: fluxo completo aprovado com cadastro autonomo, personalizacao do estudio, criacao de funcionario e profissional, cliente recorrente, reservas, edicao, recarregamento e novo login.
- Duas contas no navegador: funcionario consulta e reserva, responsavel recebe a agenda atualizada e desativa o acesso; sessao do funcionario e rejeitada.
- Menu e largura mobile em 390px aprovados.
- Isolamento entre estudios, permissao por perfil, disponibilidade e conflito de reservas testados.
- Recuperacao de senha testada com e-mail simulado: token unico e revogacao de sessoes.
- Cobranca a R$ 19,90: assinatura HMAC obrigatoria, confirmacao no provedor, pagamento unico sem credito duplicado, estorno, evento antigo apos estorno, valor incorreto e cancelamento preservando periodo pago.
- Migracoes de reconciliacao e ordenacao de eventos aplicadas no PostgreSQL isolado.
- JavaScript verificado com node --check.

Logs e capturas locais ficam em target, fora do Git. Bancos de validacao sao
isolados dos dados do estudio. A CI original foi preservada; Docker e ampliacao
da CI ficam para depois conforme solicitado.

## Limitacoes atuais

- Hospedagem, dominio/HTTPS, SMTP e conta Mercado Pago de producao ainda precisam ser configurados pelo operador. O assinante nao instala nada.
- Pagamentos foram testados com adaptador simulado. Ainda e necessaria homologacao real no ambiente de teste do Mercado Pago, incluindo renovacao automatica e notificacoes publicas. Nenhuma cobranca real foi feita.
- Pix e pagamento mensal manual, nao Pix Automatico.
- Agenda compartilhada atualiza a cada 20 segundos nas telas de visao geral e agenda, ou ao recarregar. A API valida conflitos ao salvar.
- Sem SMTP configurado, recuperacao de senha permanece indisponivel.
- A conciliacao consulta lotes e ate 20 pagamentos recentes por checkout; nao substitui conciliacao financeira contabil. Webhooks devem permanecer habilitados, inclusive para estornos antigos.
- Limite de requisicoes de autenticacao fica na memoria da instancia. Operacao com varias instancias exige rate limit compartilhado.
- Sem automacao de restauracao periodica de backup, monitoramento externo ou operacao de producao provisionada neste repositorio.

Antes da primeira venda: publicar com HTTPS, configurar integracoes, homologar
pagamentos e restauracao de backup e definir termos, privacidade e atendimento.
