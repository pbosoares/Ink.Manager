# Plano de lancamento SaaS

O assinante acessa a plataforma pelo navegador, cadastra seu proprio estudio,
personaliza o nome e administra os acessos da equipe. Uma unica operacao hospeda
as empresas com dados isolados; nao ha instalacao por cliente.

## Oferta inicial

Equipe com agenda por profissional, expediente, intervalos, folgas, clientes
recorrentes, consulta de disponibilidade e administracao de acessos. Recuperacao
de senha depende do e-mail da plataforma. Pagamento por cartao recorrente ou Pix
mensal no provedor, com liberacao apos confirmacao.

O preco e configurado por BILLING_MONTHLY_AMOUNT e exibido antes do checkout.
O valor inicial da implementacao e R$ 19,90, com avaliacao de 14 dias configuravel.
Valide preco e margem com custos de hospedagem, taxas e suporte antes de publicar.
A proposta de piloto comercial deve informar prazo, valor posterior e atendimento.

## Antes da primeira assinatura real

1. Escolher hospedagem gerenciada para backend e PostgreSQL e publicar com HTTPS.
2. Configurar URL publica, remetente de e-mail e conta recebedora.
3. Homologar fluxo completo com contas de teste: adesao, renovacao, Pix, cancelamento, atraso e estorno.
4. Ensaiar isolamento de empresas, troca de senha, revogacao de acesso e restauracao de backup.
5. Definir suporte, termos e tratamento de dados e entao iniciar um piloto assistido.

Os assinantes nao executam scripts, Docker ou comandos de banco. A operacao da
plataforma e centralizada. Docker e ampliacao da CI ficam para a etapa posterior.
