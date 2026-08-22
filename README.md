# Ink.Manager

Sistema para gerenciamento de clientes e marcações de um estúdio de tatuagem.

O projeto começou como uma aplicação Java em linha de comando, utilizando JDBC e PostgreSQL, e atualmente está sendo migrado para uma API REST com Spring Boot e Spring Data JPA.

## 🚀 Versão atual

A versão atual do projeto está localizada em:

```text
ink-manager-api/
```

### Tecnologias utilizadas

* Java
* Spring Boot
* Spring Web
* Spring Data JPA
* PostgreSQL
* Maven
* Git e GitHub

## 📌 Funcionalidades

### Clientes

* Cadastrar cliente
* Listar clientes
* Buscar cliente por ID
* Atualizar cliente
* Remover cliente

### Marcações

* Cadastrar marcação
* Listar marcações
* Buscar marcação por ID
* Atualizar marcação
* Remover marcação
* Relacionar uma marcação a um cliente
* Gerenciar status da marcação

Status disponíveis:

```text
AGENDADA
CONFIRMADA
CONCLUIDA
CANCELADA
```

## 🏗️ Arquitetura

A API está organizada em camadas:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
Spring Data JPA
    ↓
PostgreSQL
```

### Estrutura principal

```text
ink-manager-api/
└── src/
    └── main/
        └── java/
            └── ink/
                └── manager/
                    └── api/
                        ├── controller/
                        ├── model/
                        ├── repository/
                        ├── service/
                        └── ApiApplication.java
```

## 🌐 Endpoints

### Clientes

```http
GET /clientes
POST /clientes
GET /clientes/{id}
PUT /clientes/{id}
DELETE /clientes/{id}
```

### Marcações

```http
GET /marcacoes
POST /marcacoes
GET /marcacoes/{id}
PUT /marcacoes/{id}
DELETE /marcacoes/{id}
```

## 📝 Exemplo de cadastro de cliente

```json
{
  "nome": "Maria Souza",
  "telefone": "21988887777",
  "idade": 32,
  "instagram": "@mariasouza"
}
```

## 📅 Exemplo de cadastro de marcação

```json
{
  "cliente": {
    "id": 2
  },
  "data": "2026-08-25",
  "horario": "14:30:00",
  "descricao": "Tatuagem no braço",
  "status": "AGENDADA"
}
```

O cliente informado é buscado no banco através do seu ID antes da marcação ser salva.

## 🗄️ Banco de dados

O projeto utiliza PostgreSQL.

Principais tabelas:

```text
clientes
marcacoes
```

Uma marcação possui relacionamento com um cliente através da coluna:

```text
cliente_id
```

No Spring/JPA, esse relacionamento é representado com:

```java
@ManyToOne
@JoinColumn(name = "cliente_id", nullable = false)
private Cliente cliente;
```

## 🔄 Evolução do projeto

O Ink.Manager foi desenvolvido inicialmente como uma aplicação Java em linha de comando.

### Versão inicial

```text
Java CLI
   ↓
Service
   ↓
Repository JDBC
   ↓
PostgreSQL
```

Nesta versão, as operações com o banco eram feitas manualmente utilizando recursos como:

```text
Connection
PreparedStatement
ResultSet
SQL
```

### Versão atual

```text
REST API
   ↓
Controller
   ↓
Service
   ↓
Repository
   ↓
Spring Data JPA
   ↓
PostgreSQL
```

A migração para Spring Boot reduziu o código necessário para acesso ao banco e permitiu disponibilizar as funcionalidades através de endpoints REST.

## ▶️ Executando o projeto

Entre na pasta:

```bash
cd ink-manager-api
```

No Windows:

```bash
mvnw.cmd spring-boot:run
```

Após iniciar, a API estará disponível em:

```text
http://localhost:8080
```

Exemplo:

```bash
curl http://localhost:8080/clientes
```

ou:

```bash
curl http://localhost:8080/marcacoes
```

## 📚 Objetivo do projeto

O Ink.Manager está sendo desenvolvido como projeto de estudo e portfólio, aplicando conceitos de desenvolvimento backend com Java, orientação a objetos, API REST, persistência de dados, relacionamento entre entidades e arquitetura em camadas.

O projeto continuará evoluindo com novas funcionalidades e melhorias na arquitetura.
