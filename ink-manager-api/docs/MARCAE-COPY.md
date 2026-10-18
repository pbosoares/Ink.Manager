# Marcaê — copy para consulta do Frontend

Proposta de textos para a implementação descrita em MARCAE-CONTRATOS.md. Não comprova validação dos fluxos. Manter layout, identidade e componentes atuais; adaptar os textos aos espaços existentes. Escopo exclusivo: este documento.

## Linguagem e promessa

Nome visual: **Marcaê**. Público: profissionais e estabelecimentos de tatuagem, manicure, barbearia e sobrancelhas. Usar “seu negócio”, “serviço”, “profissional” e “atendimento” nas telas compartilhadas.

Promessa central: **Um link fixo para seus clientes reservarem os horários disponíveis.**

A disponibilidade vem do expediente, duração dos serviços, intervalos, folgas e reservas. Um horário consultado pode ser ocupado antes da conclusão; confirmar a reserva somente após sucesso do servidor.

Não prometer aumento de receita, redução de faltas ou economia de tempo medida. Não anunciar pagamento online, notificações automáticas, estoque, custos, lucro ou recursos extras. Recebimentos são valores informados e confirmados pelo responsável; não são transações processadas pelo Marcaê.

## Landing

- Título: **Seu cliente escolhe o horário. Você acompanha a agenda.**
- Apoio: **Configure serviços, preços, duração e expediente. Compartilhe seu link fixo para receber reservas com nome e telefone, sem conta para o cliente.**
- Identificação de público, se houver espaço: **Para tatuagem, manicure, barbearia e sobrancelhas.**
- Ação principal: **Testar grátis por 14 dias** — direcionar ao cadastro do estabelecimento com teste implementado. Se houver somente login, manter **Entrar**; não simular ativação de teste.
- Ação de acesso existente: **Já tenho conta**.
- Benefício 1: **Configure uma vez** — Cadastre seus serviços e expediente. Atualize quando precisar.
- Benefício 2: **Compartilhe seu link** — O cliente consulta os horários disponíveis e reserva sem criar conta.
- Benefício 3: **Acompanhe os recebimentos** — Confirme o atendimento e o valor recebido para consultar os totais da semana e do mês.
- Fluxo resumido: **Configure seus serviços → compartilhe seu link → acompanhe as reservas.**

## Área do estabelecimento

| Contexto | Texto sugerido |
| --- | --- |
| Serviços e expediente | Serviços e horários |
| Apoio da configuração | Cadastre nome, preço e duração dos serviços e configure o expediente de cada profissional. Depois, copie seu link de reservas. |
| Novo serviço | Adicionar serviço |
| Campos | Nome do serviço · Preço (R$) · Duração (minutos) |
| Salvar serviço | Salvar serviço |
| Arquivar serviço | Arquivar serviço |
| Apoio ao arquivamento | Este serviço deixará de aparecer para novas reservas pelo link. |
| Salvar expediente | Salvar horários |
| Link público | Seu link de reservas |
| Apoio do link | Compartilhe este link com seus clientes. Ele continua o mesmo se você mudar o nome do negócio. |
| Copiar / sucesso | Copiar link / Link copiado. |
| Falha ao copiar | Não foi possível copiar. Selecione o link e copie manualmente. |
| Agenda vazia | Nenhum atendimento para esta seleção. |
| Recebimentos | Recebimentos |
| Indicadores | Recebido na semana · Recebido no mês |
| Apoio dos totais | Somente valores confirmados. Os totais consideram a data da confirmação, no fuso do negócio. |
| Definição de períodos | Semana: segunda a domingo. Mês: mês calendário. |
| Lista vazia | Nenhum recebimento confirmado neste período. |

### Confirmar atendimento e recebimento

- Ação na agenda, exclusiva do responsável: **Confirmar atendimento e valor**.
- Título: **Confirmar atendimento e recebimento**.
- Resumo: mostrar cliente, serviço, data e horário correspondentes ao registro.
- Campo: **Valor recebido (R$)**.
- Apoio: **Informe o valor efetivamente recebido, mesmo que seja diferente do preço previsto.**
- Se o preço previsto aparecer: **Preço previsto: {preço}**. Não tratar esse valor como já recebido.
- Aviso antes de confirmar: **Ao confirmar, o atendimento será concluído e o valor entrará nos totais pela data desta confirmação. Depois, este registro não poderá ser alterado ou excluído.**
- Botões: **Voltar** / **Confirmar atendimento e recebimento**.
- Sucesso: **Atendimento e recebimento confirmados.**
- Conflito de valor: **Já existe um recebimento confirmado para este atendimento com outro valor. Atualize a página para consultar o registro.**
- Reserva futura: **Este atendimento ainda está no futuro. Confirme o recebimento após o atendimento.**
- Cancelada: **Não é possível confirmar recebimento de um atendimento cancelado.**

## Página pública de reservas

- Título: **Reserve seu horário em {nome do negócio}**.
- Apoio: **Escolha o serviço, o profissional e um horário disponível. Para reservar, informe seu nome e telefone. Não precisa criar conta.**
- Campos: **Serviço · Profissional · Dia · Horário · Seu nome · Telefone**.
- Opção do serviço: **{serviço} — {preço} · {duração} min**. Usar os dados do servidor; formatar preço em reais, inclusive R$ 0,00.
- Resumo antes de enviar: **{serviço} com {profissional}, em {data}, às {horário}. Duração: {duração} min. Preço: {preço}.**
- Identificação do fuso: **Horários no fuso {fuso do negócio}.**
- Botão: **Reservar horário**.
- Durante envio: **Reservando…**.
- Apoio: **Esta etapa registra a reserva e não realiza pagamento.**
- Não solicitar e-mail, senha ou criação de conta. Não sugerir envio automático de confirmação.

### Confirmação pública — apenas após resposta de sucesso

- Título: **Reserva confirmada**.
- Texto: **Sua reserva em {nome do negócio} foi registrada.**
- Dados: **Serviço · Profissional · Data · Horário · Fuso · Preço · Protocolo** — preencher com a confirmação retornada pelo servidor.
- Apoio: **Guarde o protocolo e os dados da sua reserva.**
- Não usar “pagamento confirmado”, “recebemos seu pagamento” ou “enviamos por WhatsApp”.

## Estados e mensagens públicas

| Situação | Texto sugerido |
| --- | --- |
| Carregando página | Carregando serviços e profissionais… |
| Carregando horários | Consultando horários disponíveis… |
| Seleção incompleta | Escolha o serviço, o profissional e o dia para consultar os horários. |
| Nenhum horário | Nenhum horário disponível neste dia. Escolha outra data ou outro profissional. |
| Nenhum serviço ou profissional disponível | Não há opções disponíveis para reserva neste momento. |
| Conflito de horário | Este horário não está mais disponível. Atualize os horários e escolha outro. |
| Ação após conflito | Atualizar horários |
| Nome ausente | Informe seu nome. |
| Telefone ausente | Informe seu telefone. |
| Validação geral | Confira os campos indicados antes de reservar. |
| Data fora do intervalo | Escolha uma data entre hoje e {data limite informada pelo sistema}. |
| Link inexistente ou inativo | Este link não está disponível para reservas. Confira o endereço com o estabelecimento. |
| Limite de tentativas | Muitas tentativas em pouco tempo. Aguarde um momento e tente novamente. |
| Falha ao consultar | Não foi possível carregar os horários. Tente novamente. |
| Resultado do envio desconhecido por falha de conexão | Não foi possível confirmar o resultado da reserva. Tente novamente para verificar. |
| Repetir envio após falha de conexão | Tentar novamente |
| Indisponibilidade temporária | Não foi possível concluir agora. Tente novamente em instantes. |

Nota para o Frontend: em falha de rede ao enviar, repetir os mesmos dados com a mesma chave conforme contrato. Não afirmar que a reserva falhou nem induzir uma nova reserva enquanto o resultado for desconhecido. Mensagens específicas devem corresponder ao erro real; um 409 genérico não comprova conflito de horário. Não expor detalhes internos, dados de terceiros ou situação de cobrança do estabelecimento na página pública.

## Critério para aplicar a copy

Usar somente os textos dos fluxos efetivamente implementados. Manter as permissões: confirmação de recebimentos e seus totais são exclusivos do responsável. Não chamar totais de “lucro”, “saldo disponível” ou “faturamento previsto”. Não acrescentar campanhas, depoimentos, garantias ou resultados sem comprovação.

Entrega: sugestões documentadas para consulta do Frontend; nenhum arquivo da interface, README ou contrato foi alterado nesta tarefa.

## Oferta comercial autorizada — revisão de 18/10/2026

Esta seção atualiza a oferta e o CTA da landing. Textos para o negócio que assina o Marcaê; não inserir mensalidade nem teste na página pública de reservas dos clientes.

- Título preferencial: **Seu link de reservas. Sua agenda em um só lugar.**
- Alternativa curta: **Seu link. Sua agenda.**
- Apoio: **Configure serviços, preços, duração e expediente uma vez. Compartilhe um link fixo para seus clientes consultarem os horários disponíveis e reservarem com nome e telefone, sem criar conta. Atualize sua configuração quando precisar.**
- Oferta: **Teste grátis por 14 dias, sem cartão. Depois, R$ 29,90/mês.**
- CTA principal: **Testar grátis por 14 dias**.
- Apoio junto ao CTA: **Sem cartão para começar. R$ 29,90/mês após o teste.**
- CTA para quem já tem conta: **Entrar**.
- Texto de recebimentos: **Confirme o atendimento e o valor recebido. Consulte os totais confirmados da semana e do mês.**

Aceite: preservar visual e escopo; título pode ser adaptado ao espaço mantendo a promessa do link fixo e da disponibilidade consultada. Não prometer disponibilidade garantida até o envio da reserva. Aplicar oferta e CTA em produção somente com preço e teste correspondentes no fluxo real; este documento não certifica essa implementação.

Suposição registrada: os 14 dias foram adotados do intervalo autorizado pelo usuário. Sua adequação à conversão não foi comprovada. Não dizer que é o prazo ideal, que o produto se paga, que uma falta evitada cobre a mensalidade, que aumenta receita ou que reduz faltas. Não acrescentar promessa de cancelamento, renovação automática ou cobrança automática sem contrato e comportamento confirmados. A ausência de cartão no teste não define, por si só, a política ao final dele.

### Valores confirmados e valores potenciais de faltas

- **Recebido na semana / Recebido no mês:** somente valores efetivamente informados e confirmados pelo responsável, pela data de confirmação. São recebimentos registrados, sem verificação de transação bancária pelo Marcaê.
- **Preço previsto:** preço registrado para o atendimento; não é receita confirmada.
- Uma falta não comprova perda de receita igual ao preço do serviço. Não somar preços de faltas aos recebimentos, nem apresentar essa soma como dinheiro perdido, recuperado, economia ou retorno do Marcaê.
- Se houver referência explicativa a um valor potencial: **Valor potencial associado a faltas: estimativa pelos preços previstos, não recebida e sem comprovação de perda.** Esta distinção é orientação de linguagem, não pedido de novo indicador, tela, status ou calculadora. Não introduzir esse recurso no escopo simples.
- Preferir a copy verificável: **Acompanhe o que foi recebido e confirmado.** Não anunciar prevenção de faltas ou ganho financeiro causal.

Entrega desta revisão: somente copy comercial e distinção de valores neste arquivo; sem alterações de frontend, contratos, campanhas ou execução de cobrança.
