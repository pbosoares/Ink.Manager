# Validacao da base SaaS - 17/10/2026

- PostgreSQL 18.4: 18 testes aprovados, sem falhas ou testes ignorados.
- H2: 17 testes aprovados; teste de migracao PostgreSQL ignorado por condicao.
- Chromium: fluxo completo aprovado com cadastro autonomo de estudio, cliente recorrente, criacao de profissional, duas reservas, edicao, recarregamento e novo login.
- Menu mobile corrigido e teste de largura em 390px aprovado.
- Isolamento: consultas e vinculos entre estudos rejeitados; equipe restrita ao proprio estudio.
- Funcionario agenda; configuracao e exclusao exigem responsavel. Desativar acesso invalida a sessao.
- Recuperacao de senha testada com adaptador de e-mail simulado: token unico, prazo e revogacao de sessoes.

Logs e capturas locais ficam em target, fora do Git. Os testes usaram bancos
isolados, sem acessar dados do estudio. A CI original foi preservada; ampliacao de
Docker/CI fica para depois conforme solicitado.

## Ainda depende de publicacao e integracoes

Nao existe endereco publico. Hospedagem, dominio/HTTPS, SMTP real e conta recebedora
precisam ser configurados pelo operador da plataforma. O assinante nao instala nada.
A base de checkout e livro de pagamentos esta preparada; o fluxo de assinatura e
seus testes serao entregues em commit separado. Nenhuma cobranca real foi realizada.
