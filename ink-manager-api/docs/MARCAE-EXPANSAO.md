# Marcaê — expansão incremental (2026-10-18)

Este plano amplia o checkout existente do Ink Manager; não reinicia o produto nem
substitui o frontend, a agenda, serviços, autenticação, recebimentos ou dados.
O outro projeto Marcae, em pasta separada, não faz parte desta entrega.

## Decisões confirmadas

- Preço de novas mensalidades: **R$ 29,90**, definido pelo servidor. A configuração
  padrão já foi atualizada. O checkout existente continua sendo Mercado Pago; não
  foi criada cobrança nem chamada externa.
- Teste: **14 dias, sem exigir cartão no início**, conforme a regra existente de
  avaliação contada desde o cadastro. O prazo é decisão do produto, não evidência de
  que converta mais.
- Reservas e atendimento usam horário local do estabelecimento. A taxa mensal de
  comparecimento será `comparecimentos / (comparecimentos + faltas)`. Cancelamentos
  e reservas futuras não entram no denominador. Sem atendimentos elegíveis, a taxa e
  a variação ficam indisponíveis; quando o mês anterior não tiver base, não haverá
  variação percentual inventada.
- Receita realizada usa recebimentos confirmados pela data de confirmação e fuso do
  estabelecimento. Valor de faltas é somente a soma dos preços snapshot, nomeada
  “valor potencial de faltas”; não é receita contábil perdida nem prejuízo.
- Recuperação por lista de espera só conta reserva vinculada a uma entrada da lista e
  recebimento confirmado daquela reserva. Lembrete não recebe crédito causal por
  evitar falta sem atribuição observável.
- Email e cobranças reais ficam desligados nesta implementação. Não comprar domínio,
  alterar DNS nem publicar configuração operacional de email.

## Etapas e dependências

1. **Base segura:** preservar e integrar o MVP, fixar R$29,90 em novas sessões de
   checkout, verificar vínculo por estabelecimento nas entidades/relações e adicionar
   presença `COMPARECEU`/`FALTOU` sem reinterpretar reservas legadas. Fazer inventário
   de ligações tenant existentes antes de tornar o link público disponível.
2. **Indicadores e proteção da agenda:** relatório mensal agregado por empresa;
   antecedência mínima configurável e limite de reservas abertas por email; histórico
   de faltas por cliente. Só publicar dados agregados/de próprio estabelecimento.
3. **Lista de espera:** candidatura por serviço/profissional/período, proposta de vaga
   após cancelamento, aceitação concorrente sob bloqueio de agenda e relação persistida
   entre entrada, reserva e recebimento para deduplicar recuperação.
4. **Emails transacionais em fila:** lembretes 24h/2h, resumo mensal e recuperação ao
   fim do teste. Outbox persistente, retries limitados com backoff, chave única por
   reserva/tipo/horário e cancelamento/adiamento invalidando os itens antigos. Feature
   flag de envio desligada por padrão. Tokens aleatórios de alta entropia, armazenados
   apenas como hash, prazo e escopo de ação; GET apenas apresenta página, POST valida
   tenant, ação, validade e uso antes de mutar estado. Nenhum token em log.
5. **UX e privacidade:** dashboard, seletor mensal, gráficos, ranking, onboarding,
   landing e fluxos públicos em cima dos contratos aprovados. Aviso de privacidade e
   termos com finalidade, papéis e bases definidos por finalidade; consentimentos
   separados apenas onde aplicável. Revisar com assessoria antes de anunciar
   conformidade legal.
6. **Verificação e entrega:** testes H2/PostgreSQL, isolamento cruzado, concorrência,
   idempotência e navegador; revisar diff/segredos e confirmar efeitos do push.

## Contratos da etapa incremental

`GET /relatorios/mensal?mes=YYYY-MM` (somente proprietário) é agregado e limitado
ao `estudioId` autenticado. Sem `mes`, usa o mês atual. Resposta:

```json
{
  "mes": "2026-10",
  "fusoHorario": "America/Sao_Paulo",
  "comparacaoDisponivel": false,
  "atual": {"agendamentos":0,"comparecimentos":0,"faltas":0,"cancelamentos":0,
    "denominadorComparecimento":0,"taxaComparecimento":null,
    "receitaRealizada":0.00,"valorPotencialFaltas":0.00},
  "mesAnterior": {"agendamentos":0,"comparecimentos":0,"faltas":0,"cancelamentos":0,
    "denominadorComparecimento":0,"taxaComparecimento":null,
    "receitaRealizada":0.00,"valorPotencialFaltas":0.00},
  "variacoes": {"agendamentos":null,"comparecimentos":null,"faltas":null,
    "cancelamentos":null,"pontosPercentuais":null,"receitaRealizada":null,
    "valorPotencialFaltas":null},
  "dias": [{"data":"2026-10-01","agendamentos":0,"comparecimentos":0,"faltas":0,"cancelamentos":0}],
  "semanas": [{"inicio":"2026-10-01","fim":"2026-10-04","agendamentos":0,
    "comparecimentos":0,"faltas":0,"cancelamentos":0}],
  "servicos": [{"nome":"Corte","agendamentos":4,"comparecimentos":3,"faltas":1}]
}
```

`agendamentos` são reservas não canceladas por data agendada. Cancelamentos são
contados à parte. Comparecimento inclui presença marcada `COMPARECEU` e registros
legados `CONCLUIDA`; falta exige `presenca=FALTOU`. O denominador é a soma desses dois
grupos, excluindo cancelamentos, futuros e legados sem classificação. A taxa é
percentual de 0 a 100, arredondada a 2 casas. `variacoes` são diferenças absolutas
entre os meses; `pontosPercentuais` é a diferença entre taxas. Se o mês anterior não
tiver reservas, todas as variações são nulas. Se algum dos meses não tiver base de
comparecimento, a taxa é nula e a variação em pontos percentuais também.

Gráficos e ranking usam data agendada; `semanas` são blocos segunda-domingo recortados
nas bordas do mês. `servicos` conta reservas não canceladas por nome snapshot e limita
a dez. Receita agrupa recebimentos pela data `confirmadoEm` no fuso local configurado.
Valor potencial de faltas soma somente snapshots de preço conhecidos; não é receita
contábil perdida. Ainda não há métricas de lista de espera no endpoint.

`GET /configuracao/agendamento` e `PUT /configuracao/agendamento` (somente proprietário)
leem/alteram `{ "antecedenciaMinimaMinutos":120,
"limiteAgendamentosPorEmail":3,"fusoHorario":"America/Sao_Paulo" }`.
Antecedência aceita 0..43200 minutos, limite aceita 1..50. Padrões iniciais são 120
minutos e 3 reservas futuras abertas por email por estabelecimento.

`PUT /marcacoes/{id}/presenca` (somente proprietário) recebe
`{ "presenca":"COMPARECEU" }` ou `{ "presenca":"FALTOU" }`; só libera após o fim da
duração registrada. Canceladas e atendimentos com recebimento não podem virar falta.
Recebimento confirmado marca automaticamente `COMPARECEU`. A resposta de agenda
adiciona `presenca` preservando os campos antigos. Um email e a caixa “Li o aviso de
privacidade” passam a ser obrigatórios na reserva pública; o email fica no cadastro
isolado criado para aquela reserva e o aceite registra versão/hora, sem ser tratado
como a base legal universal do tratamento.

## Checkout e preços existentes

O código já usa Mercado Pago: assinatura cartão via `/preapproval`, Pix via
`/checkout/preferences` e webhook com assinatura validada. Novos checkouts recebem
o valor configurado em `billing.monthly-amount` (padrão 29.90); registros antigos
conservam o valor persistido. Não atualizar contratos recorrentes já autorizados sem
aprovação operacional específica do titular e validação em ambiente de teste. A
documentação oficial do Mercado Pago descreve alteração de preço por `PUT
/preapproval/{id}`, operação que não será chamada nesta tarefa.

Referências consultadas em 18/10/2026:

- Lei nº 13.709/2018 (LGPD), texto oficial: https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm
- ANPD, agentes de tratamento e encarregado: https://www.gov.br/anpd/pt-br/assuntos/noticias/nova-versao-do-guia-dos-agentes-de-tratamento
- Mercado Pago, gestão de assinaturas: https://www.mercadopago.com.br/developers/pt/docs/subscriptions/subscription-management
- Mercado Pago, notificações de assinatura: https://www.mercadopago.com.br/developers/pt/docs/subscriptions/additional-content/your-integrations/notifications/webhooks

Os links orientam requisitos técnicos; não representam parecer jurídico nem
confirmação de domínio, SPF, DKIM, DMARC, credenciais, envio ou cobrança em produção.

Deployment prerequisite: inspect the existing server environment for BILLING_MONTHLY_AMOUNT and BILLING_TRIAL_DAYS. Set BILLING_MONTHLY_AMOUNT=29.90 and retain BILLING_TRIAL_DAYS=14 before offering new checkout links. The source defaults do not override an older deployment environment variable. Do not change active Mercado Pago preapprovals in this code delivery. SPF, DKIM and DMARC remain unconfigured until the sender domain and provider are chosen and an authorized DNS operator applies and verifies the records.
