# Marcae - SaaS (Ink Manager)

Uma unica aplicacao atende varios estudios, com dados separados e acesso pelo
navegador. Cada responsavel cria o proprio estudio e personaliza seu nome. Nao ha
instalacao no computador do assinante.

## Fluxo do assinante

1. Criar conta com nome, e-mail, senha e nome do estudio.
2. Em Meu estudio, ajustar o nome e criar as contas dos funcionarios.
3. Em Equipe e expedientes, cadastrar profissionais, turnos e folgas.
4. Na Agenda, escolher profissional, mes e duracao; selecionar dia e horario livre.
5. Reutilizar um cliente ou cadastrar outro ao reservar.

O expediente inicial da Agenda principal e segunda a sabado, 09h-18h; o responsavel
deve ajusta-lo a rotina real. Profissionais sao recursos de agenda; contas de acesso
sao administradas separadamente, permitindo que a recepcao agende para a equipe.
Todos da mesma equipe consultam a agenda. A tela atualiza as sessoes a cada 20
segundos quando visivel e sem editor aberto. A confirmacao final ocorre no servidor.

## Permissoes e seguranca

- Responsavel: nome do estudio, acessos, profissionais, expedientes, folgas e exclusoes.
- Funcionario: consulta, cadastro de clientes e criacao/edicao de agendamentos.
- Uma conta de e-mail pertence a um estudio; o mesmo e-mail nao cria contas duplicadas.
- Cada requisicao valida usuario ativo e versao da sessao. Desativar acesso, redefinir senha ou sair revoga os tokens anteriores.
- O responsavel nao pode desativar a propria conta. Contas nao sao apagadas junto com o historico.
- Endpoints nunca aceitam o estudio por parametro para decidir o acesso: usam a sessao autenticada.
- Reservas de profissionais diferentes podem ocorrer ao mesmo tempo. O mesmo profissional nao aceita sobreposicao.
- Turnos podem ser divididos para intervalo. Folgas nao podem sobrepor sessoes existentes; reagende ou cancele antes.
- A consulta sugere inicios em passos de 15 minutos que comportem toda a duracao. O expediente e os bloqueios sao revalidados ao salvar.
- AGENDADA e CONFIRMADA ocupam agenda; CANCELADA e CONCLUIDA liberam o horario. Status encerrados nao podem ser reabertos.
- Edicoes exigem a version da ultima leitura. Entradas invalidas retornam 400; conflitos de agenda/edicao retornam 409.

## Recuperacao de senha

O formulario envia um link de 30 minutos e uso unico. Somente o hash do token fica
no banco; o token do link fica no fragmento da URL para evitar logs de requisicao.
A resposta nao confirma se um e-mail existe. O envio exige SMTP da plataforma.
Sem SMTP configurado, a funcao informa indisponibilidade e o restante do sistema
continua funcionando. Nao ha envio simulado nem exposicao de tokens na API.

## Desenvolvimento e hospedagem

Java 25 e PostgreSQL 18, ou H2 nos testes. Configure DB_URL, DB_USERNAME,
DB_PASSWORD e JWT_SECRET (aleatorio, no minimo 32 bytes). Execute
`.\mvnw.cmd spring-boot:run`. O cadastro de estudios e publico; funcionarios sao
criados apenas pelo responsavel autenticado. STUDIO_REGISTRATION_CODE foi removido.

Para e-mail, configure PUBLIC_URL, MAIL_HOST, MAIL_PORT, MAIL_USERNAME,
MAIL_PASSWORD e MAIL_FROM no ambiente da plataforma. PUBLIC_URL deve ser a URL
HTTPS publicada. Nunca use o host enviado pelo navegador para montar links.
MAIL_HEALTH_ENABLED=true habilita verificacao SMTP no endpoint de saude, se desejado.

A infraestrutura e operada uma unica vez pelo dono da plataforma. Os arquivos de
Docker/CI existentes ficam disponiveis para uma etapa posterior; nao sao passos
para o assinante. Ainda nao existe endereco publico configurado.

## Contratos principais

- POST /auth/estudios: cria estudio e responsavel atomicamente.
- POST /auth/login; GET /auth/me; POST /auth/logout.
- POST /auth/recuperar-senha; POST /auth/redefinir-senha.
- GET/PUT /estudio: nome do estudio autenticado.
- GET/POST /equipe; PUT /equipe/{id}; POST /equipe/{id}/revogar-sessoes.
- /clientes e /marcacoes: CRUD do estudo autenticado.
- /profissionais: cadastro e expediente semanal.
- GET /profissionais/{id}/dias-disponiveis?inicio=AAAA-MM-DD&dias=31&duracaoMinutos=60.
- GET /profissionais/{id}/disponibilidade?data=AAAA-MM-DD&duracaoMinutos=60.
- GET/POST /profissionais/{id}/bloqueios; DELETE /profissionais/{id}/bloqueios/{id}.

Marcacoes exigem profissionalId, cliente, data, horario, status e duracaoMinutos.
POST /marcacoes/com-cliente cadastra cliente e sessao atomicamente. A edicao desse
endpoint preserva o cliente vinculado; altere contatos na tela Clientes.
As migracoes preservam dados anteriores em um estudio legado e na Agenda do estudio.
Sessoes JWT emitidas antes da mudanca para SaaS deixam de ser validas.

## Testes

`.\mvnw.cmd verify` usa H2. Para PostgreSQL, configure INK_TEST_DB_URL,
INK_TEST_DB_DRIVER=org.postgresql.Driver, INK_TEST_DB_USER e INK_TEST_DB_PASSWORD.
Use sempre banco exclusivo: os testes removem os dados de negocio.

Para navegador: gere o JAR, execute `npm ci`, instale o Chromium de teste com
`npx playwright install chromium`, configure outro banco exclusivo em DB_URL,
DB_USERNAME e DB_PASSWORD e execute `npm run test:e2e`. Isso e tooling de
validacao do desenvolvedor, nao instalacao para clientes.

## Assinatura por cartao ou Pix

O responsavel acessa Meu estudio para consultar o periodo de avaliacao, valor,
periodo pago e formas de pagamento. Cartao usa assinatura recorrente no checkout
Mercado Pago. Pix usa checkout hospedado para pagar uma mensalidade de cada vez;
nao e Pix Automatico. Dados de cartao ficam no provedor.

A configuracao inicial e 14 dias de avaliacao sem cartao no inicio e R$ 29,90/mes, alteraveis pelo operador
em BILLING_TRIAL_DAYS e BILLING_MONTHLY_AMOUNT antes de disponibilizar a oferta.
O backend determina o valor e valida a moeda/quantia confirmadas pelo provedor.
A configuracao de preco e comercial e precisa ser validada antes da primeira venda.

Ao vencer a avaliacao e o periodo pago, novas gravacoes de clientes/agenda ficam
bloqueadas com 402. Os dados continuam consultaveis e o responsavel pode renovar.
BILLING_ENFORCE=true e o padrao; desabilitar e destinado somente a desenvolvimento.
Cancelar recorrencia nao remove o credito ja pago. Estornos e chargebacks recebidos
pelo webhook retiram o credito correspondente; o historico de negocio e preservado.

Para habilitar pagamentos, o operador configura uma unica conta recebedora:

- PUBLIC_URL: URL HTTPS publica da plataforma.
- MP_ACCESS_TOKEN: credencial da conta recebedora.
- MP_WEBHOOK_SECRET: segredo de assinatura das notificacoes.
- Webhook publico: POST /assinatura/webhook, com eventos payment, subscription_preapproval e subscription_authorized_payment.

A plataforma verifica HMAC, consulta o recurso diretamente na API do Mercado Pago
e aplica o resultado de forma idempotente. Retornar do checkout nao ativa acesso.
Cada pagamento tem ID unico e vinculo ao estudio correto. Uma intencao de checkout
fica salva antes da chamada externa para repetir com a mesma chave de idempotencia.

A conciliacao automatica executa em lotes a cada 10 minutos, alem do webhook. O
responsavel pode usar Ja paguei para consultar o provedor. Sem as credenciais ou
URL HTTPS, os botoes de pagamento ficam desabilitados e a API retorna indisponibilidade.
Nenhuma integracao simulada e oferecida como pagamento real.

A ativacao comercial ainda exige homologacao com contas de teste do provedor,
seguida de configuracao de producao. Hospedagem, dominio e contas externas nao
foram provisionados neste repositorio.

## Marcae simples: servicos, link publico e recebimentos

O layout e os recursos anteriores de equipe, agenda, clientes e assinatura foram
preservados. Em Meu negocio, o responsavel cadastra nome, preco e duracao dos
servicos, configura os profissionais e copia o link fixo. Arquivar um servico o
retira das novas reservas sem apagar seu historico. Renomear o negocio nao muda o link.

O cliente abre /agendar.html?local=LINK, escolhe servico, profissional, dia e
horario, informa nome/telefone e confirma sem criar conta. O servidor usa preco e
duracao do catalogo e grava snapshots. A resposta perdida pode ser repetida com a
mesma chave sem duplicar cliente/reserva. O horario so e garantido ao confirmar;
conflitos exigem consultar os horarios novamente. Nenhum pagamento e feito nessa pagina.

O proprietario confirma atendimento e valor efetivamente recebido a partir do
horario agendado, em uma unica acao na agenda. Agendamento cancelado ou futuro nao
aceita recebimento. O registro confirmado conclui o atendimento e bloqueia sua
edicao/exclusao; repetir o mesmo valor devolve o registro existente. Correcao ou
estorno desses recebimentos nao faz parte desta entrega.

A tela Recebimentos soma somente valores confirmados: semana segunda a domingo
e mes calendario, pela data da confirmacao no fuso do estabelecimento. Reservas e
atendimentos concluidos sem confirmacao de valor nao entram no total. Funcionarios
nao acessam controles ou totais de recebimentos. O catalogo nao e controle de estoque,
custos, lucro ou sistema contabil.

Contratos novos (JWT existente, salvo rotas publicas):

- GET/POST /servicos; PUT /servicos/{id}. Escrita somente proprietario.
- GET /servicos/link: caminho publico fixo, somente proprietario.
- GET /publico/{link}: catalogo minimo, sem contatos de terceiros.
- GET /publico/{link}/horarios?servicoId=ID&profissionalId=ID&data=AAAA-MM-DD.
- POST /publico/{link}/reservas: chave UUID, servicoId, profissionalId, data, horario, nome e telefone.
- POST /recebimentos/marcacoes/{id}: {valor}, somente proprietario.
- GET /recebimentos?data=AAAA-MM-DD: totais e registros, somente proprietario.

Agendas incluem servicoNome/precoPrevisto (nulos no legado), recebimentoConfirmado
e podeConfirmarRecebimento. Migracao V10 e aditiva. Reservas publicas continuam
sujeitas ao acesso vigente do negocio, isolamento e validacao de disponibilidade.
Todos os servicos ativos podem ser reservados com qualquer profissional ativo do
mesmo negocio. Janela publica: hoje ate 365 dias, inicio em passos de 15 minutos.
Limites publicos padrao por instancia/IP: 10 reservas e 120 consultas por minuto.

Resultados executados, comandos e limitacoes estao em docs/MARCAE-VALIDACAO.md.
Homologacao local nao representa publicacao ou cobranca real.
