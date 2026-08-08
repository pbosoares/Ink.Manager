# 🎨 Ink.Manager

Sistema de gerenciamento para tatuadores desenvolvido em **Java**, com evolução de uma aplicação Java tradicional para uma **API REST utilizando Spring Boot, Spring Data JPA, Hibernate e PostgreSQL**.

O projeto tem como objetivo facilitar o gerenciamento de **clientes e marcações**, permitindo cadastrar, consultar, atualizar e excluir informações de forma persistente.

---

## 🚀 Tecnologias

* Java 25
* Spring Boot 4
* Spring Data JPA
* Hibernate
* PostgreSQL
* Maven
* JDBC
* REST API
* Git e GitHub

---

## 📂 Estrutura do projeto

```text
Ink.Manager/
│
├── ink-manager-api/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── ink/manager/api/
│   │   │   │       ├── controller/
│   │   │   │       │   └── ClienteController.java
│   │   │   │       │
│   │   │   │       ├── exception/
│   │   │   │       │   └── GlobalExceptionHandler.java
│   │   │   │       │
│   │   │   │       ├── model/
│   │   │   │       │   └── Cliente.java
│   │   │   │       │
│   │   │   │       ├── repository/
│   │   │   │       │   └── ClienteRepository.java
│   │   │   │       │
│   │   │   │       ├── service/
│   │   │   │       │   └── ClienteService.java
│   │   │   │       │
│   │   │   │       └── ApiApplication.java
│   │   │   │
│   │   │   └── resources/
│   │   │       └── application.properties
│   │   │
│   │   └── test/
│   │
│   ├── pom.xml
│   ├── mvnw
│   └── mvnw.cmd
│
├── Cliente.java
├── ClienteRepository.java
├── ClienteService.java
├── Marcacao.java
├── MarcacaoRepository.java
├── MarcacaoService.java
├── StatusMarcacao.java
├── MainMaster.java
└── README.md
```

---

## 🏗️ Arquitetura da API

A API utiliza uma arquitetura em camadas:

```text
ClienteController
       ↓
ClienteService
       ↓
ClienteRepository
       ↓
Spring Data JPA
       ↓
Hibernate
       ↓
PostgreSQL
```

### Controller

Responsável por receber as requisições HTTP e disponibilizar os endpoints da API.

### Service

Responsável pelas operações e regras relacionadas aos clientes.

### Repository

Utiliza `JpaRepository` do Spring Data JPA para realizar as operações de persistência.

### Model

Representa as entidades utilizadas pela aplicação.

---

## 👤 CRUD de Clientes

A API possui atualmente um CRUD completo para clientes.

| Método   | Endpoint         | Descrição               |
| -------- | ---------------- | ----------------------- |
| `GET`    | `/clientes`      | Lista todos os clientes |
| `GET`    | `/clientes/{id}` | Busca cliente por ID    |
| `POST`   | `/clientes`      | Cadastra cliente        |
| `PUT`    | `/clientes/{id}` | Atualiza cliente        |
| `DELETE` | `/clientes/{id}` | Remove cliente          |

### GET — Listar clientes

```http
GET http://localhost:8080/clientes
```

Exemplo de resposta:

```json
[
  {
    "nome": "Maria Souza",
    "telefone": "21988887777",
    "idade": 32,
    "instagram": "@mariasouza",
    "id": 2
  }
]
```

### GET — Buscar por ID

```http
GET http://localhost:8080/clientes/2
```

### POST — Cadastrar cliente

```http
POST http://localhost:8080/clientes
Content-Type: application/json
```

```json
{
  "nome": "João Silva",
  "telefone": "21999999999",
  "idade": 25,
  "instagram": "@joaosilva"
}
```

### PUT — Atualizar cliente

```http
PUT http://localhost:8080/clientes/3
Content-Type: application/json
```

```json
{
  "nome": "João Silva",
  "telefone": "21911112222",
  "idade": 25,
  "instagram": "@joaosilva"
}
```

### DELETE — Excluir cliente

```http
DELETE http://localhost:8080/clientes/3
```

---

## 🗄️ Banco de dados

O projeto utiliza **PostgreSQL** para persistência dos dados.

Configuração utilizada no ambiente local:

```text
Banco: ink_manager
Host: localhost
Porta: 5432
Schema: public
```

A API utiliza **Spring Data JPA + Hibernate** para realizar a comunicação com o banco.

---

## ▶️ Como executar a API

Entre na pasta da API:

```cmd
cd ink-manager-api
```

Execute utilizando o Maven Wrapper:

```cmd
mvnw.cmd spring-boot:run
```

A aplicação será iniciada em:

```text
http://localhost:8080
```

### Testando pelo terminal

Listar clientes:

```cmd
curl http://localhost:8080/clientes
```

Buscar cliente:

```cmd
curl http://localhost:8080/clientes/1
```

---

## 🧪 Testes realizados

O CRUD de clientes foi validado através de requisições HTTP diretamente na API.

* ✅ `GET /clientes`
* ✅ `GET /clientes/{id}`
* ✅ `POST /clientes`
* ✅ `PUT /clientes/{id}`
* ✅ `DELETE /clientes/{id}`
* ✅ Persistência no PostgreSQL
* ✅ Consulta de dados persistidos
* ✅ Retorno `404 Not Found` para cliente inexistente
* ✅ Aplicação executando na porta `8080`

---

## 📈 Evolução do projeto

### v0.1 — Java

* Estrutura inicial do sistema
* Cadastro de clientes
* Listagem de clientes
* Busca de cliente por ID

### v0.2 — Persistência em arquivos

* Persistência utilizando arquivos `.txt`
* Implementação de marcações
* Gerenciamento de status das marcações

### v0.3 — PostgreSQL

* Integração com PostgreSQL
* JDBC
* `ConnectionFactory`
* Repositories
* Persistência de clientes
* Persistência de marcações

### v0.4 — API REST

* Spring Boot
* Spring Data JPA
* Hibernate
* Arquitetura Controller / Service / Repository
* CRUD completo de clientes
* Integração com PostgreSQL
* Tratamento global de exceções

---

## 🔮 Próximos passos

* [ ] Criar CRUD de marcações na API
* [ ] Relacionar clientes e marcações
* [ ] Melhorar validações
* [ ] Aprimorar tratamento de exceções
* [ ] Documentar endpoints com Swagger/OpenAPI
* [ ] Implementar autenticação e autorização
* [ ] Criar frontend para consumo da API
* [ ] Dockerizar a aplicação
* [ ] Realizar deploy

---

## 🎯 Objetivo

O **Ink.Manager** é um projeto de estudo e portfólio focado no desenvolvimento **Backend Java**.

O projeto está sendo desenvolvido de forma incremental, acompanhando a evolução das tecnologias utilizadas:

```text
Java
  ↓
JDBC
  ↓
PostgreSQL
  ↓
Spring Boot
  ↓
Spring Data JPA
  ↓
Hibernate
  ↓
API REST
```

A proposta é continuar evoluindo o sistema até transformá-lo em uma aplicação backend completa para gerenciamento de tatuadores, clientes e marcações.
