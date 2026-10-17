# Liberacao do SaaS

O produto e acessado pelo navegador. Uma unica plataforma hospeda varios estudios,
com isolamento e contas proprias. O cliente nao instala nenhum componente.

Antes da venda publica:

- [ ] Publicar a revisao validada em hospedagem com PostgreSQL e HTTPS.
- [ ] Definir dominio/URL publica e configurar remetente SMTP.
- [ ] Configurar conta recebedora, credenciais de pagamento e webhook.
- [ ] Homologar assinatura, renovacao, inadimplencia, cancelamento e estorno.
- [ ] Ensaiar dois estudios reais sem qualquer acesso cruzado.
- [ ] Validar recepcao agendando para profissionais diferentes.
- [ ] Configurar backup externo, retencao, monitoramento e restauracao.
- [ ] Formalizar suporte, termos e tratamento dos dados.

A hospedagem, o dominio e as contas externas ainda nao foram provisionados. Docker
e CI foram adiados por decisao do projeto. Esses itens nao sao instalacoes exigidas
do assinante. As validacoes locais anteriores a arquitetura SaaS nao substituem a
nova regressao de isolamento, acesso e cobranca.
