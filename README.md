# Ink.Manager 🎨

Sistema de gerenciamento para estúdios de tatuagem, desenvolvido em **Java**, com persistência de dados em **PostgreSQL** utilizando **JDBC**.

O projeto começou utilizando arquivos `.txt` e evoluiu para uma aplicação com banco de dados e arquitetura em camadas.

## 🚀 Tecnologias

* Java
* JDBC
* PostgreSQL
* SQL
* Git e GitHub

## 📌 Funcionalidades

### Clientes

* Cadastrar
* Listar
* Buscar por ID
* Atualizar
* Remover

### Marcações

* Cadastrar
* Listar
* Associar a um cliente
* Definir data, horário, descrição e status

## 🗄️ Banco de dados

O projeto utiliza PostgreSQL com duas tabelas principais:

```text
clientes
├── id
├── nome
├── telefone
├── idade
└── instagram

marcacoes
├── id
├── cliente_id → clientes.id
├── data
├── horario
├── descricao
└── status
```

## 🏗️ Estrutura

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
```

## ⚙️ Como executar

### 1. Pré-requisitos

* JDK 25+
* PostgreSQL
* Git

### 2. Criar o banco

```sql
CREATE DATABASE ink_manager;
```

Depois, crie as tabelas `clientes` e `marcacoes`.

### 3. Configurar a conexão

No arquivo `ConnectionFactory.java`:

```java
private static final String URL =
        "jdbc:postgresql://localhost:5432/ink_manager";

private static final String USUARIO = "postgres";
private static final String SENHA = "SUA_SENHA";
```

> Não envie sua senha real para o GitHub.

### 4. Compilar

Com o driver PostgreSQL dentro da pasta `lib`:

```bash
javac -cp "lib/postgresql-42.7.13.jar" -d out *.java
```

### 5. Executar

**Windows:**

```bash
java -cp "out;lib/postgresql-42.7.13.jar" MainMaster
```

**Linux/macOS:**

```bash
java -cp "out:lib/postgresql-42.7.13.jar" MainMaster
```

## 📚 Objetivo

Projeto desenvolvido para praticar:

* Programação Orientada a Objetos
* CRUD
* SQL e PostgreSQL
* JDBC
* Persistência de dados
* Relacionamento entre tabelas
* Arquitetura em camadas
* Git e GitHub

## 🚧 Próximos passos

* Melhorar validações e tratamento de erros
* Criar novas funcionalidades
* Implementar testes
* Migrar para **API REST com Spring Boot**
* Documentar a API com Swagger
* Utilizar Docker

## 👨‍💻 Autor

**Pablo Soares**

Projeto desenvolvido para estudos e evolução prática em **Backend Java**.
