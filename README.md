# Ink Manager

SaaS para estudios de tatuagem: o cliente cria sua conta no navegador, informa o
nome do estudio e administra equipe, clientes e agendas por profissional.

- Dados isolados por estudio, inclusive nos vinculos do banco.
- Responsavel com controle de configuracao e acessos; funcionarios podem consultar e agendar.
- Expedientes semanais, intervalos, folgas e consulta de dias e horarios livres.
- Clientes recorrentes, sessoes versionadas e protecao contra reservas simultaneas.
- Recuperacao de senha por e-mail e revogacao imediata de sessoes.

Aplicacao em [ink-manager-api](ink-manager-api/README.md).
O assinante nao instala Java, banco ou Docker. A plataforma precisa ser publicada
online pelo operador; hospedagem e dominio ainda nao foram provisionados.
A integracao de assinatura por cartao/Pix esta em desenvolvimento separado.

Os Java da raiz, `lib/` e `clientes.txt` sao o CLI historico, fora do build atual.
