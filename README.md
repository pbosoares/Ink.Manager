# Marcae (checkout Ink Manager)

SaaS simples para organizar servicos, equipe, clientes e agendas. O responsavel
cria seu estabelecimento pelo navegador, informa o nome e compartilha um link
publico para clientes reservarem sem conta.

- Catalogo com nome, preco e duracao; link fixo para reservas publicas.
- Confirmacao de atendimento e valor recebido; totais de semana e mes pela data da confirmacao.
- Dados isolados por estudio, inclusive nos vinculos do banco.
- Responsavel com controle de configuracao e acessos; funcionarios podem consultar e agendar.
- Expedientes semanais, intervalos, folgas e consulta de dias e horarios livres.
- Clientes recorrentes, sessoes versionadas e protecao contra reservas simultaneas.
- Recuperacao de senha por e-mail e revogacao imediata de sessoes.

Aplicacao em [ink-manager-api](ink-manager-api/README.md).
O assinante nao instala Java, banco ou Docker: a plataforma roda em producao numa
VPS (Docker Compose + Caddy), com deploy automatico pelo GitHub Actions.
Mensalidade inicial de R$ 29,90 por estudio, com 14 dias de avaliacao sem cartao no inicio.
Assinatura por cartao recorrente ou Pix mensal via Stripe, com confirmacao pelo provedor.
Pagamentos reais dependem da configuracao e homologacao da conta Stripe.

Os Java da raiz, `lib/` e `clientes.txt` sao o CLI historico, fora do build atual.

A marca visual e Marcae; pastas, endpoints existentes e identificadores tecnicos
do Ink Manager permanecem para preservar compatibilidade. Veja o
[indice da documentacao](ink-manager-api/docs/README.md).
