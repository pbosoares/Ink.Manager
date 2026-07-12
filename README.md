🎨 Ink.Manager

Sistema de gerenciamento desenvolvido em Java com o objetivo de auxiliar tatuadores no controle de clientes e organização dos seus atendimentos.

O projeto faz parte da minha evolução em Engenharia de Software, aplicando conceitos de Programação Orientada a Objetos, organização de código e boas práticas de desenvolvimento.

🚀 Versão atual

v0.2

📌 Funcionalidades

✅ Cadastro de clientes
✅ Geração automática de ID para cada cliente
✅ Listagem de clientes cadastrados
✅ Busca de clientes por ID
✅ Validação de cliente existente através do retorno do Service

🛠️ Tecnologias utilizadas:

Java
Programação Orientada a Objetos (POO)
ArrayList
Encapsulamento
Classes e Objetos
Git e GitHub

🏗️ Estrutura do projeto
Ink.Manager
│
├── Cliente.java
│
├── ClienteService.java
│
└── MainMaster.java
💡 Como funciona

O sistema possui uma separação simples de responsabilidades:

Cliente.java

Responsável pelo modelo do cliente, contendo seus atributos e métodos de acesso.

ClienteService.java

Responsável pelas regras de negócio:

Cadastro de clientes
Geração automática de IDs
Listagem dos clientes
Busca de clientes pelo ID
MainMaster.java

Responsável pela interação com o usuário através do menu do sistema.

📋 Exemplo de uso

Menu principal:

===== INK.MANAGER =====

1 - Cadastrar cliente
2 - Listar clientes
3 - Buscar cliente
0 - Sair

Exemplo de busca:

Buscar cliente
Id: 1

ID: 1
Nome: Pablo Soares
Telefone: 21 9999-9999

🔄 Próximas melhorias
Editar informações de clientes
Remover clientes pelo ID
Persistência de dados em banco de dados
Implementação de API REST com Spring Boot
Interface gráfica ou aplicação web

👨‍💻 Desenvolvedor

Projeto desenvolvido por Pablo Soares como parte da jornada de aprendizado em Engenharia de Software e desenvolvimento Backend Java.