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

Agende pg_dump em formato custom para armazenamento externo protegido e teste
pg_restore em outro banco. A retencao e os compromissos de recuperacao precisam
ser acordados. Monitore saude, erros de e-mail e processamento de pagamentos.

A administracao de acessos e feita pelo responsavel no painel: desativar usuario
invalida as sessoes imediatamente. Sair encerra as sessoes daquela conta; redefinir
senha tambem. O limite de autenticacao e por endereco observado e por instancia;
quando houver varias instancias, aplique limite compartilhado na infraestrutura.

Nao ha endereco publico neste ambiente. Docker/CI e configuracao da hospedagem
ficam para a etapa posterior solicitada pelo projeto.
