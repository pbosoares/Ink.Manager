# Plano de lancamento: R$ 19,90 por mes por estudio

Oferta definida: agenda unica, cadastro de clientes e painel geral, com atualizacoes
do produto. Suporte por mensagem em horario comercial, com canal e janela
informados na proposta. Personalizacoes sao orcadas separadamente. O preco nao e
vitalicio; qualquer reajuste deve ser comunicado previamente conforme o acordo.

## Modelo da primeira versao

Uma instalacao dedicada por estudio: banco, volumes, rede, JWT e convite proprios.
As contas da equipe do mesmo estudio compartilham os dados e permissoes.
Nao cadastrar empresas diferentes na mesma instalacao.
Use um servidor/VM independente por estudio nesta topologia: o proxy publica
80/443. Projetos Compose separados no mesmo host nao resolvem a disputa dessas
portas. Hospedagem compartilhada exige outro desenho de proxy e validacao.

## Preparar cada instalacao

Em uma copia independente da aplicacao, execute no PowerShell:

```powershell
./deploy/New-StudioConfig.ps1 -StudioId estudio-exemplo -Domain agenda.exemplo.com.br -ImageTag release-validada
docker compose --env-file .env -f compose.production.yml config --quiet
docker compose --env-file .env -f compose.production.yml up --build -d
```

O script gera segredos aleatorios independentes, nao imprime seus valores e
recusa sobrescrever arquivo existente. Restrinja acesso ao .env ao operador.
O identificador do estudio define o projeto Compose e seus volumes.
Nao troque esse identificador depois de gravar dados: outro nome aponta para
outros volumes, sem migrar os registros existentes.

Cadastre a equipe pelo convite do .env. Depois deixe STUDIO_REGISTRATION_CODE
vazio e recrie a API. Valide bloqueio de cadastro e login das contas existentes.

## Cobranca e operacao

Nesta versao, a mensalidade e cobrada manualmente pelo responsavel comercial.
Nao ha checkout, assinatura recorrente, webhook ou suspensao automatica.
Registre cliente, vencimento e pagamento fora do repositorio, sem dados de cartao.
O preco de R$ 19,90 nao comprova viabilidade: meca infraestrutura, backup,
taxas, impostos e tempo de suporte antes de ampliar as vendas.

Antes de cobrar por acesso operacional, cumpra docs/RELEASE.md: CI da revisao,
HTTPS, persistencia, backup restaurado, monitoramento e homologacao.
Para dois estudios, valide tambem que um token da instalacao A e rejeitado na B
e que criar/excluir um cliente em A nao altera os dados de B.
Nao marque estes testes como concluidos sem executa-los nos ambientes reais.
