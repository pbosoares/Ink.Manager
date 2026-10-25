# Marcae simples — contratos e divisao de trabalho

Esta implementacao pertence SOMENTE ao checkout Ink.Manager. Nao alterar o outro
projeto Marcae. Nome visual Marcaê. Preservar layout, billing, contratos legados e
dados; migracoes aditivas. Sem custos, estoque, lucro, campanhas ou provedor novo.

## Responsabilidade exclusiva (18/10/2026)

- Tech Lead (esta sessao): src/main/java/**, src/main/resources/db/**,
  docs/MARCAE-CONTRATOS.md. Integracao dos contratos abaixo. Nao edita static/testes.
- Frontend (terminal 91bd28eb): src/main/resources/static/** SOMENTE. Interface,
  marca e textos; novos agendar.html/agendar.js e modulo interno simple.js se util.
- Backend (terminal 5dfe9cdd): src/test/** SOMENTE. Adaptar limpeza das tabelas e
  testes HTTP, concorrencia, isolamento e recebimentos. Nao edita codigo principal.
- Engenheiro (terminal 39120b1c): README.md, ink-manager-api/README.md,
  docs/MARCAE-VALIDACAO.md e e2e/** SOMENTE. Validacao e build coordenados: e o unico
  operador Maven/Playwright apos a compilacao inicial do Lead finalizar. Nao editar
  contratos, static ou src/main/test. Pode preparar cenarios E2E.
- Marketing (terminal c6405d66): docs/MARCAE-COPY.md SOMENTE. Revisao de linguagem e
  promessas reais, sem campanhas. Frontend pode ler suas sugestoes.

Nao criar agentes adicionais, commits, publicacao, cobrancas, e-mails ou mensagens
externas. Agentes podem escrever relatorio ao final no arquivo exclusivo para o
Lead consultar. Configuracao de acesso continua sujeita ao sandbox/on-request.

## Comunicacao interna autorizada pelo usuario

Mensagens de trabalho entre estes terminais do Ink Manager estao autorizadas.
CLI: C:/Users/Acer/AppData/Local/Programs/orca/resources/bin/orca.exe (absoluto).
Se o sandbox negar acesso ao CLI/runtime, pedir escalacao on-request; nao reiniciar.
Ler terminal antes de enviar. Mensagens curtas com arquivo, dependencia e criterio
de aceite; confirmar recebimento e prosseguir. Nao reenviar por silencio: observar
recibo e leitura; se retry exigido pelo CLI, usar o requestId original.

- Tech Lead: term_f8562828-30a9-447d-9be0-f4eb00fdd047
- Frontend: term_91bd28eb-0d17-4b11-a5e0-899becbe6881
- Engenheiro: term_39120b1c-e6b0-4e1c-a65d-52ed402af3cb
- Backend-testes: term_5dfe9cdd-8ca5-4236-9bc0-770eb4b667ad
- Marketing-copy: term_c6405d66-3f0b-4818-adc8-8972fa100b37

Fluxo: Marketing avisa copy pronta ao Frontend; Backend-testes avisa testes prontos
ao Engenheiro; Frontend avisa interface pronta ao Engenheiro; Engenheiro executa
Maven/E2E em serie e devolve falhas ao dono dos arquivos, com evidencias ao Lead.
Lead publicou src/main e V10 para integracao; compilacao inicial aprovada. Nenhum
agente alem do Engenheiro deve executar build ou testes com banco compartilhado.
Nao enviar comandos shell aos agentes; nao mandar prompts a terminais shell.

## Servicos e link (JWT existente)

- GET /servicos -> [{id,nome,preco,duracaoMinutos,ativo,version}].
- POST /servicos, PUT /servicos/{id} (proprietario):
  {nome,preco,duracaoMinutos,ativo,version?}. 201/200. Preco >=0 com ate 2 casas;
  duracao 1..720 min. Edicao exige version; arquivar com ativo=false, nao excluir.
- GET /servicos/link (proprietario) -> {caminho:"/agendar.html?local=TOKEN",
  caminhoCurto:"/a/CODIGO"}. Prefixar caminhoCurto com location.origin para compartilhar.
  Para o UUID atual, CODIGO tem 22 caracteres seguros para URL. O link permanece estavel
  ao renomear o estabelecimento e resolve para a mesma agenda; links antigos continuam aceitos.
- Ao criar ou reagendar uma reserva ativa com email, enfileirar CONFIRMACAO_AGENDAMENTO
  imediatamente para o cliente. A mensagem informa procedimento, data, horario e fuso do
  estabelecimento; cancelamento ou reagendamento invalida a confirmacao ainda pendente.
- Expedientes/folgas existentes em /profissionais permanecem.

## Link publico — sem token, sem conta

- GET /publico/{link} -> {nome,fusoHorario,hoje,servicos:[{id,nome,preco,
  duracaoMinutos}],profissionais:[{id,nome}]}.
- GET /publico/{link}/horarios?servicoId=1&profissionalId=2&data=YYYY-MM-DD ->
  {profissionalId,data,duracaoMinutos,fusoHorario,horarios:["09:15:00",...]}.
- POST /publico/{link}/reservas -> 201:
  {chave:UUID,servicoId,profissionalId,data,horario,nome,telefone}.
  Resposta minima: {protocolo,servico,profissional,data,horario,fusoHorario,preco,duracaoMinutos}.
  Repetir EXATAMENTE o pedido com mesma chave retorna mesma confirmacao (201).
  Reutilizar chave com dados diferentes ->409. Conservar chave em falha de rede.
- Nunca devolver contatos, clientes ou reservas de terceiros no publico.
- Data hoje..hoje+365 dias; slots a cada 15 min, duracao inteira dentro do turno,
  sem intervalos/bloqueios/reservas. O servidor usa duracao/preco do catalogo e
  salva snapshot. 09:15+30min ocupa [09:15,09:45); 09:20 nao e aceito; 09:45 pode.
  Profissionais diferentes atendem simultaneamente. Escritas bloqueiam o estudio.
- Conflito ->409 e recarregar horarios. 404 inexistente/inativo, 400 validacao,
  429 limite. Consultas publicas no-store. Atualizar horarios com polling leve.

## Agenda e recebimentos (JWT existente)

- GET /marcacoes preserva campos existentes e adiciona servicoNome,precoPrevisto
  (nulos no legado). Ordem servidor por data, horario, id. duracaoMinutos e snapshot.
  Inclui recebimentoConfirmado e podeConfirmarRecebimento, calculados pelo servidor
  no fuso do negocio. Ocultar acao para futuras/canceladas e mostrar confirmado
  sem exigir abrir Recebimentos. Esses indicadores nao substituem permissao owner.
- POST /recebimentos/marcacoes/{id} (SOMENTE proprietario): {valor:decimal}.
  ->200 {id,marcacaoId,cliente,servico,valor,confirmadoEm:ISO com offset}.
  Valor efetivamente recebido >=0, ate 2 casas. Marca atendimento CONCLUIDA.
  Reserva futura ou CANCELADA ->409. Repetir mesmo valor retorna mesmo recebimento;
  outro valor ->409. Atendimento com recebimento nao aceita editar/excluir (409).
- GET /recebimentos?data=YYYY-MM-DD (opcional, padrao hoje; proprietario):
  {moeda:"BRL",fusoHorario,inicioSemana,fimSemana,inicioMes,fimMes,totalSemana,
  totalMes,recebimentos:[linha acima]}.
  Semana segunda..domingo. Mes calendario. Datas limites inclusivas na resposta.
  Totais por DATA DA CONFIRMACAO no fuso configurado (padrao America/Sao_Paulo),
  NAO pela data agendada. Lista e uniao do mes e semana selecionados, sem duplicar.
  Somente registros confirmados; reservas/concluidas sem recebimento nao contam.
- Permissao e isolamento validados no servidor. Assinatura existente permanece:
  novas escritas dependem de acesso vigente; publico nao pode contornar billing.

## UX enxuta

Estabelecimento: cadastrar nome/preco/duracao dos servicos e expediente uma vez,
copiar link fixo; agenda ordenada; confirmar atendimento e valor em uma acao;
recebimentos semanal/mensal. Servicos/link podem ficar em Equipe e horarios ou
Meu negocio para nao multiplicar menus. Um menu Recebimentos so para proprietario.
Cliente: servico (mostrar preco/duracao), profissional, dia/horario, nome/telefone,
reservar. Sem cadastro, painel financeiro complexo ou etapas de pagamento publico.

## Decisoes de implementacao

- Todos os servicos ativos podem ser escolhidos com qualquer profissional ativo
  do mesmo estabelecimento. Nao introduzir matriz de especialidades nesta entrega.
- Novos estabelecimentos recebem link UUID; legados recebem link estavel e-ID na
  migracao. Nome da empresa pode mudar sem alterar o link. Link e publico, nao senha.
- A consulta publica usa passos de 15 minutos e sempre encaixa a duracao inteira.
  Somente o servidor resolve preco/duracao; a confirmacao devolve os snapshots.
- Reserva anonima cria contato minimo sem consultar ou atualizar contatos antigos
  por telefone. A chave do pedido evita duplicar cliente/reserva ao repetir envio.
- Recebimento e confirmado no instante atual, sem retroagir datas nem aceitar data
  enviada pelo navegador. Libera a partir do inicio agendado, com declaracao do
  proprietario de que atendeu e recebeu. Registros confirmados sao imutaveis nesta
  entrega; correcao/estorno financeiro fica fora do escopo, sem exclusao silenciosa.
- Limites publicos por IP observado: 10 POST e 120 consultas/minuto por instancia,
  sem confiar em X-Forwarded-For do cliente. Proxy pode compartilhar cota entre
  clientes; uma operacao maior exige limite no proxy validado, sem desativar protecao.
- Issuer JWT, pacotes Java, IDs do billing e migracoes antigas mantidos para
  preservar contratos. Somente textos de marca da assinatura/recuperacao mudam.
