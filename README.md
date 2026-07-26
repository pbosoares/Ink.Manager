# Ink.Manager 🎨

Sistema de gerenciamento para estúdios de tatuagem, desenvolvido em Java.

O projeto começou como uma aplicação simples utilizando arquivos `.txt` para armazenar os dados. Durante o desenvolvimento, evoluiu para utilizar PostgreSQL como banco de dados, com integração através do JDBC.

## 🚀 Tecnologias utilizadas

- Java
- JDBC
- PostgreSQL
- SQL
- Git e GitHub

## 📌 Funcionalidades

### Clientes

- Cadastrar cliente
- Listar clientes
- Buscar cliente por ID
- Atualizar dados do cliente
- Remover cliente

### Marcações

- Cadastrar marcação
- Listar marcações
- Associar uma marcação a um cliente
- Definir data, horário, descrição e status da marcação

## 🗄️ Banco de dados

O projeto utiliza PostgreSQL para persistência dos dados.

O banco possui as seguintes tabelas:

### clientes

- id
- nome
- telefone
- idade
- instagram

### marcacoes

- id
- cliente_id
- data
- horario
- descricao
- status

A tabela `marcacoes` possui um relacionamento com a tabela `clientes` através da chave estrangeira `cliente_id`.

## 🏗️ Estrutura do projeto

```text
MainMaster
     ↓
Service
     ↓
Repository
     ↓
JDBC
     ↓
PostgreSQL

📚 Objetivo do projeto

O Ink.Manager é um projeto desenvolvido para praticar e consolidar conhecimentos em:

Programação Orientada a Objetos com Java
SQL
JDBC
PostgreSQL
Persistência de dados
Relacionamento entre tabelas
Organização de código.

🚧 Próximos passos
Melhorar as validações dos dados
Criar novas funcionalidades para as marcações
Melhorar a interface do sistema
Migrar o projeto para uma API REST utilizando Spring Boot