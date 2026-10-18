# Testes Marcae simples - entrega ao Tech Lead e Engenheiro

Responsavel: Backend, exclusivamente src/test/**. Contrato lido: docs/MARCAE-CONTRATOS.md.

## Prontos para o operador unico

- StudioIntegrationTest: 16 novos cenarios HTTP, mantendo os 11 cenarios existentes.
- PublicRateLimitFilterTest: 5 cenarios unitarios com Clock fixo/controlado, sem esperar o tempo real.
- Limites publicos de escrita/leitura configurados em 1000 para a suite HTTP.
- Limpeza: recebimentos antes de marcacoes; marcacoes antes de servicos; servicos antes de estudios.
- Nenhum Maven, Playwright ou aplicativo foi executado por esta sessao.

## Cobertura

Catalogo publico minimo sem contatos, link estavel apos renomear, arquivamento, reserva sem JWT/sem criar usuario, resposta minima e no-store. Nome/telefone/chave/IDs invalidos. Isolamento de servico/profissional entre estudios. Duracao e preco fonte servidor, tentativa de adulteracao no POST e na consulta, snapshot apos editar catalogo e recebimento com valor real diferente do previsto.

Reserva 09:15 por 30 minutos, recusa 09:20/09:30, fronteira livre 09:45; turno, intervalo, bloqueio, janela hoje..365 dias; profissionais simultaneos; duas reservas concorrentes com chaves diferentes e iguais; ausencia de clientes parciais apos conflito; assinatura expirada; ordenacao data/horario/id.

Permissoes owner/funcionario/anonimo, isolamento de recebimentos, confirmacao conclui atendimento, idempotencia decimal, concorrencia com valores iguais/diferentes, canceladas/futuras/concluidas sem recebimento nao contam, entradas monetarias invalidas, valor zero, edicao/exclusao de marcacao recebida retornam 409.

Resumo: soma exata BigDecimal, semana segunda..domingo atravessando o ano, mes calendario, limites UTC que caem no dia anterior em America/Sao_Paulo, timestamp com offset -03:00, uniao mes/semana sem duplicar e filtro pela data de confirmacao em vez da data da marcacao. Fixtures historicas sao preparadas por JDBC; as verificacoes de contrato usam HTTP.

## Validacao pendente - somente Engenheiro

Executar a suite com Java 25 e a configuracao de certificados ja usada no projeto. Rodar H2 e PostgreSQL conforme o fluxo coordenado; nao executar junto com outro build/Playwright. Nao houve compilacao nesta sessao, portanto nao ha afirmacao de testes aprovados.

Pontos de integracao: V10 precisa estar disponivel antes da limpeza; propriedades studio.public-bookings-per-minute e studio.public-queries-per-minute; recebimentos/profissionais/servicos exigem os contratos atuais; drivers H2/PostgreSQL devem aceitar OffsetDateTime no fixture confirmado_em. Nenhuma dependencia nova adicionada.
## Integracao apos primeira execucao H2

Confirmacao publica exige 8 campos, incluindo duracaoMinutos=30. Acrescentados asserts recebimentoConfirmado/podeConfirmarRecebimento no GET individual e lista para recebido/futuro, alem de cancelado e atendimento passado elegivel.

Execucao do Engenheiro: 27 HTTP, 1 falha de idempotencia no timestamp. Primeiro retorno 2026-10-18T14:37:23.4093742-03:00 e retry .409374-03:00: precisao de TIMESTAMP(6) diverge do Instant criado em memoria. Expectativa de mesma confirmacao preservada; implementar precisao consistente no src/main cabe ao Lead. Sem execucao de build nesta sessao.

## Aceite final observado nos logs do operador unico

- target/marcae-h2-final.log, 18/10/2026 14:43:48 -03:00: BUILD SUCCESS, 42 testes reportados, 0 falhas, 0 erros, 1 ignorado (migracao exclusiva PostgreSQL).
- target/marcae-postgres-final.log, 18/10/2026 14:46:35 -03:00: BUILD SUCCESS, 42 testes, 0 falhas, 0 erros, 0 ignorados; pacote JAR gerado.
- A classe HTTP compilada em 14:42:25 e posterior ao fonte final de 14:41:37, incluindo os novos indicadores de recebimento.
- A correcao de precisao no src/main foi feita pelo Lead. Comparacao integral de confirmacao idempotente preservada e aprovada nas duas suites.
- Testes executados pelo Engenheiro em serie. Esta sessao somente leu os logs e alterou src/test. Aceite de navegador pertence ao Engenheiro; nao inferido desses testes.

## Marcae incremental - V11 fase 1 (cenarios escritos)

Alterado StudioIntegrationTest: pedido publico agora inclui email valido e aceite do aviso de privacidade para manter compatibilidade dos cenarios V10. Cenarios novos: configuracao de agendamento owner-only, tenant, valores e fuso; email/aviso obrigatorios e persistidos; limite de reservas por email limitado ao estudio e HTTP 429; antecedencia minima verificada tanto em horarios quanto no POST; presenca owner-only, apos fim, ids cruzados, reservas canceladas e atendimento recebido; historico de faltas agrupado pelo email apenas dentro do tenant; relatorio mensal com denominador (CONCLUIDA legado, FALTOU, cancelada, pendencia sem classificacao), comparacao sem base, mes vazio, receita confirmadaEm/fuso, preco potencial de snapshot e isolamento tenant; expectativa de inclusao de reservas futuras em agendamentos e exclusao do denominador conforme contrato V11.

A decisao comercial R$29,90 e o teste de 14 dias sao parametros/contratos do produto; estes testes nao executam checkout, webhook, Mercado Pago, email real ou cobranca. V12 ainda nao publicado: nenhum teste de lista de espera ou token de acao foi incluido.

Execucao pendente do Engenheiro: apos o Lead terminar a compilacao/integracao V11, rodar H2 e PostgreSQL em serie com Java 25 e relatar falhas. Backend-testes nao executou Maven/Playwright; este documento registra apenas cenarios escritos. O relatorio conta reservas futuras em agendamentos, mas as exclui do denominador de comparecimento conforme contrato V11.
### Cadastro e avaliacao sem checkout (cenario escrito)

StudioIntegrationTest.cadastroNovoIniciaAvaliacaoSemPagamentoOuChamadaAoGateway cobre POST /auth/estudios sem dados de pagamento, login e GET /assinatura; verifica AVALIACAO, mensalidade 29.90 BRL, avaliacaoAte em torno de 14 dias apos criado_em, tabelas assinatura_checkouts/assinatura_pagamentos vazias e zero interacoes com MercadoPagoGateway. Engenheiro: executar este teste com os cenarios V11 junto a suite H2 e PostgreSQL, em serie. Sem Maven nesta sessao.
